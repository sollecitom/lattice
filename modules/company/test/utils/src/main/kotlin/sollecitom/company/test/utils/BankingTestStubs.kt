package sollecitom.company.test.utils

import kotlinx.coroutines.CoroutineScope
import sollecitom.company.domain.Account
import sollecitom.company.domain.AccountId
import sollecitom.company.domain.registerBanking
import sollecitom.lattice.test.utils.LatticeUnderTest
import sollecitom.lattice.test.utils.latticeTest

fun Account.Companion.withRandomId(): Account = Account(AccountId.random())

// TODO I don't like this name. `withBankingDomain()` instead?
fun bankingTest(body: suspend CoroutineScope.(LatticeUnderTest) -> Unit) =
    latticeTest(register = { registerBanking() }, body = body)
