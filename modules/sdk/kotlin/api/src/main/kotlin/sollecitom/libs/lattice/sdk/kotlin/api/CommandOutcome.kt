package sollecitom.libs.lattice.sdk.kotlin.api

sealed interface CommandOutcome {

    data object Processed : CommandOutcome

    data class Rejected(val reason: String) : CommandOutcome

    companion object
}
