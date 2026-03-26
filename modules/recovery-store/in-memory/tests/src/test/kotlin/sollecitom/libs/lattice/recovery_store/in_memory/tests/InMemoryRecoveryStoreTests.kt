package sollecitom.libs.lattice.recovery_store.in_memory.tests

import sollecitom.libs.lattice.recovery_store.domain.RecoveryStore
import sollecitom.libs.lattice.recovery_store.in_memory.implementation.InMemoryRecoveryStore
import sollecitom.libs.lattice.recovery_store.test.specification.RecoveryStoreTestSpecification

class InMemoryRecoveryStoreTests : RecoveryStoreTestSpecification {

    override val recoveryStore: RecoveryStore<String> = InMemoryRecoveryStore()
}
