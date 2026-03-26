package sollecitom.libs.lattice.aggregate.domain

@JvmInline
value class AggregateVersion(val value: Long) : Comparable<AggregateVersion> {

    init {
        require(value >= 0) { "Aggregate version must be non-negative, but was $value" }
    }

    operator fun plus(count: Int) = AggregateVersion(value + count)

    override fun compareTo(other: AggregateVersion) = value.compareTo(other.value)

    override fun toString() = "v$value"

    companion object {

        val initial = AggregateVersion(0)
    }
}
