package sollecitom.lattice.usage

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import sollecitom.company.domain.Account
import sollecitom.company.domain.DepositProcessed
import sollecitom.company.sdk.Accounts
import sollecitom.company.test.utils.bankingTest
import sollecitom.company.test.utils.isResultOf
import sollecitom.company.test.utils.withRandomId
import sollecitom.lattice.core.Command
import sollecitom.lattice.core.CommandReceipt
import sollecitom.lattice.core.CommandRejectedException
import sollecitom.lattice.core.DomainEvent
import sollecitom.lattice.core.Freshness
import sollecitom.lattice.core.Id
import sollecitom.lattice.core.Marker
import sollecitom.lattice.core.NoSuchReactionException
import sollecitom.lattice.core.acceptedOrThrow
import sollecitom.lattice.core.awaitReaction
import sollecitom.lattice.inmemory.ManualProjectionScheduler
import sollecitom.lattice.test.utils.sawAtLeast
import sollecitom.lattice.test.utils.wasRecordedAfter
import sollecitom.libs.swissknife.test.utils.assertions.failedThrowing

@TestInstance(PER_CLASS)
class ClientRoundTripTests {

    @Nested
    @TestInstance(PER_CLASS)
    inner class SubmittingACommand {

        @Test
        fun `the request is recorded before it is processed, at an earlier position`() = bankingTest { lattice ->

            val account = Account.withRandomId()
            val command = account.deposit(amount = 100)

            val accepted = lattice.submit(command).acceptedOrThrow()
            val processed = accepted.awaitReaction<DepositProcessed>()

            assertThat(processed).isResultOf(command, leavingBalance = 100)
            assertThat(processed).wasRecordedAfter(accepted)
        }

        @Test
        fun `the log holds the command as a fact, then its result`() = bankingTest { lattice ->

            val account = Account.withRandomId()
            val command = account.deposit(amount = 250)

            val accepted = lattice.submit(command).acceptedOrThrow()
            val processed = accepted.awaitReaction<DepositProcessed>()
            val history = lattice.history(account.key).toList()

            assertThat(history.map { it.value }).containsExactly(
                Marker.CommandReceived(commandId = command.id, command = command),
                processed.value,
            )
            assertThat(history.map { it.position }).containsExactly(accepted.position, processed.position)
        }

        @Test
        fun `awaiting an event the owner never produced fails immediately, naming what was produced`() = bankingTest { lattice ->

            val accepted = lattice.submit(Account.withRandomId().deposit(amount = 10)).acceptedOrThrow()

            assertThat(runCatching { accepted.awaitReaction<SomeOtherEvent>() })
                .failedThrowing<NoSuchReactionException>()
                .messageContains("DepositProcessed")
        }

        @Test
        fun `a command nobody owns is rejected`() = bankingTest { lattice ->

            val receipt = lattice.submit(UnownedCommand())

            assertThat(receipt).isInstanceOf(CommandReceipt.Rejected::class)
            assertThat(runCatching { receipt.acceptedOrThrow() }).failedThrowing<CommandRejectedException>()
        }
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class ReadingYourOwnWrites {

        @Test
        fun `a query constrained to the effect position sees the write`() = bankingTest { lattice ->

            val accounts = Accounts(lattice)
            val account = Account.withRandomId()

            val processed = accounts.deposit(account, amount = 100)
            val balance = accounts.balanceOf(account, Freshness.AtLeast(processed.position))

            assertThat(balance.answer).isEqualTo(100)
            assertThat(balance).sawAtLeast(processed)
        }

        @Test
        fun `a constrained query does not answer until the read model catches up`() {

            val scheduler = ManualProjectionScheduler()

            bankingTest(scheduler) { lattice ->

                val accounts = Accounts(lattice)
                val account = Account.withRandomId()

                val processed = accounts.deposit(account, amount = 100)
                val balance = async { accounts.balanceOf(account, Freshness.AtLeast(processed.position)) }

                assertThat(withTimeoutOrNull(200) { balance.await() }).isNull()

                scheduler.release()

                assertThat(balance.await().answer).isEqualTo(100)
            }
        }
    }
}

private data class SomeOtherEvent(val irrelevant: String = "") : DomainEvent

private data class UnownedCommand(override val id: Id = Id.random()) : Command
