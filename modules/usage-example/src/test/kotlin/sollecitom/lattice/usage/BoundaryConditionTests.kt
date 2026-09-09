package sollecitom.lattice.usage

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import sollecitom.company.domain.Account
import sollecitom.company.domain.DepositProcessed
import sollecitom.company.test.utils.bankingTest
import sollecitom.company.test.utils.withRandomId
import sollecitom.lattice.core.Command
import sollecitom.lattice.core.CommandReceipt
import sollecitom.lattice.core.CommandRejectedException
import sollecitom.lattice.core.DomainEvent
import sollecitom.lattice.core.Id
import sollecitom.lattice.core.NoSuchReactionException
import sollecitom.lattice.core.acceptedOrThrow
import sollecitom.lattice.core.awaitReaction
import sollecitom.lattice.test.utils.recordedAsReceivedAt
import sollecitom.libs.swissknife.test.utils.assertions.failedThrowing

@TestInstance(PER_CLASS)
class BoundaryConditionTests {

    @Nested
    @TestInstance(PER_CLASS)
    inner class HistoryBounds {

        @Test
        fun `history after a position excludes it`() = bankingTest { lattice ->

            val account = Account.withRandomId()
            val accepted = lattice.submit(account.deposit(amount = 250)).acceptedOrThrow()
            val processed = accepted.awaitReaction<DepositProcessed>()

            val history = lattice.history(account.key, after = accepted.position).toList()

            assertThat(history).containsExactly(processed)
        }

        @Test
        fun `history through a position includes it`() = bankingTest { lattice ->

            val account = Account.withRandomId()
            val command = account.deposit(amount = 250)
            val accepted = lattice.submit(command).acceptedOrThrow()
            accepted.awaitReaction<DepositProcessed>()

            val history = lattice.history(account.key, through = accepted.position).toList()

            assertThat(history).containsExactly(command.recordedAsReceivedAt(accepted.position))
        }
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class Failures {

        @Test
        fun `awaiting an event the owner never produced fails immediately, naming what was produced`() = bankingTest { lattice ->

            val accepted = lattice.submit(Account.withRandomId().deposit(amount = 10)).acceptedOrThrow()

            val outcome = runCatching { accepted.awaitReaction<SomeOtherEvent>() }

            assertThat(outcome).failedThrowing<NoSuchReactionException>().messageContains("DepositProcessed")
        }

        @Test
        fun `a command nobody owns is rejected`() = bankingTest { lattice ->

            val receipt = lattice.submit(UnownedCommand())

            assertThat(receipt).isInstanceOf(CommandReceipt.Rejected::class)
            assertThat(runCatching { receipt.acceptedOrThrow() }).failedThrowing<CommandRejectedException>()
        }
    }
}

private data class SomeOtherEvent(val irrelevant: String = "") : DomainEvent

private data class UnownedCommand(override val id: Id = Id.random()) : Command
