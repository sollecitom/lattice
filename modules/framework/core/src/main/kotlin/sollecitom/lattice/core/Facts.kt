package sollecitom.lattice.core

@JvmInline
value class Id(val value: String) {

    override fun toString() = value

    companion object {
        fun random(): Id = Id(java.util.UUID.randomUUID().toString())
    }
}

sealed interface Fact

sealed interface Instruction : Fact

interface Command : Instruction {

    val id: Id
}

interface Query<out ANSWER> : Instruction

sealed interface Event : Fact

interface DomainEvent : Event

sealed interface Marker : Event {

    data class CommandReceived(val commandId: Id, val command: Command) : Marker

    data class CommandRejected(val commandId: Id, val reason: String) : Marker
}
