package sollecitom.lattice.inmemory

import sollecitom.lattice.core.Position
import sollecitom.lattice.core.Positions

data class InMemoryPosition(override val partition: Int, val offset: Long) : Position {

    override fun compareTo(other: Position): Int {
        require(other is InMemoryPosition) { "cannot compare a $SCHEME position to ${other::class.simpleName}" }
        require(partition == other.partition) { "positions in different partitions are not comparable: $this vs $other" }
        return offset.compareTo(other.offset)
    }

    override fun encode() = "$SCHEME:$partition:$offset"

    override fun toString() = "p$partition@$offset"

    companion object {

        const val SCHEME = "memory1"

        fun beginningOf(partition: Int) = InMemoryPosition(partition, -1)
    }
}

object InMemoryPositions : Positions {

    override fun decode(encoded: String): Position {
        val parts = encoded.split(':')
        require(parts.size == 3 && parts[0] == InMemoryPosition.SCHEME) { "not a ${InMemoryPosition.SCHEME} position: $encoded" }
        return InMemoryPosition(parts[1].toInt(), parts[2].toLong())
    }
}
