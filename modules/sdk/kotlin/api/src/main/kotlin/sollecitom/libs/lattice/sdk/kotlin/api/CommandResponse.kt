package sollecitom.libs.lattice.sdk.kotlin.api

sealed interface CommandResponse<out RESPONSE : Any> {

    data class Responded<RESPONSE : Any>(val response: RESPONSE) : CommandResponse<RESPONSE> {

        companion object
    }

    data class Rejected(val reason: String) : CommandResponse<Nothing> {

        companion object
    }

    companion object
}
