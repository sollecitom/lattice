# Lattice

A framework for event-driven backend systems, driven outside-in from the SDK perspective.

## Design Principles

### What the client provides
- Domain types: events, commands, queries (all subtypes of Fact)
- Aggregate logic: how to process commands and evolve state from events (pure, non-suspending functions)
- Read model logic: how to project events into query-friendly state and answer queries
- Bindings: which command/event types each aggregate handles, and how to extract routing keys

### What the framework handles
- Persistence, routing, partitioning, concurrency, recovery, distribution, idempotency
- State loading (event sourcing, snapshots, or both)
- Commands-as-events wrapping and lifecycle
- Just-in-time lookups for external data
- Event sinks for outbound integrations
- Topology management and co-partitioning

## Fact Hierarchy

All inputs to the system are **Facts**. A Fact is an immutable, identifiable record.

```
Fact (sealed)
├── Event
└── Instruction (sealed)
    ├── Command
    │   └── CommandWithResponse<RESPONSE>
    └── Query<ANSWER>
```

- **Fact**: Has an `id: Id` and an `idempotencyKey: Id` (defaults to the id). The idempotency key prevents duplicate processing on retries.
- **Event**: Something that happened. Can originate externally or internally.
- **Command**: An intention to change state. Outcome: `Processed` or `Rejected(reason)`.
- **CommandWithResponse\<RESPONSE\>**: A command that also produces a typed response for the caller.
- **Query\<ANSWER\>**: A request for information. Goes to read models, not aggregates.

## Commands Are Events

When a client submits a command, the framework wraps it as an event (e.g., `WithdrawCommandReceived`). **Aggregates only ever process events.** This has several consequences:

- The aggregate's unified topic contains a complete history: what happened, what was requested, and why.
- Commands, lookup results, and domain events are all stored in the same stream.
- Replay only applies events (domain events + lookup events). Command-received markers are skipped during replay — their effects are already captured by the result events.
- The aggregate's `handle` function is called by the framework when it encounters a command-received event in the stream. The framework unwraps the command before calling `handle`.

Example of a unified topic for one aggregate key:

```
[offset 1]  Deposited(amount=1000)                          ← external event, applied
[offset 2]  WithdrawCommandReceived(Withdraw(amount=300))   ← command-as-event, triggers processing
[offset 3]  ExchangeRateFetched(pair=EUR/USD, rate=1.08)    ← lookup event, fetched during processing of [2]
[offset 4]  Withdrawn(amount=300, newBalance=700)           ← result of processing [2] with state from [1]+[3]
```

On replay:
- [1] apply Deposited → balance = 1000
- [2] skip (command marker — effects captured by [3] and [4])
- [3] apply ExchangeRateFetched → state gets rate (recorded value, no API call)
- [4] apply Withdrawn → balance = 700

Deterministic. The full audit trail is preserved.

## Aggregate: Pure Domain Logic

An aggregate is a **pure, stateless function pair** — no I/O, no side effects, non-suspending:

```kotlin
interface Aggregate<COMMAND : Command, EVENT, STATE> {
    val initialState: STATE
    fun handle(state: STATE, command: COMMAND): Decision<EVENT>
    fun apply(state: STATE, event: EVENT): STATE
}
```

- `handle`: Given current state and a command, decide — `Accept(event)`, `AcceptWithResponse(event, response)`, or `Reject(reason)`.
- `apply`: Given current state and any event, return new state. Handles ALL events — produced by commands, received externally, lookup results.
- `initialState`: The starting state for a new aggregate instance.

**Why pure?**
- Trivially testable: `handle(testState, testCommand)` — no setup, no mocking.
- Deterministic: same inputs → same outputs, always. Critical for event-sourced replay.
- Non-suspending is a compile-time guarantee of no side effects.
- State is visible to the framework — it can snapshot, inspect, and serialize it.

**Why non-suspending?** Domain logic should not need coroutines. `handle` is a decision function — it looks at state and a command and decides. No I/O. No waiting. `apply` is arithmetic — it transforms state. If domain logic needs external data, it arrives as events (see Just-in-Time Lookups). If domain logic needs to call external systems, it emits an intent event and a reactor/sink handles the I/O (see Event Sinks).

**State is derived from events**: `state = events.fold(initialState) { s, e -> apply(s, e) }`. The framework manages state loading (from event replay, snapshots, or both). The aggregate never touches storage.

## Routing via Bindings

Domain types are **pure data** — no routing keys, no framework interfaces. Routing is declared at registration time:

```kotlin
environment.registerAggregate(
    type = "bank-account",
    aggregate = BankAccountAggregate,
    bindings = bindings {
        command<Withdraw> { it.accountId }
        command<SendPayment> { it.accountId }
        event<Deposited> { it.accountId }
        event<PaymentReceived> { it.recipientAccountId }
    },
)
```

Each binding says: "this aggregate handles facts of this type, and here's how to extract the routing key." The framework uses bindings to route facts to the correct aggregate instance.

Different aggregates can bind the same event type with different key extractors. A `Deposited` event might be routed to `BankAccountAggregate` by `accountId` and to `DailyLedgerAggregate` by `date`.

## Read Models: Query Side

Aggregates handle the write side (commands + events → state transitions). Read models handle the read side (events → query-optimized state → answers):

```kotlin
interface ReadModel<EVENT, STATE, QUERY, ANSWER> {
    val initialState: STATE
    fun apply(state: STATE, event: EVENT): STATE
    fun answer(state: STATE, query: QUERY): ANSWER
}
```

- **Single-key read models**: Consume events for one aggregate instance (e.g., balance for account X). The query carries the target key (e.g., `GetBalance(accountId = X)`).
- **Cross-key read models**: Consume events across all instances of an aggregate type (e.g., total daily transaction volume).

Aggregates do not handle queries. The write side is strictly separated from the read side.

## Submission vs Outcome

| | Submission | Outcome |
|---|---|---|
| **What** | Posting a fact into the system | The result of processing that fact |
| **For Command** | `lattice.accept(command)` → `Processed` or `Rejected(reason)` | No extra data |
| **For CommandWithResponse** | `lattice.acceptAndReceive(command)` → `Responded(response)` or `Rejected(reason)` | Typed response |
| **For Query** | `lattice.query(query)` → `Answered(answer)` or `Failed(reason)` | Typed answer |

The outcome does NOT contain the full domain event — that's an internal system artifact. For commands that need to return data to the caller, use `CommandWithResponse<RESPONSE>`. The aggregate returns `Decision.AcceptWithResponse(event, response)` — the event goes to the log, the response goes to the caller.

## Physical Architecture: Triage + Router + Unified Per-Aggregate Topics

### The Triage Topic

All facts enter the system through a single **triage topic** — the immutable journal, the system of record. Apache Pulsar with tiered storage, infinite retention.

**Partitioning**: The SDK publishes using the **fact's idempotency key** as the Pulsar partition key. The SDK knows nothing about bindings, aggregates, or routing — it publishes to one topic with one key it already has. This gives uniform distribution and ensures retries (same idempotency key) land on the same partition for Pulsar's producer deduplication.

**The SDK publishes to a single topic.** All routing intelligence lives in the router (framework side). Bindings can change over time (aggregates added/removed) without affecting the SDK.

### The Router

A **stateless** framework component that fans out facts from the triage topic to per-aggregate topics:

1. Consumes from the triage topic
2. For each fact, checks which aggregates are interested (from registered bindings)
3. For each interested aggregate, extracts the routing key using that aggregate's binding
4. Publishes the fact to the aggregate's topic, using the extracted key as the partition key

The router is horizontally scalable (each instance handles a subset of triage partitions). It's stateless — no data loss on crash.

### Unified Per-Aggregate Topics

Each aggregate type gets its own **unified topic**, partitioned by routing key. This single topic contains everything that happened for each aggregate key: command-received events, lookup events, domain events. It serves as both the input queue and the event log.

Each partition is consumed by exactly one aggregate actor instance. Pulsar guarantees ordering within a partition → sequential processing per key → no concurrency, no locks.

```
                         ┌─────────────────────────┐
                         │      Triage Topic        │
                         │   (all facts, immutable) │
                         │   (partitioned by        │
                         │    idempotency key)      │
                         └────────────┬────────────┘
                                      │
                              ┌───────┴───────┐
                              │    Router      │
                              │ (stateless,    │
                              │  fans out by   │
                              │  bindings)     │
                              └───┬───┬───┬───┘
                                  │   │   │
                    ┌─────────────┘   │   └─────────────┐
                    ▼                 ▼                  ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │ bank-account     │ │ fraud-detection   │ │ daily-ledger     │
        │ (by accountId)   │ │ (by accountId)    │ │ (by date)        │
        │ unified topic    │ │ unified topic     │ │ unified topic    │
        └──────────────────┘ └──────────────────┘ └──────────────────┘
```

Same event, different topics, different partition keys. The router handles the fan-out and re-keying.

### Aggregate Actor Processing

When the aggregate actor reads from its unified topic:

1. **Domain event** → call `apply(state, event)` → update state
2. **Command-received event** → check lookups (fetch if stale, store lookup events) → call `handle(state, command)` → store result event → call `apply(state, resultEvent)` → publish result event to triage for downstream consumers
3. **Lookup event** → call `apply(state, lookupEvent)` → update state

The actor is the single writer for its partition. It controls the ordering of lookup events and result events in the topic.

### Pipeline Flow (Cascading Aggregates)

No orchestrator needed. Events flow through the triage topic naturally:

```
SendPayment(from=A, to=B) → triage → router → bank-account[A]
    → BankAccount(A) emits PaymentSent → triage → router → bank-account[B]
        → BankAccount(B) applies PaymentReceived
        → triage → router → fraud-detection[B]
            → FraudDetection(B) processes...
```

Each step is fully decoupled. Aggregates don't know about each other.

## Topologies: Co-Partitioned Aggregate Groups

A **topology** groups aggregates that share the same partition key type and cascade frequently:

```kotlin
environment.registerTopology(
    name = "account-processing",
    partitions = 256,
) {
    aggregate(type = "bank-account", aggregate = ..., bindings = ...)
    aggregate(type = "fraud-detection", aggregate = ..., bindings = ...)
    aggregate(type = "compliance", aggregate = ..., bindings = ...)
}
```

Within a topology:
- All aggregates are **co-partitioned** (same partition count, same hash function)
- All are **co-located** (same node handles the same partition across all aggregates)
- Events cascade **locally** — no triage hop for intra-topology routing
- The topology **owns** the partition count — underlying topics are created/validated to match. Partition count mismatch is a configuration error.

Across topologies:
- Events go through the triage topic and the router re-keys as needed
- Different topologies can have different key types and partition counts

Topologies are an **optimization**, not a requirement. The triage model works correctly without them.

## Just-in-Time Lookups

For data that doesn't exist as events — external APIs, data vendors, reference data that costs per call:

```
Command-received event arrives at aggregate actor
    → Framework checks registered lookups for this command type
    → Cached and fresh (within TTL)? → proceed
    → Stale or missing?
        → Fetch from vendor API (suspending, may cost money)
        → Write result as event to the unified topic (ExchangeRateFetched)
        → Apply to aggregate state
        → NOW process the command
```

The lookup event is written to the unified topic **before** the command's result event. Both are written by the same actor (single writer per partition), so ordering is guaranteed:

```
[offset N]    CommandReceived(SendInternationalPayment)   ← triggers lookup
[offset N+1]  ExchangeRateFetched(rate=1.08)              ← fetched and stored before processing
[offset N+2]  PaymentSent(amount=500, convertedAt=1.08)   ← result, using recorded rate
```

On replay: skip [N], apply [N+1] (recorded rate, no API call), apply [N+2]. Deterministic.

Key properties:
- **Fetched data becomes a real event** — in the log, replayable, auditable
- **Framework tracks freshness** — not the aggregate. TTL per (lookup, key)
- **Cost control via TTL** — fetches only when stale, only for relevant command types
- **Failure policy is configurable** — use stale data, reject command, or retry
- **Aggregate stays pure** — it just sees events and applies them

```kotlin
environment.registerAggregate(
    ...
    lookups = listOf(
        lookup(
            name = "exchange-rate",
            triggeredBy = setOf(SendInternationalPayment::class),
            ttl = 5.minutes,
            fetch = { command -> exchangeRateApi.getRate(command.currencyPair) },
            toEvent = { data -> ExchangeRateFetched(...) },
            onFailure = LookupFailurePolicy.USE_STALE,
        ),
    ),
)
```

## Event Sinks (Outbound Integrations)

Sinks push events OUT to external systems — the output counterpart of lookups:

```kotlin
interface Sink<EVENT : Event> {
    suspend fun handle(event: EVENT)
}
```

Examples: send emails, call payment gateways, write to data warehouses, notify webhooks.

Sinks consume from the triage topic or per-aggregate topics. They're registered separately:

```kotlin
environment.registerSink(
    name = "payment-gateway",
    bindings = bindings {
        event<PaymentRequested> { it.accountId }
    },
    sink = PaymentGatewaySink(gatewayClient),
)
```

If a sink produces a result the system needs (e.g., a payment gateway returns a transaction ID), the sink publishes a **result event** back to the triage topic. This result event flows through the router to the relevant aggregate, which applies it like any other event:

```
PaymentRequested → Sink → gateway API → PaymentCompleted(txnId=...)
                                              → [Triage Topic] → router → aggregate applies
```

Sinks guarantee at-least-once delivery. Idempotency is the sink's responsibility (via the event's `idempotencyKey`).

## State Recovery

- **State = f(events)**. Replay from `initialState` through all events via `apply`, skipping command-received markers.
- **Snapshots** are an optimization — skip replaying from the beginning.
- **The unified per-aggregate topic** is the aggregate's complete history (commands, lookups, events). Recovery = load snapshot + replay events from the topic since the snapshot offset.
- **The triage topic** is the global system of record. Per-aggregate topics can be rebuilt from it by replaying through the router.

## SDK API

```kotlin
val environment = newLatticeEnvironment()
environment.registerAggregate(type = "bank-account", aggregate = ..., bindings = ...)
val lattice = environment.start()

// Publish external event
lattice.publish(Deposited(id = newId(), accountId = accountId, amount = 1000))

// Submit command, get outcome
val outcome = lattice.accept(Withdraw(id = newId(), accountId = accountId, amount = 300))
// outcome: Processed | Rejected(reason)

// Submit command with response
val response = lattice.acceptAndReceive(WithdrawWithConfirmation(...))
// response: Responded(WithdrawConfirmation) | Rejected(reason)

// Query a read model
val answer = lattice.query(GetBalance(id = newId(), accountId = accountId))
// answer: Answered(700L) | Failed(reason)

// Event history
val history: Flow<Event> = lattice.eventHistory(targetId)

// Stop
lattice.stop()
```

The SDK publishes everything to one topic (triage). It uses the fact's idempotency key as the partition key. It knows nothing about bindings, routing, or downstream aggregates.

## Module Structure

```
modules/
├── sdk/kotlin/
│   ├── api/                        # What the Kotlin client codes against
│   ├── test/specification/         # Contract tests + banking domain example
│   └── in-memory/tests/            # In-memory impl passes the contract tests
│
├── framework/
│   ├── api/                        # Server-side framework API
│   ├── implementation/in-memory/   # In-memory framework implementation
│   └── connector/embedded/         # SDK ↔ framework bridge (in-process)
```

Future SDKs (Python, Go, etc.) would code against a wire protocol (gRPC/Protobuf). Future connectors (e.g., `connector/grpc`) would expose gRPC endpoints delegating to the same framework API.

## Test Plan

Tests are self-contained examples using the banking domain, ordered from simplest to most advanced.

| # | Test | What it demonstrates |
|---|------|---------------------|
| 1 | Submit a command and receive the outcome | Publish a deposit, submit a withdrawal, verify processed. |
| 2 | Command rejection | Withdraw exceeding balance, verify rejection reason. |
| 3 | CommandWithResponse | Submit a command that returns typed response data. |
| 4 | Multiple commands to the same key | State evolves correctly across sequential commands. |
| 5 | Publish external event, verify state | Publish deposits, query balance via read model. |
| 6 | Query via read model | Register balance read model, submit query, verify answer. |
| 7 | Consume historical events | Read full event history for an account. |
| 8 | Idempotent command handling | Same command twice (same idempotency key), processed once. |
| 9 | Event-driven cascade (choreography) | SendPayment on A → PaymentSent → PaymentReceived on B. |
| 10 | Multiple aggregate types with routing | Register bank-account + fraud-detection, verify routing. |
| 11 | Aggregate pipeline | A emits → B reacts → C reacts (chain). |
| 12 | Cross-key read model | Project all transactions across all accounts (daily total). |
| 13 | Topology registration | Register co-partitioned aggregates in a topology. |
| 14 | Topology modification | Change partition count, add/remove aggregates from topology. |
| 15 | Just-in-time lookup | Fetch exchange rate before processing international payment. |
| 16 | Event sink | Payment gateway sink consumes event, result event flows back. |
| 17 | Lookup failure policy | Vendor API down, verify USE_STALE / REJECT behavior. |
| 18 | Lifecycle (start/stop) | Start, work, stop, verify cleanup. |

## Current Status

- Module structure: 6 modules
- SDK API: Fact hierarchy, Aggregate (pure, non-suspending), Bindings, Lattice, Decision, CommandOutcome/Response, QueryOutcome
- Banking domain example: AccountEvent, AccountCommand, GetBalance, BankAccountAggregate
- Test #1 passes: "Submit a command and receive the outcome"
- In-memory framework: routes commands/events via bindings, manages per-key state
- Not yet implemented: read models, topologies, lookups, sinks, CommandWithResponse flow, idempotency, commands-as-events in the unified topic
