package sollecitom.lattice.core

interface Position : Comparable<Position> {

    val partition: Int

    fun encode(): String
}

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
