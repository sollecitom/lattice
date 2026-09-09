package sollecitom.lattice.inmemory

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import assertk.assertions.messageContains
import sollecitom.lattice.core.MAX_ROUTING_KEY_LENGTH
import sollecitom.lattice.core.distanceToOrNull
import sollecitom.lattice.core.validRoutingKey
import sollecitom.libs.swissknife.test.utils.assertions.failedThrowing

@TestInstance(PER_CLASS)
class RoutingKeyTests {

    @Test
    fun `refuses a blank key, which would collapse every instance onto one partition`() {

        assertThat(runCatching { validRoutingKey("  ", "commandKey of 'account'") })
            .failedThrowing<IllegalArgumentException>()
            .messageContains("account")
    }

    @Test
    fun `refuses an oversized key`() {

        assertThat(runCatching { validRoutingKey("x".repeat(MAX_ROUTING_KEY_LENGTH + 1), "eventKey") })
            .failedThrowing<IllegalArgumentException>()
    }

    @Test
    fun `passes an ordinary key through`() {

        assertThat(validRoutingKey("acc-1", "commandKey")).isEqualTo("acc-1")
    }
}

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
    fun `measures distance within a partition, signed`() {

        assertThat(InMemoryPosition(1, 4).distanceTo(InMemoryPosition(1, 9))).isEqualTo(5L)
        assertThat(InMemoryPosition(1, 9).distanceTo(InMemoryPosition(1, 4))).isEqualTo(-5L)
    }

    @Test
    fun `refuses to relate positions in different partitions`() {

        assertThat(runCatching { InMemoryPosition(1, 5) > InMemoryPosition(2, 4) })
            .failedThrowing<IllegalArgumentException>()

        assertThat(runCatching { InMemoryPosition(1, 5).distanceTo(InMemoryPosition(2, 4)) })
            .failedThrowing<IllegalArgumentException>()
    }

    @Test
    fun `distanceToOrNull yields null rather than throwing across partitions`() {

        assertThat(InMemoryPosition(1, 4).distanceToOrNull(InMemoryPosition(1, 9))).isEqualTo(5L)
        assertThat(InMemoryPosition(1, 4).distanceToOrNull(InMemoryPosition(2, 9))).isNull()
    }
}
