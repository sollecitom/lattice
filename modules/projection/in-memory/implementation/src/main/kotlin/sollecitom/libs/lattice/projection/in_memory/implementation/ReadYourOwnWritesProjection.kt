package sollecitom.libs.lattice.projection.in_memory.implementation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.event_store.domain.VersionedEvent
import sollecitom.libs.lattice.projection.domain.EventProjection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ReadYourOwnWritesProjection<EVENT, VIEW>(
    private val project: (VIEW?, VersionedEvent<EVENT>) -> VIEW,
    private val defaultTimeout: Duration = 5.seconds,
) : EventProjection<EVENT> {

    private val views = mutableMapOf<AggregateId, VIEW>()
    private val offsets = mutableMapOf<AggregateId, AggregateVersion>()
    private val mutex = Mutex()

    override suspend fun process(event: VersionedEvent<EVENT>): Unit = mutex.withLock {

        val currentView = views[event.aggregateId]
        views[event.aggregateId] = project(currentView, event)
        offsets[event.aggregateId] = event.version
    }

    suspend fun get(aggregateId: AggregateId): VIEW? = mutex.withLock {
        views[aggregateId]
    }

    suspend fun getAfter(aggregateId: AggregateId, minimumVersion: AggregateVersion, timeout: Duration = defaultTimeout): VIEW? {

        return withTimeoutOrNull(timeout) {
            while (true) {
                val result = mutex.withLock {
                    val currentVersion = offsets[aggregateId] ?: AggregateVersion.initial
                    if (currentVersion >= minimumVersion) views[aggregateId] else null
                }
                if (result != null || mutex.withLock { (offsets[aggregateId] ?: AggregateVersion.initial) >= minimumVersion }) {
                    return@withTimeoutOrNull mutex.withLock { views[aggregateId] }
                }
                kotlinx.coroutines.delay(10)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    companion object
}
