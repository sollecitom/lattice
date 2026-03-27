package sollecitom.libs.lattice.sdk.kotlin.api

interface Aggregate<COMMAND : Command, EVENT, STATE> {

    val initialState: STATE

    fun handle(state: STATE, command: COMMAND): Decision<EVENT>

    fun apply(state: STATE, event: EVENT): STATE

    companion object
}
