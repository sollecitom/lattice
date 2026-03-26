package sollecitom.libs.lattice.aggregate.domain

@JvmInline
value class RejectionReason(val value: String) {

    init {
        require(value.isNotBlank()) { "Rejection reason must not be blank" }
    }

    override fun toString() = value

    companion object
}
