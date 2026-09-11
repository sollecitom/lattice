package sollecitom.lattice.core

interface Positioned {

    val position: Position
}

interface Position : Comparable<Position>, Positioned {

    override val position: Position get() = this

    val partition: Int

    val offset: Long

    fun encode(): String

    override fun compareTo(other: Position): Int {
        requireRelatable(other)
        return offset.compareTo(other.offset)
    }

    fun distanceTo(other: Position): Long {
        requireRelatable(other)
        return other.offset - offset
    }

    private fun requireRelatable(other: Position) {
        require(this::class == other::class) { "cannot relate a ${this::class.simpleName} to a ${other::class.simpleName}" }
        require(partition == other.partition) { "positions in different partitions are unrelated: $this vs $other" }
    }
}

fun Position.distanceToOrNull(other: Position): Long? = runCatching { distanceTo(other) }.getOrNull()

interface Positions {

    fun decode(encoded: String): Position
}

sealed interface Freshness {

    data object Unconstrained : Freshness

    class AtLeast(of: Set<Positioned>) : Freshness {

        val positions: Set<Position> = of.map(Positioned::position).groupBy(Position::partition).values.map { it.max() }.toSet()

        init {
            require(positions.isNotEmpty()) { "a freshness constraint needs at least one position; use Unconstrained instead" }
        }

        override fun equals(other: Any?) = other is AtLeast && positions == other.positions

        override fun hashCode() = positions.hashCode()

        override fun toString() = "AtLeast($positions)"

        companion object {

            operator fun invoke(first: Positioned, vararg rest: Positioned) = AtLeast(setOf(first, *rest))
        }
    }
}

data class Recorded<out T>(val value: T, override val position: Position) : Positioned

data class Answered<out ANSWER>(val answer: ANSWER, val appliedThrough: Position)
