package sollecitom.libs.lattice.aggregate_repository.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.domain.RejectionReason

sealed interface CommandOutcome<out EVENT> {

    data class Accepted<EVENT>(val events: List<EVENT>, val newVersion: AggregateVersion) : CommandOutcome<EVENT> {

        companion object
    }

    data class Rejected(val reason: RejectionReason) : CommandOutcome<Nothing> {

        companion object
    }

    companion object
}

fun <EVENT> CommandOutcome<EVENT>.eventsOrThrow(): List<EVENT> = when (this) {
    is CommandOutcome.Accepted -> events
    is CommandOutcome.Rejected -> error("Command was rejected: ${reason.value}")
}

fun <EVENT> CommandOutcome<EVENT>.isAccepted(): Boolean = this is CommandOutcome.Accepted
fun <EVENT> CommandOutcome<EVENT>.isRejected(): Boolean = this is CommandOutcome.Rejected
