package sollecitom.libs.lattice.command_gateway.test.specification

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome
import sollecitom.libs.lattice.command_gateway.domain.CommandGateway

@Suppress("FunctionName")
interface CommandGatewayTestSpecification<COMMAND, EVENT> {

    val gateway: CommandGateway<COMMAND, EVENT>

    fun acceptedCommand(): COMMAND
    fun rejectedCommand(): COMMAND

    @Test
    fun `sending an accepted command returns accepted outcome`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val command = acceptedCommand()

        val outcome = gateway.send(aggregateId, command)

        assertThat(outcome).isInstanceOf<CommandOutcome.Accepted<EVENT>>()
    }

    @Test
    fun `sending a rejected command returns rejected outcome`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val command = rejectedCommand()

        val outcome = gateway.send(aggregateId, command)

        assertThat(outcome).isInstanceOf<CommandOutcome.Rejected>()
    }
}
