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

    fun history(key: Id): Flow<Recorded<Event>>

    suspend fun stop()
}

interface LatticeEnvironment {

    fun <COMMAND : Command, EVENT : DomainEvent, STATE> registerAggregate(
        type: String,
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        commandType: KClass<COMMAND>,
        commandKey: (COMMAND) -> Id,
        eventKey: (EVENT) -> Id,
    )

    fun <EVENT : DomainEvent, STATE, QUERY : Query<*>> registerReadModel(
        name: String,
        readModel: ReadModel<EVENT, STATE, QUERY>,
        eventType: KClass<EVENT>,
        queryType: KClass<QUERY>,
        eventKey: (EVENT) -> Id,
        queryKey: (QUERY) -> Id,
    )

    suspend fun start(): Lattice
}

inline fun <reified COMMAND : Command, EVENT : DomainEvent, STATE> LatticeEnvironment.registerAggregate(
    type: String,
    aggregate: Aggregate<COMMAND, EVENT, STATE>,
    noinline commandKey: (COMMAND) -> Id,
    noinline eventKey: (EVENT) -> Id,
) = registerAggregate(type, aggregate, COMMAND::class, commandKey, eventKey)

inline fun <reified EVENT : DomainEvent, STATE, reified QUERY : Query<*>> LatticeEnvironment.registerReadModel(
    name: String,
    readModel: ReadModel<EVENT, STATE, QUERY>,
    noinline eventKey: (EVENT) -> Id,
    noinline queryKey: (QUERY) -> Id,
) = registerReadModel(name, readModel, EVENT::class, QUERY::class, eventKey, queryKey)
