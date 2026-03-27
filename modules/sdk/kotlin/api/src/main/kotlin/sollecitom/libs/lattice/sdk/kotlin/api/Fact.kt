package sollecitom.libs.lattice.sdk.kotlin.api

import sollecitom.libs.swissknife.core.domain.identity.Id

sealed interface Fact {

    val id: Id
    val idempotencyKey: Id get() = id
}

interface Event : Fact

sealed interface Instruction : Fact

interface Command : Instruction

interface CommandWithResponse<out RESPONSE : Any> : Command

interface Query<out ANSWER> : Instruction
