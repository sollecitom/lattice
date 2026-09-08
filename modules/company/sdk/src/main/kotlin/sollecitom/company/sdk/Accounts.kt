package sollecitom.company.sdk

import sollecitom.company.domain.*
import sollecitom.lattice.core.*

class Accounts(private val lattice: Lattice) {

    suspend fun deposit(account: Account, amount: Long, commandId: Id = Id.random()): Recorded<DepositProcessed> =
        lattice.submit(account.deposit(amount, commandId)).acceptedOrThrow().awaitReaction()

    suspend fun balanceOf(account: Account, atLeast: Freshness): Answered<Long> =
        lattice.query(GetBalance(account.id), atLeast)
}
