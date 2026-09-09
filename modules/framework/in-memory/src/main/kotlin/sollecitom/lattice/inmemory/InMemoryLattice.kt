package sollecitom.lattice.inmemory

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sollecitom.lattice.core.*
import kotlin.math.absoluteValue
import kotlin.reflect.KClass

class InMemoryLatticeEnvironment(
    private val partitionCount: Int = 8,
    private val scheduler: ProjectionScheduler = ProjectionScheduler.Immediate,
) : LatticeEnvironment {

    private val aggregates = mutableListOf<RegisteredAggregate>()
    private val readModels = mutableListOf<RegisteredReadModel>()
    private var started = false

    override fun <COMMAND : Command, EVENT : DomainEvent, STATE> registerAggregate(
        type: String,
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        commandType: KClass<COMMAND>,
        commandKey: (COMMAND) -> String,
        eventKey: (EVENT) -> String,
    ) {
        check(!started) { "cannot register '$type' after start()" }
        aggregates.firstOrNull { it.commandType == commandType }?.let {
            error("command ${commandType.simpleName} is already owned by '${it.type}'; a command has exactly one owner")
        }
        @Suppress("UNCHECKED_CAST")
        aggregates += RegisteredAggregate(
            type = type,
            commandType = commandType,
            commandKey = { validRoutingKey((commandKey as (Command) -> String)(it), "commandKey of '$type'") },
            eventKey = { validRoutingKey((eventKey as (DomainEvent) -> String)(it), "eventKey of '$type'") },
            initialState = aggregate.initialState,
            decide = { state, command -> aggregate.decide(state as STATE, command as COMMAND) },
            apply = { state, event -> aggregate.apply(state as STATE, event as EVENT) },
        )
    }

    override fun <EVENT : DomainEvent, STATE, QUERY : Query<*>> registerReadModel(
        name: String,
        readModel: ReadModel<EVENT, STATE, QUERY>,
        eventType: KClass<EVENT>,
        queryType: KClass<QUERY>,
        eventKey: (EVENT) -> String,
        queryKey: (QUERY) -> String,
    ) {
        check(!started) { "cannot register '$name' after start()" }
        @Suppress("UNCHECKED_CAST")
        readModels += RegisteredReadModel(
            name = name,
            eventType = eventType,
            queryType = queryType,
            queryKey = { validRoutingKey((queryKey as (Query<*>) -> String)(it), "queryKey of '$name'") },
            initialState = readModel.initialState as Any,
            apply = { state, event -> readModel.apply(state as STATE, event as EVENT) as Any },
            answer = { state, query -> readModel.answer(state as STATE, query) },
        )
    }

    override suspend fun start(): Lattice {
        started = true
        return RunningLattice(partitionCount, aggregates.toList(), readModels.toList(), scheduler)
    }
}

fun interface ProjectionScheduler {

    suspend fun beforeApply(position: Position)

    companion object {
        val Immediate = ProjectionScheduler { }
    }
}

class ManualProjectionScheduler : ProjectionScheduler {

    private val released = CompletableDeferred<Unit>()

    override suspend fun beforeApply(position: Position) = released.await()

    fun release() {
        released.complete(Unit)
    }
}

private class RunningLattice(
    partitionCount: Int,
    private val aggregates: List<RegisteredAggregate>,
    private val readModels: List<RegisteredReadModel>,
    scheduler: ProjectionScheduler,
) : Lattice {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = PartitionedLog(partitionCount)
    private val pending = mutableMapOf<Id, CompletableDeferred<Verdict>>()
    private val pendingMutex = Mutex()

    init {
        aggregates.forEach { registered ->
            repeat(partitionCount) { partition -> scope.launch { runAggregate(registered, partition) } }
        }
        readModels.forEach { registered ->
            repeat(partitionCount) { partition ->
                scope.launch { registered.consume(log.stream(partition), partition, scheduler) }
            }
        }
    }

    override suspend fun submit(command: Command): CommandReceipt {
        val owner = aggregates.firstOrNull { it.commandType.isInstance(command) }
            ?: return CommandReceipt.Rejected("no aggregate owns ${command::class.simpleName}")

        val verdict = CompletableDeferred<Verdict>()
        pendingMutex.withLock { pending[command.id] = verdict }

        val position = log.append(owner.commandKey(command), CommandReceived(command.id, command))
        return AcceptedReceipt(command.id, position, verdict)
    }

    override suspend fun <ANSWER> query(query: Query<ANSWER>, atLeast: Freshness): Answered<ANSWER> {
        val readModel = readModels.firstOrNull { it.answers(query) }
            ?: error("no read model answers ${query::class.simpleName}")

        if (atLeast is Freshness.AtLeast) readModel.awaitAppliedThrough(atLeast.position)

        val partition = log.partitionFor(readModel.queryKey(query))
        return Answered(readModel.answer(query), readModel.appliedThrough(partition))
    }

    override fun history(key: String, after: Position?, through: Position?): Flow<Recorded<Event>> = log.history(key, after, through)

    override suspend fun stop() = scope.cancel()

    private suspend fun runAggregate(registered: RegisteredAggregate, partition: Int) {
        val states = mutableMapOf<String, Any?>()
        log.stream(partition).collect { recorded ->
            val event = recorded.value
            when (event) {
                is CommandReceived -> {
                    if (!registered.commandType.isInstance(event.command)) return@collect
                    val key = registered.commandKey(event.command)
                    val state = states.getOrPut(key) { registered.initialState }
                    when (val decision = registered.decide(state, event.command)) {
                        is Decision.Accept -> {
                            val at = log.append(key, decision.event)
                            complete(event.commandId, Verdict.Applied(listOf(Recorded(decision.event, at))))
                        }
                        is Decision.Reject -> {
                            log.append(key, CommandRejected(event.commandId, decision.reason))
                            complete(event.commandId, Verdict.Refused(decision.reason))
                        }
                    }
                }
                is CommandRejected -> Unit
                is DomainEvent -> {
                    val key = registered.eventKey(event)
                    states[key] = registered.apply(states.getOrPut(key) { registered.initialState }, event)
                }
            }
        }
    }

    private suspend fun complete(commandId: Id, verdict: Verdict) {
        pendingMutex.withLock { pending.remove(commandId) }?.complete(verdict)
    }

    private class AcceptedReceipt(
        override val commandId: Id,
        override val position: Position,
        private val pending: CompletableDeferred<Verdict>,
    ) : CommandReceipt.Accepted {
        override suspend fun verdict(): Verdict = pending.await()
    }
}

private class PartitionedLog(private val partitionCount: Int) {

    private val partitions = List(partitionCount) { Partition(it) }

    fun partitionFor(key: String): Int = key.hashCode().absoluteValue % partitionCount

    suspend fun append(key: String, event: Event): Position = partitions[partitionFor(key)].append(event)

    fun stream(partition: Int): Flow<Recorded<Event>> = partitions[partition].stream()

    fun history(key: String, after: Position?, through: Position?): Flow<Recorded<Event>> = partitions[partitionFor(key)]
        .recorded()
        .filter { (after == null || it.position > after) && (through == null || it.position <= through) }
        .asFlow()

    private class Partition(private val index: Int) {

        private val mutex = Mutex()
        private val recorded = mutableListOf<Recorded<Event>>()
        private val emitted = MutableSharedFlow<Recorded<Event>>(replay = Int.MAX_VALUE)
        private var nextOffset = 0L

        suspend fun append(event: Event): Position = mutex.withLock {
            val entry = Recorded(event, InMemoryPosition(index, nextOffset++))
            recorded += entry
            emitted.emit(entry)
            entry.position
        }

        fun stream(): Flow<Recorded<Event>> = emitted.asSharedFlow()

        fun recorded(): List<Recorded<Event>> = synchronized(recorded) { recorded.toList() }
    }
}

private class RegisteredAggregate(
    val type: String,
    val commandType: KClass<out Command>,
    val commandKey: (Command) -> String,
    val eventKey: (DomainEvent) -> String,
    val initialState: Any?,
    val decide: (Any?, Command) -> Decision<DomainEvent>,
    val apply: (Any?, DomainEvent) -> Any?,
)

private class RegisteredReadModel(
    val name: String,
    private val eventType: KClass<out DomainEvent>,
    private val queryType: KClass<out Query<*>>,
    val queryKey: (Query<*>) -> String,
    initialState: Any,
    private val apply: (Any, DomainEvent) -> Any,
    private val answer: (Any, Query<*>) -> Any?,
) {

    private val stateMutex = Mutex()
    private var state: Any = initialState
    private val progress = java.util.concurrent.ConcurrentHashMap<Int, MutableStateFlow<Position>>()

    fun answers(query: Query<*>): Boolean = queryType.isInstance(query)

    @Suppress("UNCHECKED_CAST")
    fun <ANSWER> answer(query: Query<ANSWER>): ANSWER = answer(state, query) as ANSWER

    fun appliedThrough(partition: Int): Position = progressOf(partition).value

    suspend fun awaitAppliedThrough(position: Position) {
        progressOf(position.partition).first { it >= position }
    }

    suspend fun consume(stream: Flow<Recorded<Event>>, partition: Int, scheduler: ProjectionScheduler) {
        stream.collect { (event, position) ->
            scheduler.beforeApply(position)
            if (eventType.isInstance(event)) {
                stateMutex.withLock { state = apply(state, event as DomainEvent) }
            }
            progressOf(partition).value = position
        }
    }

    private fun progressOf(partition: Int): MutableStateFlow<Position> =
        progress.computeIfAbsent(partition) { MutableStateFlow(InMemoryPosition.beginningOf(partition)) }
}
