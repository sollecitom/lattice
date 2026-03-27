package sollecitom.libs.lattice.sdk.kotlin.api

interface LatticeEnvironment {

    fun <COMMAND : Command, EVENT, STATE> registerAggregate(
        type: String,
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        bindings: Bindings,
    )

    suspend fun start(): Lattice

    companion object
}
