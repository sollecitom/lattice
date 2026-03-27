package sollecitom.libs.lattice.sdk.kotlin.api

import kotlinx.coroutines.flow.Flow
import sollecitom.libs.swissknife.core.domain.identity.Id
import sollecitom.libs.swissknife.core.domain.lifecycle.Stoppable

interface Lattice : Stoppable {

    suspend fun accept(command: Command): CommandOutcome

    suspend fun <RESPONSE : Any> acceptAndReceive(command: CommandWithResponse<RESPONSE>): CommandResponse<RESPONSE>

    suspend fun publish(event: Event)

    suspend fun <ANSWER> query(query: Query<ANSWER>): QueryOutcome<ANSWER>

    fun eventHistory(targetId: Id): Flow<Event>

    companion object
}
