package sollecitom.companystubs

/**
 * Three levels of identity, per the design's identity model.
 *
 * Occurrence ids are deliberately absent: those are framework-generated fact metadata, not something a
 * company defines.
 */

@JvmInline
value class TenantId(val value: String)

@JvmInline
value class CustomerId(val value: String)

@JvmInline
value class AccountId(val value: String)

/** One user intent. Shared by every invocation that intent spawns. Client-generated. */
@JvmInline
value class ActionId(val value: String)

/** One request. Shared by every fact derived from that request. Client-generated. */
@JvmInline
value class InvocationId(val value: String)
