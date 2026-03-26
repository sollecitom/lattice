package sollecitom.libs.lattice.aggregate.test.utils

import sollecitom.libs.lattice.aggregate.domain.CommandId
import sollecitom.libs.swissknife.core.domain.identity.Id
import sollecitom.libs.swissknife.core.domain.identity.StringId

fun CommandId.Companion.testRandom(): CommandId = CommandId(StringId(java.util.UUID.randomUUID().toString()))
