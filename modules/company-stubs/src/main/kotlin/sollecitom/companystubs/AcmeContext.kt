package sollecitom.companystubs

/**
 * The invocation context Acme happens to define: who is invoking, and under what circumstances.
 *
 * Shape is entirely Acme's. The framework treats it as opaque and obtains only what it declares it
 * needs — currently the invocation id (reply correlation) and a namespaced idempotency key (dedup).
 * Whether those arrive as interface members or as supplied functions is still an open fork.
 */
data class AcmeContext(
    val tenant: TenantId,
    val customer: CustomerId,
    val actor: Actor,
    val action: ActionId,
    val invocation: InvocationId,
) {
    /**
     * Namespaced by tenant and customer: action ids are client-generated, so an unnamespaced key would
     * let one tenant's action suppress another tenant's command.
     */
    val idempotencyScope: String get() = "${tenant.value}/${customer.value}/${action.value}"
}

sealed interface Actor {

    data class User(val id: String) : Actor

    data class Service(val name: String) : Actor
}
