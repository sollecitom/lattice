package sollecitom.lattice.usage

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import sollecitom.company.domain.Account
import sollecitom.company.domain.AccountId
import sollecitom.company.domain.BankAccount
import sollecitom.company.domain.DepositProcessed
import sollecitom.company.domain.GetBalance
import sollecitom.company.test.utils.withRandomId
import sollecitom.lattice.core.Freshness
import sollecitom.lattice.core.MaterialisingReadModel
import sollecitom.lattice.core.Query
import sollecitom.lattice.core.acceptedOrThrow
import sollecitom.lattice.core.awaitReaction
import sollecitom.lattice.core.registerAggregate
import sollecitom.lattice.core.registerReadModel
import sollecitom.lattice.inmemory.get
import sollecitom.lattice.test.utils.LatticeUnderTest
import sollecitom.lattice.test.utils.latticeTest
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.ConcurrentHashMap

@TestInstance(PER_CLASS)
class MaterialisingReadModelTests {

    @Test
    fun `a read model owning its own store answers queries`() = materialisingTest { lattice ->

        val account = Account.withRandomId()

        val accepted = lattice.submit(account.deposit(amount = 100)).acceptedOrThrow()
        val processed = accepted.awaitReaction<DepositProcessed>()
        val balance = lattice.query(GetBalance(account.id), Freshness.AtLeast(processed))

        assertThat(balance.answer).isEqualTo(100)
    }

    @Test
    fun `it can be paused like any other projection`() = materialisingTest { lattice ->

        val account = Account.withRandomId()
        lattice.projections[ExternallyStoredBalances].pause()

        val accepted = lattice.submit(account.deposit(amount = 100)).acceptedOrThrow()
        val processed = accepted.awaitReaction<DepositProcessed>()
        val balance = async { lattice.query(GetBalance(account.id), Freshness.AtLeast(processed)) }

        assertThat(withTimeoutOrNull(200.milliseconds) { balance.await() }).isNull()

        lattice.projections[ExternallyStoredBalances].resume()

        assertThat(balance.await().answer).isEqualTo(100)
    }
}

// TODO I don't like this. Is there a way to re-use withBankingDomain and pass aggregates and read models, with default values? commandKey, eventKey, queryKey, shouldn't these be defined by the command, aggregate, and read model themselves?
private fun materialisingTest(body: suspend CoroutineScope.(LatticeUnderTest) -> Unit) = latticeTest(
    register = {
        registerAggregate(aggregate = BankAccount, commandKey = { it.accountId.value }, eventKey = { it.accountId.value })
        registerReadModel(readModel = ExternallyStoredBalances, eventKey = { it.accountId.value }, queryKey = { it.accountId.value })
    },
    body = body,
)

private object ExternallyStoredBalances : MaterialisingReadModel<DepositProcessed, GetBalance, Long> {

    override val id = "externally-stored-balances"

    private val store = ConcurrentHashMap<AccountId, Long>()

    override suspend fun apply(event: DepositProcessed) {
        store.merge(event.accountId, event.amount, Long::plus)
    }

    override suspend fun answer(query: GetBalance) = store[query.accountId] ?: 0L
}
