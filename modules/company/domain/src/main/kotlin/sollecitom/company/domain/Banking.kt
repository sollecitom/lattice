package sollecitom.company.domain

import sollecitom.lattice.core.*

@JvmInline
value class AccountId(val value: String) {

    companion object {
        fun random(): AccountId = AccountId("acc-${Id.random()}")
    }
}

@JvmInline
value class Account(val id: AccountId) {

    val key: String get() = id.key()

    fun deposit(amount: Long, commandId: Id = Id.random()) = Deposit(id, amount, commandId)

    companion object
}

data class Deposit(
    val accountId: AccountId,
    val amount: Long,
    override val id: Id = Id.random(),
) : Command

data class DepositProcessed(
    val accountId: AccountId,
    val amount: Long,
    val newBalance: Long,
) : DomainEvent

data class GetBalance(val accountId: AccountId) : Query<Long>

data class AccountState(val balance: Long = 0)

object BankAccount : Aggregate<Deposit, DepositProcessed, AccountState> {

    override val initialState = AccountState()

    override fun decide(state: AccountState, command: Deposit): Decision<DepositProcessed> =
        accept(DepositProcessed(command.accountId, command.amount, state.balance + command.amount))

    override fun apply(state: AccountState, event: DepositProcessed) =
        state.copy(balance = state.balance + event.amount)
}

data class Balances(val byAccount: Map<AccountId, Long> = emptyMap())

object BalanceReadModel : ReadModel<DepositProcessed, Balances, GetBalance> {

    override val initialState = Balances()

    override fun apply(state: Balances, event: DepositProcessed) = Balances(
        state.byAccount + (event.accountId to (state.byAccount[event.accountId] ?: 0) + event.amount),
    )

    @Suppress("UNCHECKED_CAST")
    override fun <ANSWER> answer(state: Balances, query: Query<ANSWER>): ANSWER = when (query) {
        is GetBalance -> (state.byAccount[query.accountId] ?: 0L) as ANSWER
        else -> error("BalanceReadModel does not answer ${query::class.simpleName}")
    }
}

fun LatticeEnvironment.registerBanking() {
    registerAggregate(
        type = "account",
        aggregate = BankAccount,
        commandKey = { it.accountId.key() },
        eventKey = { it.accountId.key() },
    )
    registerReadModel(
        name = "balances",
        readModel = BalanceReadModel,
        eventKey = { it.accountId.key() },
        queryKey = { it.accountId.key() },
    )
}

internal fun AccountId.key() = value
