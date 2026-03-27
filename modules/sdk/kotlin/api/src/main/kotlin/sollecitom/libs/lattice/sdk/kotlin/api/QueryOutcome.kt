package sollecitom.libs.lattice.sdk.kotlin.api

sealed interface QueryOutcome<out ANSWER> {

    data class Answered<ANSWER>(val answer: ANSWER) : QueryOutcome<ANSWER> {

        companion object
    }

    data class Failed(val reason: String) : QueryOutcome<Nothing> {

        companion object
    }

    companion object
}
