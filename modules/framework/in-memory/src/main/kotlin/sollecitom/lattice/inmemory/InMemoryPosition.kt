package sollecitom.lattice.inmemory

import sollecitom.lattice.core.Position
import sollecitom.lattice.core.Positions

data class InMemoryPosition(override val partition: Int, override val offset: Long) : Position {

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
