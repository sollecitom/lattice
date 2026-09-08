package sollecitom.company.test.utils

import kotlinx.coroutines.CoroutineScope
import sollecitom.company.domain.Account
import sollecitom.company.domain.AccountId
import sollecitom.company.domain.registerBanking
import sollecitom.lattice.core.Lattice
import sollecitom.lattice.inmemory.ProjectionScheduler
import sollecitom.lattice.test.utils.latticeTest

fun Account.Companion.withRandomId(): Account = Account(AccountId.random())

fun bankingTest(
    scheduler: ProjectionScheduler = ProjectionScheduler.Immediate,
    body: suspend CoroutineScope.(Lattice) -> Unit,
) = latticeTest(scheduler = scheduler, register = { registerBanking() }, body = body)
