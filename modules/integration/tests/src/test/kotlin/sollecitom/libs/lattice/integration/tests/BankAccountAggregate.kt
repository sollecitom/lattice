package sollecitom.libs.lattice.integration.tests

import sollecitom.libs.lattice.aggregate.domain.*

object BankAccountAggregate : IdempotentAggregate<BankAccountCommand, BankAccountEvent, BankAccountState> {

    override val type = AggregateType("bank-account")
    override val initialState = BankAccountState(balance = 0L, processedCommandIds = emptySet())

    override fun decide(state: BankAccountState, command: BankAccountCommand): CommandDecision<BankAccountEvent> = when (command) {

        is BankAccountCommand.Deposit -> CommandDecision.Accepted(
            BankAccountEvent.Deposited(amount = command.amount, commandId = command.commandId)
        )

        is BankAccountCommand.Withdraw -> {
            if (state.balance >= command.amount) {
                CommandDecision.Accepted(
                    BankAccountEvent.Withdrawn(amount = command.amount, commandId = command.commandId)
                )
            } else {
                CommandDecision.Rejected("Insufficient balance. Current: ${state.balance}, requested: ${command.amount}")
            }
        }

        is BankAccountCommand.Transfer -> {
            if (state.balance >= command.amount) {
                CommandDecision.Accepted(
                    BankAccountEvent.OutboundTransferInitiated(amount = command.amount, targetAccountId = command.targetAccountId, commandId = command.commandId)
                )
            } else {
                CommandDecision.Rejected("Insufficient balance for transfer. Current: ${state.balance}, requested: ${command.amount}")
            }
        }
    }

    override fun evolve(state: BankAccountState, event: BankAccountEvent): BankAccountState {

        val newProcessedIds = if (event.commandId != null) state.processedCommandIds + event.commandId!! else state.processedCommandIds
        return when (event) {
            is BankAccountEvent.Deposited -> state.copy(balance = state.balance + event.amount, processedCommandIds = newProcessedIds)
            is BankAccountEvent.Withdrawn -> state.copy(balance = state.balance - event.amount, processedCommandIds = newProcessedIds)
            is BankAccountEvent.OutboundTransferInitiated -> state.copy(balance = state.balance - event.amount, processedCommandIds = newProcessedIds)
            is BankAccountEvent.InboundTransferReceived -> state.copy(balance = state.balance + event.amount, processedCommandIds = newProcessedIds)
        }
    }

    override fun commandId(command: BankAccountCommand): CommandId? = command.commandId

    override fun hasProcessed(state: BankAccountState, commandId: CommandId): Boolean = commandId in state.processedCommandIds
}

sealed interface BankAccountCommand {
    val commandId: CommandId?

    data class Deposit(val amount: Long, override val commandId: CommandId? = null) : BankAccountCommand
    data class Withdraw(val amount: Long, override val commandId: CommandId? = null) : BankAccountCommand
    data class Transfer(val amount: Long, val targetAccountId: AggregateId, override val commandId: CommandId? = null) : BankAccountCommand
}

sealed interface BankAccountEvent {
    val commandId: CommandId?

    data class Deposited(val amount: Long, override val commandId: CommandId? = null) : BankAccountEvent
    data class Withdrawn(val amount: Long, override val commandId: CommandId? = null) : BankAccountEvent
    data class OutboundTransferInitiated(val amount: Long, val targetAccountId: AggregateId, override val commandId: CommandId? = null) : BankAccountEvent
    data class InboundTransferReceived(val amount: Long, val sourceAccountId: AggregateId, override val commandId: CommandId? = null) : BankAccountEvent
}

data class BankAccountState(val balance: Long, val processedCommandIds: Set<CommandId>)
