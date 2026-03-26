package sollecitom.libs.lattice.aggregate_repository.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId

interface AggregateRepository<COMMAND, EVENT, STATE> {

    suspend fun handle(aggregateId: AggregateId, command: COMMAND): CommandOutcome<EVENT>

    suspend fun state(aggregateId: AggregateId): STATE

    companion object
}
