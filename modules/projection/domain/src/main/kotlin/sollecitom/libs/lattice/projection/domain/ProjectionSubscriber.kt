package sollecitom.libs.lattice.projection.domain

import sollecitom.libs.swissknife.core.domain.lifecycle.Startable
import sollecitom.libs.swissknife.core.domain.lifecycle.Stoppable

interface ProjectionSubscriber<EVENT> : Startable, Stoppable {

    val projection: EventProjection<EVENT>

    companion object
}
