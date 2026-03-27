package sollecitom.libs.lattice.sdk.kotlin.api

import sollecitom.libs.swissknife.core.domain.identity.Id

class Bindings private constructor(
    internal val commandBindings: List<FactBinding<out Command>>,
    internal val eventBindings: List<FactBinding<out Event>>,
) {

    @Suppress("UNCHECKED_CAST")
    fun extractKeyForCommand(command: Command): Id? {

        val binding = commandBindings.find { it.factType.isAssignableFrom(command.javaClass) } ?: return null
        return (binding as FactBinding<Command>).keyExtractor(command)
    }

    @Suppress("UNCHECKED_CAST")
    fun extractKeyForEvent(event: Event): Id? {

        val binding = eventBindings.find { it.factType.isAssignableFrom(event.javaClass) } ?: return null
        return (binding as FactBinding<Event>).keyExtractor(event)
    }

    fun handlesCommand(command: Command): Boolean = commandBindings.any { it.factType.isAssignableFrom(command.javaClass) }

    fun handlesEvent(event: Event): Boolean = eventBindings.any { it.factType.isAssignableFrom(event.javaClass) }

    class Builder {

        @PublishedApi internal val commandBindings = mutableListOf<FactBinding<out Command>>()
        @PublishedApi internal val eventBindings = mutableListOf<FactBinding<out Event>>()

        inline fun <reified C : Command> command(noinline keyExtractor: (C) -> Id) {
            commandBindings.add(FactBinding(C::class.java, keyExtractor))
        }

        inline fun <reified E : Event> event(noinline keyExtractor: (E) -> Id) {
            eventBindings.add(FactBinding(E::class.java, keyExtractor))
        }

        fun build(): Bindings = Bindings(commandBindings.toList(), eventBindings.toList())
    }

    companion object
}

data class FactBinding<T>(val factType: Class<T>, val keyExtractor: (T) -> Id)

fun bindings(block: Bindings.Builder.() -> Unit): Bindings = Bindings.Builder().apply(block).build()
