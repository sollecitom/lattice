package sollecitom.libs.lattice.command_gateway.local.implementation

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate_repository.domain.AggregateRepository
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome
import sollecitom.libs.lattice.command_gateway.domain.CommandGateway

class LocalCommandGateway<COMMAND, EVENT, STATE>(
    private val repository: AggregateRepository<COMMAND, EVENT, STATE>,
) : CommandGateway<COMMAND, EVENT> {

    override suspend fun send(aggregateId: AggregateId, command: COMMAND): CommandOutcome<EVENT> =
        repository.handle(aggregateId, command)

    companion object
}
