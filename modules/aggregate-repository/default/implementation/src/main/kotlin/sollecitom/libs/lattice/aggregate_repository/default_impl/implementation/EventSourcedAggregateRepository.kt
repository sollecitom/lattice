package sollecitom.libs.lattice.aggregate_repository.default_impl.implementation

import kotlinx.coroutines.flow.toList
import sollecitom.libs.lattice.aggregate.domain.*
import sollecitom.libs.lattice.aggregate_repository.domain.AggregateRepository
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome
import sollecitom.libs.lattice.event_store.domain.EventStore
import sollecitom.libs.lattice.recovery_store.domain.RecoveryStore
import sollecitom.libs.lattice.recovery_store.domain.Snapshot

class EventSourcedAggregateRepository<COMMAND, EVENT, STATE>(
    private val aggregate: Aggregate<COMMAND, EVENT, STATE>,
    private val eventStore: EventStore<EVENT>,
    private val recoveryStore: RecoveryStore<STATE>? = null,
    private val snapshotPolicy: SnapshotPolicy = SnapshotPolicy.never,
) : AggregateRepository<COMMAND, EVENT, STATE> {

    override suspend fun handle(aggregateId: AggregateId, command: COMMAND): CommandOutcome<EVENT> {

        val (currentState, currentVersion) = loadState(aggregateId)

        return when (val decision = aggregate.decide(currentState, command)) {
            is CommandDecision.Accepted -> {
                val newVersion = eventStore.append(aggregateId, decision.events, currentVersion)
                val newState = decision.events.fold(currentState) { state, event -> aggregate.evolve(state, event) }
                maybeSnapshot(aggregateId, newState, newVersion)
                CommandOutcome.Accepted(events = decision.events, newVersion = newVersion)
            }

            is CommandDecision.Rejected -> CommandOutcome.Rejected(decision.reason)
        }
    }

    override suspend fun state(aggregateId: AggregateId): STATE = loadState(aggregateId).first

    private suspend fun loadState(aggregateId: AggregateId): Pair<STATE, AggregateVersion> {

        val snapshot = recoveryStore?.get(aggregateId)
        val baseState = snapshot?.state ?: aggregate.initialState
        val baseVersion = snapshot?.version ?: AggregateVersion.initial
        val deltaEvents = eventStore.read(aggregateId, fromVersion = baseVersion).toList()
        val currentState = deltaEvents.fold(baseState) { state, versioned -> aggregate.evolve(state, versioned.event) }
        val currentVersion = if (deltaEvents.isEmpty()) baseVersion else deltaEvents.last().version
        return currentState to currentVersion
    }

    private suspend fun maybeSnapshot(aggregateId: AggregateId, state: STATE, version: AggregateVersion) {

        if (recoveryStore == null) return
        if (!snapshotPolicy.shouldSnapshot(version)) return
        recoveryStore.save(Snapshot(state = state, version = version, aggregateId = aggregateId))
    }

    companion object
}
