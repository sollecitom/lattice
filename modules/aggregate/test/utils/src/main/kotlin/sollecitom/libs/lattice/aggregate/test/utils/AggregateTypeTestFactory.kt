package sollecitom.libs.lattice.aggregate.test.utils

import sollecitom.libs.lattice.aggregate.domain.AggregateType

fun AggregateType.Companion.testRandom(): AggregateType = AggregateType("test-aggregate-${java.util.UUID.randomUUID().toString().take(8)}")
