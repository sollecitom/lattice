package sollecitom.libs.lattice.framework.api

import kotlinx.coroutines.flow.Flow
import sollecitom.libs.lattice.sdk.kotlin.api.*
import sollecitom.libs.swissknife.core.domain.identity.Id
import sollecitom.libs.swissknife.core.domain.lifecycle.Stoppable

interface FrameworkEngine : Stoppable {

    fun <COMMAND : Command, EVENT, STATE> registerAggregate(
        type: String,
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        bindings: Bindings,
    )

    suspend fun processCommand(command: Command): Decision<*>

    suspend fun processEvent(event: Event)

    fun eventHistory(targetId: Id): Flow<Event>

    fun canHandleCommand(command: Command): Boolean

    companion object
}
