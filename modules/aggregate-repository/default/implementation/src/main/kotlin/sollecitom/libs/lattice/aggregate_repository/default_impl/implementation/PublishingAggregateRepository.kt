package sollecitom.libs.lattice.aggregate_repository.default_impl.implementation

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate_repository.domain.AggregateRepository
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome
import sollecitom.libs.lattice.event_store.domain.EventBus
import sollecitom.libs.lattice.event_store.domain.VersionedEvent

class PublishingAggregateRepository<COMMAND, EVENT, STATE>(
    private val delegate: AggregateRepository<COMMAND, EVENT, STATE>,
    private val eventBus: EventBus<EVENT>,
) : AggregateRepository<COMMAND, EVENT, STATE> {

    override suspend fun handle(aggregateId: AggregateId, command: COMMAND): CommandOutcome<EVENT> {

        val outcome = delegate.handle(aggregateId, command)
        if (outcome is CommandOutcome.Accepted) {
            val baseVersion = AggregateVersion(outcome.newVersion.value - outcome.events.size)
            outcome.events.forEachIndexed { index, event ->
                val version = AggregateVersion(baseVersion.value + index + 1)
                eventBus.publish(VersionedEvent(event = event, version = version, aggregateId = aggregateId))
            }
        }
        return outcome
    }

    override suspend fun state(aggregateId: AggregateId): STATE = delegate.state(aggregateId)

    companion object
}
