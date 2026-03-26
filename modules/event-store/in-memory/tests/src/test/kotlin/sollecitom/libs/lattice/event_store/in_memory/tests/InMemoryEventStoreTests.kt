package sollecitom.libs.lattice.event_store.in_memory.tests

import sollecitom.libs.lattice.event_store.domain.EventStore
import sollecitom.libs.lattice.event_store.in_memory.implementation.InMemoryEventStore
import sollecitom.libs.lattice.event_store.test.specification.EventStoreTestSpecification

class InMemoryEventStoreTests : EventStoreTestSpecification {

    override val eventStore: EventStore<String> = InMemoryEventStore()
}
