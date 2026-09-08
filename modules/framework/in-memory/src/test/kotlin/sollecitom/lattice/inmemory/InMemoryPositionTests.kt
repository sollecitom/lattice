package sollecitom.lattice.inmemory

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import sollecitom.libs.swissknife.test.utils.assertions.failedThrowing

@TestInstance(PER_CLASS)
class InMemoryPositionTests {

    @Test
    fun `encodes and decodes back to itself`() {

        val position = InMemoryPosition(partition = 7, offset = 42)

        assertThat(InMemoryPositions.decode(position.encode())).isEqualTo(position)
    }

    @Test
    fun `refuses to decode another scheme`() {

        assertThat(runCatching { InMemoryPositions.decode("pulsar1:7:AAAA") })
            .failedThrowing<IllegalArgumentException>()
    }

    @Test
    fun `orders within a partition`() {

        assertThat(InMemoryPosition(1, 5)).isGreaterThan(InMemoryPosition(1, 4))
    }

    @Test
    fun `refuses to compare across partitions`() {

        assertThat(runCatching { InMemoryPosition(1, 5) > InMemoryPosition(2, 4) })
            .failedThrowing<IllegalArgumentException>()
    }
}
