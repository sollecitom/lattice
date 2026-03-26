package sollecitom.libs.lattice.aggregate_repository.test.specification

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.aggregate_repository.domain.AggregateRepository
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome

@Suppress("FunctionName")
interface AggregateRepositoryTestSpecification<COMMAND, EVENT, STATE> {

    val repository: AggregateRepository<COMMAND, EVENT, STATE>
    val expectedInitialState: STATE

    fun acceptedCommand(): COMMAND
    fun rejectedCommand(): COMMAND

    @Test
    fun `handling an accepted command returns accepted outcome with events`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val command = acceptedCommand()

        val outcome = repository.handle(aggregateId, command)

        assertThat(outcome).isInstanceOf<CommandOutcome.Accepted<EVENT>>()
        val accepted = outcome as CommandOutcome.Accepted<EVENT>
        assertThat(accepted.events).isNotEmpty()
    }

    @Test
    fun `handling a rejected command returns rejected outcome`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val command = rejectedCommand()

        val outcome = repository.handle(aggregateId, command)

        assertThat(outcome).isInstanceOf<CommandOutcome.Rejected>()
    }

    @Test
    fun `state reflects handled commands`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val command = acceptedCommand()
        repository.handle(aggregateId, command)

        val state = repository.state(aggregateId)

        assertThat(state).isNotEqualTo(expectedInitialState)
    }

    @Test
    fun `state for unknown aggregate is the initial state`() = runTest {

        val aggregateId = AggregateId.testRandom()

        val state = repository.state(aggregateId)

        assertThat(state).isEqualTo(expectedInitialState)
    }

    @Test
    fun `multiple commands can be handled sequentially`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val command1 = acceptedCommand()
        val command2 = acceptedCommand()

        val outcome1 = repository.handle(aggregateId, command1)
        val outcome2 = repository.handle(aggregateId, command2)

        assertThat(outcome1).isInstanceOf<CommandOutcome.Accepted<EVENT>>()
        assertThat(outcome2).isInstanceOf<CommandOutcome.Accepted<EVENT>>()
    }
}
