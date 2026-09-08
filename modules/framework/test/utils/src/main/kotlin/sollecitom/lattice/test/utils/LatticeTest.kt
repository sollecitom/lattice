package sollecitom.lattice.test.utils

import kotlinx.coroutines.CoroutineScope
import sollecitom.lattice.core.Lattice
import sollecitom.lattice.core.LatticeEnvironment
import sollecitom.lattice.inmemory.InMemoryLatticeEnvironment
import sollecitom.lattice.inmemory.ProjectionScheduler
import sollecitom.libs.swissknife.test.utils.execution.utils.test

fun latticeTest(
    scheduler: ProjectionScheduler = ProjectionScheduler.Immediate,
    partitionCount: Int = 4,
    register: LatticeEnvironment.() -> Unit,
    body: suspend CoroutineScope.(Lattice) -> Unit,
) = test {

    val environment = InMemoryLatticeEnvironment(partitionCount, scheduler)
    environment.register()
    val lattice = environment.start()
    try {
        body(lattice)
    } finally {
        lattice.stop()
    }
}
