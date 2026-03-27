package sollecitom.libs.lattice.sdk.kotlin.test.specification

import assertk.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.sdk.kotlin.api.*
import sollecitom.libs.swissknife.core.domain.identity.Id
import sollecitom.libs.swissknife.core.domain.identity.StringId

// --- Test plan (ordered by priority) ---
// TODO test: command rejection — withdraw exceeding balance, verify rejection reason
// TODO test: CommandWithResponse — submit withdraw with typed response, verify WithdrawConfirmation at compile time
// TODO test: multiple commands to the same aggregate — deposit, withdraw, withdraw, verify cumulative state
// TODO test: publish external event, verify state — publish deposits, query balance via read model
// TODO test: query via read model — register balance read model, submit GetBalance, verify answer
// TODO test: consume historical events — read the full event history for an account
// TODO test: idempotent command handling — submit same command twice (same idempotency key), processed once
// TODO test: event-driven cascade (choreography) — SendPayment on A → PaymentSent → PaymentReceived on B
// TODO test: multiple aggregate types with routing — register bank-account + fraud-detection, verify routing
// TODO test: aggregate pipeline — A emits → B reacts → C reacts (chain of 3+)
// TODO test: cross-key read model — project all transactions across all accounts (e.g., daily total)
// TODO test: topology registration — register co-partitioned aggregates in a named topology
// TODO test: topology modification — change partition count, add/remove aggregates from a topology
// TODO test: just-in-time lookup — fetch exchange rate before processing international payment, verify cached
// TODO test: event sink — payment gateway sink consumes PaymentRequested, result event flows back
// TODO test: lookup failure policy — vendor API down, verify USE_STALE / REJECT behavior
// TODO test: lifecycle (start/stop) — start, work, stop, verify cleanup

@Suppress("FunctionName")
interface LatticeTestSpecification {

    fun newLatticeEnvironment(): LatticeEnvironment

    @Test
    fun `submit a command and receive the outcome`() = runTest {

        val environment = newLatticeEnvironment()
        environment.registerAggregate(
            type = "bank-account",
            aggregate = BankAccountAggregate,
            bindings = bindings {
                command<AccountCommand.Withdraw> { it.accountId }
                command<AccountCommand.SendPayment> { it.accountId }
                event<AccountEvent.Deposited> { it.accountId }
                event<AccountEvent.PaymentReceived> { it.recipientAccountId }
            },
        )
        val lattice = environment.start()

        val accountId = newId()
        lattice.publish(AccountEvent.Deposited(id = newId(), accountId = accountId, amount = 1000))

        val withdraw = AccountCommand.Withdraw(id = newId(), accountId = accountId, amount = 300)
        val outcome = lattice.accept(withdraw)

        assertThat(outcome).wasProcessed()

        lattice.stop()
    }

    companion object {

        // TODO use a ULID or equivalent from swissknife
        private fun newId(): Id = StringId(java.util.UUID.randomUUID().toString())
    }
}

// --- Banking domain: pure data, no framework concerns ---

sealed interface AccountEvent : Event {

    data class Deposited(override val id: Id, val accountId: Id, val amount: Long) : AccountEvent

    data class Withdrawn(override val id: Id, val amount: Long, val newBalance: Long) : AccountEvent

    data class PaymentSent(override val id: Id, val amount: Long, val toAccountId: Id, val newBalance: Long) : AccountEvent

    data class PaymentReceived(override val id: Id, val recipientAccountId: Id, val amount: Long, val fromAccountId: Id, val newBalance: Long) : AccountEvent
}

sealed interface AccountCommand : Command {

    data class Withdraw(override val id: Id, val accountId: Id, val amount: Long) : AccountCommand

    data class SendPayment(override val id: Id, val accountId: Id, val amount: Long, val toAccountId: Id) : AccountCommand
}

data class GetBalance(override val id: Id, val accountId: Id) : Query<Long>

data class AccountState(val balance: Long)

object BankAccountAggregate : Aggregate<AccountCommand, AccountEvent, AccountState> {

    override val initialState = AccountState(balance = 0)

    override fun handle(state: AccountState, command: AccountCommand): Decision<AccountEvent> = when (command) {
        is AccountCommand.Withdraw -> {
            if (state.balance >= command.amount) {
                val newBalance = state.balance - command.amount
                accept(AccountEvent.Withdrawn(id = command.id, amount = command.amount, newBalance = newBalance))
            } else {
                reject("Insufficient balance: ${state.balance} < ${command.amount}")
            }
        }

        is AccountCommand.SendPayment -> {
            if (state.balance >= command.amount) {
                val newBalance = state.balance - command.amount
                accept(AccountEvent.PaymentSent(id = command.id, amount = command.amount, toAccountId = command.toAccountId, newBalance = newBalance))
            } else {
                reject("Insufficient balance for payment: ${state.balance} < ${command.amount}")
            }
        }
    }

    override fun apply(state: AccountState, event: AccountEvent): AccountState = when (event) {
        is AccountEvent.Deposited -> state.copy(balance = state.balance + event.amount)
        is AccountEvent.Withdrawn -> state.copy(balance = state.balance - event.amount)
        is AccountEvent.PaymentSent -> state.copy(balance = state.balance - event.amount)
        is AccountEvent.PaymentReceived -> state.copy(balance = state.balance + event.amount)
    }
}
