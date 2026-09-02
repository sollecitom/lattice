package sollecitom.companystubs

/**
 * A deliberately small banking domain, to iterate the framework design against.
 *
 * Note what is *absent*: no framework supertype, no occurrence id, no idempotency key, no routing
 * annotation. These are plain data. Routing keys are domain fields (`accountId`) that the framework is
 * told about at registration rather than discovering structurally.
 */

sealed interface AccountCommand {

    val accountId: AccountId

    data class Withdraw(override val accountId: AccountId, val amount: Long) : AccountCommand

    data class SendPayment(override val accountId: AccountId, val amount: Long, val toAccountId: AccountId) : AccountCommand
}

sealed interface AccountEvent {

    data class Deposited(val accountId: AccountId, val amount: Long) : AccountEvent

    data class Withdrawn(val accountId: AccountId, val amount: Long, val newBalance: Long) : AccountEvent

    data class PaymentSent(val accountId: AccountId, val amount: Long, val toAccountId: AccountId, val newBalance: Long) : AccountEvent

    data class PaymentReceived(val accountId: AccountId, val amount: Long, val fromAccountId: AccountId, val newBalance: Long) : AccountEvent
}

sealed interface AccountQuery<ANSWER> {

    data class GetBalance(val accountId: AccountId) : AccountQuery<Long>
}

data class AccountState(val balance: Long = 0) {

    fun with(event: AccountEvent): AccountState = when (event) {
        is AccountEvent.Deposited -> copy(balance = balance + event.amount)
        is AccountEvent.Withdrawn -> copy(balance = balance - event.amount)
        is AccountEvent.PaymentSent -> copy(balance = balance - event.amount)
        is AccountEvent.PaymentReceived -> copy(balance = balance + event.amount)
    }
}
