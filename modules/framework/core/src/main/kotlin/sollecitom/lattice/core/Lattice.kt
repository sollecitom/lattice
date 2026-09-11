package sollecitom.lattice.core

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

sealed interface CommandReceipt {

    interface Accepted : CommandReceipt, Positioned {

        val commandId: Id

        override val position: Position

        suspend fun verdict(): Verdict
    }

    data class Rejected(val reason: String) : CommandReceipt
}

class CommandRejectedException(val reason: String) : IllegalStateException("Command rejected: $reason")

fun CommandReceipt.acceptedOrThrow(): CommandReceipt.Accepted = when (this) {
    is CommandReceipt.Accepted -> this
    is CommandReceipt.Rejected -> throw CommandRejectedException(reason)
}

sealed interface Verdict {

    data class Applied(val events: List<Recorded<DomainEvent>>) : Verdict

    data class Refused(val reason: String) : Verdict
}

class NoSuchReactionException(message: String) : IllegalStateException(message)

@Suppress("UNCHECKED_CAST")
suspend inline fun <reified T : DomainEvent> CommandReceipt.Accepted.awaitReaction(): Recorded<T> =
    when (val verdict = verdict()) {
        is Verdict.Applied -> verdict.events.firstOrNull { it.value is T } as Recorded<T>?
            ?: throw NoSuchReactionException("command $commandId produced ${verdict.events.map { it.value::class.simpleName }}; no ${T::class.simpleName}")
        is Verdict.Refused -> throw CommandRejectedException(verdict.reason)
    }

interface Lattice {

    suspend fun submit(command: Command): CommandReceipt

    suspend fun <ANSWER> query(query: Query<ANSWER>, atLeast: Freshness): Answered<ANSWER>

    fun history(key: String, after: Position? = null, through: Position? = null): Flow<Recorded<Event>>

    suspend fun stop()
}

interface LatticeEnvironment {

    fun <COMMAND : Command, EVENT : DomainEvent, STATE> registerAggregate(
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        commandType: KClass<COMMAND>,
        commandKey: (COMMAND) -> String,
        eventKey: (EVENT) -> String,
    )

    fun <EVENT : DomainEvent, STATE, QUERY : Query<ANSWER>, ANSWER> registerReadModel(
        readModel: EventSourcedReadModel<EVENT, STATE, QUERY, ANSWER>,
        eventType: KClass<EVENT>,
        queryType: KClass<QUERY>,
        eventKey: (EVENT) -> String,
        queryKey: (QUERY) -> String,
    )

    fun <EVENT : DomainEvent, QUERY : Query<ANSWER>, ANSWER> registerReadModel(
        readModel: MaterialisingReadModel<EVENT, QUERY, ANSWER>,
        eventType: KClass<EVENT>,
        queryType: KClass<QUERY>,
        eventKey: (EVENT) -> String,
        queryKey: (QUERY) -> String,
    )

    suspend fun start(): Lattice
}

inline fun <reified EVENT : DomainEvent, reified QUERY : Query<ANSWER>, ANSWER> LatticeEnvironment.registerReadModel(
    readModel: MaterialisingReadModel<EVENT, QUERY, ANSWER>,
    noinline eventKey: (EVENT) -> String,
    noinline queryKey: (QUERY) -> String,
) = registerReadModel(readModel, EVENT::class, QUERY::class, eventKey, queryKey)

inline fun <reified COMMAND : Command, EVENT : DomainEvent, STATE> LatticeEnvironment.registerAggregate(
    aggregate: Aggregate<COMMAND, EVENT, STATE>,
    noinline commandKey: (COMMAND) -> String,
    noinline eventKey: (EVENT) -> String,
) = registerAggregate(aggregate, COMMAND::class, commandKey, eventKey)

inline fun <reified EVENT : DomainEvent, STATE, reified QUERY : Query<ANSWER>, ANSWER> LatticeEnvironment.registerReadModel(
    readModel: EventSourcedReadModel<EVENT, STATE, QUERY, ANSWER>,
    noinline eventKey: (EVENT) -> String,
    noinline queryKey: (QUERY) -> String,
) = registerReadModel(readModel, EVENT::class, QUERY::class, eventKey, queryKey)
