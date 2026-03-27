package sollecitom.libs.lattice.framework.connector.embedded

import sollecitom.libs.lattice.framework.implementation.in_memory.InMemoryFrameworkEngine
import sollecitom.libs.lattice.sdk.kotlin.api.*

class EmbeddedLatticeEnvironment : LatticeEnvironment {

    private val engine = InMemoryFrameworkEngine()

    override fun <COMMAND : Command, EVENT, STATE> registerAggregate(
        type: String,
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        bindings: Bindings,
    ) = engine.registerAggregate(type, aggregate, bindings)

    override suspend fun start(): Lattice = EmbeddedLattice(engine)

    companion object
}
