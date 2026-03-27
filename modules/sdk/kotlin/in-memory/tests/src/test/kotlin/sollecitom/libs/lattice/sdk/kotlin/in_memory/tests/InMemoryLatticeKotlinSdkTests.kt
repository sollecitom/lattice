package sollecitom.libs.lattice.sdk.kotlin.in_memory.tests

import sollecitom.libs.lattice.framework.connector.embedded.EmbeddedLatticeEnvironment
import sollecitom.libs.lattice.sdk.kotlin.api.LatticeEnvironment
import sollecitom.libs.lattice.sdk.kotlin.test.specification.LatticeTestSpecification

class InMemoryLatticeKotlinSdkTests : LatticeTestSpecification {

    override fun newLatticeEnvironment(): LatticeEnvironment = EmbeddedLatticeEnvironment()
}
