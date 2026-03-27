package sollecitom.libs.lattice.sdk.kotlin.api

sealed interface Decision<out EVENT> {

    data class Accept<EVENT>(val event: EVENT) : Decision<EVENT> {

        companion object
    }

    data class AcceptWithResponse<EVENT, RESPONSE : Any>(val event: EVENT, val response: RESPONSE) : Decision<EVENT> {

        companion object
    }

    data class Reject(val reason: String) : Decision<Nothing> {

        companion object
    }

    companion object
}

fun <EVENT> accept(event: EVENT): Decision<EVENT> = Decision.Accept(event)

fun <EVENT, RESPONSE : Any> acceptWithResponse(event: EVENT, response: RESPONSE): Decision<EVENT> = Decision.AcceptWithResponse(event, response)

fun reject(reason: String): Decision<Nothing> = Decision.Reject(reason)
