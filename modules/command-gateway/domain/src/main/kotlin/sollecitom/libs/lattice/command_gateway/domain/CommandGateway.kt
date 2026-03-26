package sollecitom.libs.lattice.command_gateway.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome

interface CommandGateway<COMMAND, EVENT> {

    suspend fun send(aggregateId: AggregateId, command: COMMAND): CommandOutcome<EVENT>

    companion object
}
