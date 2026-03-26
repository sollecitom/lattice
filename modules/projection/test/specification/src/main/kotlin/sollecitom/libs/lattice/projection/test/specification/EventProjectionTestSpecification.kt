package sollecitom.libs.lattice.projection.test.specification

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.event_store.domain.VersionedEvent
import sollecitom.libs.lattice.projection.domain.EventProjection

@Suppress("FunctionName")
interface EventProjectionTestSpecification<EVENT> {

    val projection: EventProjection<EVENT>

    fun sampleEvent(): EVENT
    fun verifyProjected(event: EVENT)

    @Test
    fun `processing a versioned event updates the projection`() = runTest {

        val event = sampleEvent()
        val aggregateId = AggregateId.testRandom()
        val versionedEvent = VersionedEvent(event = event, version = AggregateVersion(1), aggregateId = aggregateId)

        projection.process(versionedEvent)

        verifyProjected(event)
    }
}
