package sollecitom.libs.lattice.framework.connector.embedded

import kotlinx.coroutines.flow.Flow
import sollecitom.libs.lattice.framework.api.FrameworkEngine
import sollecitom.libs.lattice.sdk.kotlin.api.*
import sollecitom.libs.swissknife.core.domain.identity.Id

internal class EmbeddedLattice(private val engine: FrameworkEngine) : Lattice {

    override suspend fun accept(command: Command): CommandOutcome {

        if (!engine.canHandleCommand(command)) return CommandOutcome.Rejected("No aggregate registered for command type ${command.javaClass.name}")

        return when (engine.processCommand(command)) {
            is Decision.Accept, is Decision.AcceptWithResponse<*, *> -> CommandOutcome.Processed
            is Decision.Reject -> CommandOutcome.Rejected((engine.processCommand(command) as? Decision.Reject)?.reason ?: "Unknown")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <RESPONSE : Any> acceptAndReceive(command: CommandWithResponse<RESPONSE>): CommandResponse<RESPONSE> {

        if (!engine.canHandleCommand(command)) return CommandResponse.Rejected("No aggregate registered for command type ${command.javaClass.name}")

        return when (val decision = engine.processCommand(command)) {
            is Decision.AcceptWithResponse<*, *> -> CommandResponse.Responded(decision.response as RESPONSE)
            is Decision.Accept -> error("Aggregate returned Accept without response for a CommandWithResponse. Use AcceptWithResponse in the aggregate's handle function.")
            is Decision.Reject -> CommandResponse.Rejected(decision.reason)
        }
    }

    override suspend fun publish(event: Event) = engine.processEvent(event)

    override suspend fun <ANSWER> query(query: Query<ANSWER>): QueryOutcome<ANSWER> {
        TODO("Queries not yet implemented")
    }

    override fun eventHistory(targetId: Id): Flow<Event> = engine.eventHistory(targetId)

    override suspend fun stop() = engine.stop()
}
