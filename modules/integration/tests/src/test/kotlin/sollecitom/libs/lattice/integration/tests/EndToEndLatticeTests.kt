package sollecitom.libs.lattice.integration.tests

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.*
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.EventSourcedAggregateRepository
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.PublishingAggregateRepository
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.SequentialAggregateRepository
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.SnapshotPolicy
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome
import sollecitom.libs.lattice.command_gateway.local.implementation.LocalCommandGateway
import sollecitom.libs.lattice.event_store.in_memory.implementation.InMemoryEventBus
import sollecitom.libs.lattice.event_store.in_memory.implementation.InMemoryEventStore
import sollecitom.libs.lattice.projection.in_memory.implementation.InMemoryKeyValueProjection
import sollecitom.libs.lattice.recovery_store.in_memory.implementation.InMemoryRecoveryStore

@Suppress("FunctionName")
class EndToEndLatticeTests {

    @Test
    fun `full banking workflow with deposit, withdrawal, and balance query`() = runTest {

        val eventStore = InMemoryEventStore<BankAccountEvent>()
        val recoveryStore = InMemoryRecoveryStore<BankAccountState>()
        val repository = EventSourcedAggregateRepository(
            aggregate = BankAccountAggregate,
            eventStore = eventStore,
            recoveryStore = recoveryStore,
            snapshotPolicy = SnapshotPolicy.everyNEvents(3),
        )
        val gateway = LocalCommandGateway(repository)
        val accountId = AggregateId.testRandom()

        val depositOutcome = gateway.send(accountId, BankAccountCommand.Deposit(1000))

        assertThat(depositOutcome).isInstanceOf<CommandOutcome.Accepted<BankAccountEvent>>()
        val accepted = depositOutcome as CommandOutcome.Accepted<BankAccountEvent>
        assertThat(accepted.events).hasSize(1)
        assertThat(accepted.events.first()).isInstanceOf<BankAccountEvent.Deposited>()

        val withdrawOutcome = gateway.send(accountId, BankAccountCommand.Withdraw(300))

        assertThat(withdrawOutcome).isInstanceOf<CommandOutcome.Accepted<BankAccountEvent>>()

        val state = repository.state(accountId)
        assertThat(state.balance).isEqualTo(700)
    }

    @Test
    fun `overdraft is rejected`() = runTest {

        val repository = EventSourcedAggregateRepository(
            aggregate = BankAccountAggregate,
            eventStore = InMemoryEventStore(),
        )
        val gateway = LocalCommandGateway(repository)
        val accountId = AggregateId.testRandom()

        gateway.send(accountId, BankAccountCommand.Deposit(500))
        val outcome = gateway.send(accountId, BankAccountCommand.Withdraw(1000))

        assertThat(outcome).isInstanceOf<CommandOutcome.Rejected>()
        val rejected = outcome as CommandOutcome.Rejected
        assertThat(rejected.reason.value).contains("Insufficient balance")
    }

    @Test
    fun `idempotent command processing prevents duplicates`() = runTest {

        val eventStore = InMemoryEventStore<BankAccountEvent>()
        val repository = EventSourcedAggregateRepository(
            aggregate = BankAccountAggregate,
            eventStore = eventStore,
        )
        val accountId = AggregateId.testRandom()
        val commandId = CommandId.testRandom()

        repository.handle(accountId, BankAccountCommand.Deposit(100, commandId))

        val decision = BankAccountAggregate.decideIdempotently(
            repository.state(accountId),
            BankAccountCommand.Deposit(100, commandId)
        )

        assertThat(decision).isInstanceOf<CommandDecision.Accepted<BankAccountEvent>>()
        val accepted = decision as CommandDecision.Accepted<BankAccountEvent>
        assertThat(accepted.events).isEmpty()
    }

    @Test
    fun `event store persists and replays events`() = runTest {

        val eventStore = InMemoryEventStore<BankAccountEvent>()
        val repository = EventSourcedAggregateRepository(
            aggregate = BankAccountAggregate,
            eventStore = eventStore,
        )
        val accountId = AggregateId.testRandom()

        repository.handle(accountId, BankAccountCommand.Deposit(500))
        repository.handle(accountId, BankAccountCommand.Withdraw(200))

        val events = eventStore.read(accountId).toList()
        assertThat(events).hasSize(2)
        assertThat(events[0].event).isInstanceOf<BankAccountEvent.Deposited>()
        assertThat(events[1].event).isInstanceOf<BankAccountEvent.Withdrawn>()
        assertThat(events[0].version).isEqualTo(AggregateVersion(1))
        assertThat(events[1].version).isEqualTo(AggregateVersion(2))
    }

    @Test
    fun `snapshot recovery with delta replay`() = runTest {

        val eventStore = InMemoryEventStore<BankAccountEvent>()
        val recoveryStore = InMemoryRecoveryStore<BankAccountState>()
        val repository = EventSourcedAggregateRepository(
            aggregate = BankAccountAggregate,
            eventStore = eventStore,
            recoveryStore = recoveryStore,
            snapshotPolicy = SnapshotPolicy.everyNEvents(3),
        )
        val accountId = AggregateId.testRandom()

        repository.handle(accountId, BankAccountCommand.Deposit(100))
        repository.handle(accountId, BankAccountCommand.Deposit(200))
        repository.handle(accountId, BankAccountCommand.Deposit(300))

        val snapshot = recoveryStore.get(accountId)
        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.state.balance).isEqualTo(600)
        assertThat(snapshot.version).isEqualTo(AggregateVersion(3))

        repository.handle(accountId, BankAccountCommand.Withdraw(150))

        val state = repository.state(accountId)
        assertThat(state.balance).isEqualTo(450)
    }

    @Test
    fun `projection builds a materialized view from events`() = runTest {

        val eventStore = InMemoryEventStore<BankAccountEvent>()
        val eventBus = InMemoryEventBus<BankAccountEvent> { BankAccountAggregate.type }
        val repository = PublishingAggregateRepository(
            delegate = EventSourcedAggregateRepository(
                aggregate = BankAccountAggregate,
                eventStore = eventStore,
            ),
            eventBus = eventBus,
        )

        val balanceProjection = InMemoryKeyValueProjection<BankAccountEvent, Long> { currentBalance, event ->
            val balance = currentBalance ?: 0L
            when (event.event) {
                is BankAccountEvent.Deposited -> balance + (event.event as BankAccountEvent.Deposited).amount
                is BankAccountEvent.Withdrawn -> balance - (event.event as BankAccountEvent.Withdrawn).amount
                is BankAccountEvent.OutboundTransferInitiated -> balance - (event.event as BankAccountEvent.OutboundTransferInitiated).amount
                is BankAccountEvent.InboundTransferReceived -> balance + (event.event as BankAccountEvent.InboundTransferReceived).amount
            }
        }

        val accountId = AggregateId.testRandom()

        repository.handle(accountId, BankAccountCommand.Deposit(1000))
        repository.handle(accountId, BankAccountCommand.Withdraw(250))

        val events = eventStore.read(accountId).toList()
        events.forEach { balanceProjection.process(it) }

        val projectedBalance = balanceProjection.get(accountId)
        assertThat(projectedBalance).isNotNull().isEqualTo(750)
    }

    @Test
    fun `aggregate test harness provides given-when-then style testing`() {

        val harness = BankAccountAggregate.testHarness()

        harness.given(
            BankAccountEvent.Deposited(amount = 500),
            BankAccountEvent.Deposited(amount = 300)
        )
        assertThat(harness.currentState.balance).isEqualTo(800)

        val decision = harness.`when`(BankAccountCommand.Withdraw(200))

        assertThat(decision).isInstanceOf<CommandDecision.Accepted<BankAccountEvent>>()
        assertThat(harness.currentState.balance).isEqualTo(600)
    }

    @Test
    fun `sequential repository ensures serialized access per aggregate`() = runTest {

        val repository = SequentialAggregateRepository(
            delegate = EventSourcedAggregateRepository(
                aggregate = BankAccountAggregate,
                eventStore = InMemoryEventStore(),
            )
        )
        val accountId = AggregateId.testRandom()

        repository.handle(accountId, BankAccountCommand.Deposit(1000))
        repository.handle(accountId, BankAccountCommand.Withdraw(100))
        repository.handle(accountId, BankAccountCommand.Withdraw(200))

        val state = repository.state(accountId)
        assertThat(state.balance).isEqualTo(700)
    }

    private fun CommandId.Companion.testRandom(): CommandId =
        CommandId(sollecitom.libs.swissknife.core.domain.identity.StringId(java.util.UUID.randomUUID().toString()))
}
