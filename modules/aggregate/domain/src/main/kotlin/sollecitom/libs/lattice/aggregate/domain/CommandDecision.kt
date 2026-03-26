package sollecitom.libs.lattice.aggregate.domain

sealed interface CommandDecision<out EVENT> {

    data class Accepted<EVENT>(val events: List<EVENT>) : CommandDecision<EVENT> {

        constructor(vararg events: EVENT) : this(events.toList())

        companion object
    }

    data class Rejected(val reason: RejectionReason) : CommandDecision<Nothing> {

        companion object {

            operator fun invoke(reason: String) = Rejected(RejectionReason(reason))
        }
    }

    companion object
}

fun <EVENT> CommandDecision<EVENT>.eventsOrThrow(): List<EVENT> = when (this) {
    is CommandDecision.Accepted -> events
    is CommandDecision.Rejected -> error("Command was rejected: ${reason.value}")
}

fun <EVENT> CommandDecision<EVENT>.isAccepted(): Boolean = this is CommandDecision.Accepted
fun <EVENT> CommandDecision<EVENT>.isRejected(): Boolean = this is CommandDecision.Rejected
