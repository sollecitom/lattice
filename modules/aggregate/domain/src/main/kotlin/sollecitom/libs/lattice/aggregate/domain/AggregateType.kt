package sollecitom.libs.lattice.aggregate.domain

@JvmInline
value class AggregateType(val value: String) {

    init {
        require(value.isNotBlank()) { "Aggregate type must not be blank" }
    }

    override fun toString() = value

    companion object
}
