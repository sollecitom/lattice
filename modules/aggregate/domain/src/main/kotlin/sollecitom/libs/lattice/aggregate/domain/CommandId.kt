package sollecitom.libs.lattice.aggregate.domain

import sollecitom.libs.swissknife.core.domain.identity.Id

@JvmInline
value class CommandId(val id: Id) {

    val stringValue: String get() = id.stringValue

    override fun toString() = stringValue

    companion object
}
