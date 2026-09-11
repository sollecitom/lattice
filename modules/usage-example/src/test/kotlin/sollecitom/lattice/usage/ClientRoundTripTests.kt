package sollecitom.lattice.usage

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import sollecitom.company.domain.Account
import sollecitom.company.domain.BalanceReadModel
import sollecitom.company.domain.DepositProcessed
import sollecitom.company.sdk.Accounts
import sollecitom.company.test.utils.bankingTest
import sollecitom.company.test.utils.recordsDeposit
import sollecitom.company.test.utils.withRandomId
import sollecitom.lattice.core.Freshness
import sollecitom.lattice.core.acceptedOrThrow
import sollecitom.lattice.core.awaitReaction
import sollecitom.lattice.inmemory.get
import sollecitom.lattice.test.utils.recordsReceptionOf
import sollecitom.lattice.test.utils.sawAtLeast
import sollecitom.lattice.test.utils.wasRecordedAfter
import kotlin.time.Duration.Companion.milliseconds

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

            assertThat(processed).recordsDeposit(command, leavingBalance = 100)
            assertThat(processed).wasRecordedAfter(accepted)
        }

        @Test
        fun `an accepted command is in the log before it is processed`() = bankingTest { lattice ->

            val account = Account.withRandomId()
            val command = account.deposit(amount = 250)

            val accepted = lattice.submit(command).acceptedOrThrow()
            val commandReceived = lattice.history(account.key).first()

            assertThat(commandReceived).recordsReceptionOf(command, at = accepted.position)
        }
    }

    @Nested
    @TestInstance(PER_CLASS)
    inner class ReadingYourOwnWrites {

        // TODO review
        @Test
        fun `a query constrained to the effect position sees the write`() = bankingTest { lattice ->

            val accounts = Accounts(lattice)
            val account = Account.withRandomId()
            val processed = accounts.deposit(account, amount = 100)

            val balance = accounts.balanceOf(account, Freshness.AtLeast(processed))

            assertThat(balance.answer).isEqualTo(100)
            assertThat(balance).sawAtLeast(processed)
        }

        // TODO review
        @Test
        fun `a constrained query does not answer until the read model catches up`() = bankingTest { lattice ->

            val accounts = Accounts(lattice)
            val account = Account.withRandomId()
            val balanceProjection = lattice.projections[BalanceReadModel]
            balanceProjection.pause()

            val processed = accounts.deposit(account, amount = 100)
            val balance = async { accounts.balanceOf(account, Freshness.AtLeast(processed)) }

            assertThat(withTimeoutOrNull(200.milliseconds) { balance.await() }).isNull()

            balanceProjection.resume()

            val answered = balance.await()
            assertThat(answered.answer).isEqualTo(100)
            assertThat(answered).sawAtLeast(processed)
        }
    }
}
