package sollecitom.lattice.test.utils

import kotlinx.coroutines.CoroutineScope
import sollecitom.lattice.core.Lattice
import sollecitom.lattice.core.LatticeEnvironment
import sollecitom.lattice.inmemory.InMemoryLatticeEnvironment
import sollecitom.lattice.inmemory.Projections
import sollecitom.libs.swissknife.test.utils.execution.utils.test

class LatticeUnderTest(lattice: Lattice, val projections: Projections) : Lattice by lattice

fun latticeTest(
    partitionCount: Int = 4,
    register: LatticeEnvironment.() -> Unit,
    body: suspend CoroutineScope.(LatticeUnderTest) -> Unit,
) = test {

    val environment = InMemoryLatticeEnvironment(partitionCount)
    environment.register()
    val lattice = LatticeUnderTest(environment.start(), environment.projectionsUnderTest)
    try {
        body(lattice)
    } finally {
        lattice.stop()
    }
}
