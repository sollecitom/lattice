package sollecitom.libs.lattice.sdk.kotlin.api

import sollecitom.libs.swissknife.core.domain.identity.Id

data class CommandReceived(val command: Command, override val id: Id = command.id) : Event {

    override val idempotencyKey: Id get() = command.idempotencyKey

    companion object
}
