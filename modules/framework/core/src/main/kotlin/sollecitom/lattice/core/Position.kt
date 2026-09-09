package sollecitom.lattice.core

interface Position : Comparable<Position> {

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

    data class AtLeast(val position: Position) : Freshness
}

interface Positioned {

    val position: Position
}

data class Recorded<out T>(val value: T, override val position: Position) : Positioned

data class Answered<out ANSWER>(val answer: ANSWER, val appliedThrough: Position)
