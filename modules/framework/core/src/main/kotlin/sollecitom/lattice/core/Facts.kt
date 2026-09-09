package sollecitom.lattice.core

@JvmInline
value class Id(val value: String) {

    override fun toString() = value

    companion object {
        fun random(): Id = Id(java.util.UUID.randomUUID().toString())
    }
}

const val MAX_ROUTING_KEY_LENGTH = 512

fun validRoutingKey(key: String, source: String): String {
    require(key.isNotBlank()) { "$source produced a blank routing key: every instance would collapse onto one partition" }
    require(key.length <= MAX_ROUTING_KEY_LENGTH) { "$source produced a routing key of ${key.length} characters, over $MAX_ROUTING_KEY_LENGTH" }
    return key
}

sealed interface Fact

sealed interface Instruction : Fact

interface Command : Instruction {

    val id: Id
}

interface Query<out ANSWER> : Instruction

sealed interface Event : Fact

interface DomainEvent : Event

sealed interface Marker : Event

data class CommandReceived(val commandId: Id, val command: Command) : Marker

data class CommandRejected(val commandId: Id, val reason: String) : Marker
