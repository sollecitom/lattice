package sollecitom.libs.lattice.aggregate.test.utils

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.swissknife.core.domain.identity.Id
import sollecitom.libs.swissknife.core.domain.identity.StringId

fun AggregateId.Companion.testRandom(): AggregateId = AggregateId(Id.testRandom())

private fun Id.Companion.testRandom(): Id = StringId(java.util.UUID.randomUUID().toString())
