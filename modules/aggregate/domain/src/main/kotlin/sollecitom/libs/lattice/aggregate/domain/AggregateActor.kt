package sollecitom.libs.lattice.aggregate.domain

import sollecitom.libs.swissknife.core.domain.lifecycle.Startable
import sollecitom.libs.swissknife.core.domain.lifecycle.Stoppable

interface AggregateActor<COMMAND, EVENT, STATE> : Startable, Stoppable {

    val aggregateId: AggregateId
    val aggregateType: AggregateType

    suspend fun handle(command: COMMAND): CommandDecision<EVENT>

    suspend fun currentState(): STATE

    companion object
}
