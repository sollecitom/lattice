# Events Framework — Discussion

Working doc for the design of lattice: an event-driven, CQRS, event-sourced backend framework.

Started from scratch. The earlier exploration under this name was removed rather than continued — see
*Prior art: lattice* below. `facts`, a sibling repo in the workspace, was an earlier attempt at the same
idea and is now unwired; nothing is carried over from either by default.

---

## Goal

A highly opinionated framework for backend systems. Developers declare facts and handlers; the
framework supplies the API surface, routing, journaling, request-reply, and the read side. Everything
works by default; everything has an escape hatch.

Company-agnostic. Depends on swissknife only, never pillar.

---

## Decided

| # | Decision |
|---|---|
| D1 | Versioning is **major only** in the path. Compatible evolution within a major is resolved by schema, not by a new endpoint. |
| D2 | **No codegen.** Routes, OpenAPI, and schemas are derived at runtime from registrations. |
| D3 | **Aggregates only consume events and only produce events.** Pure, non-suspending. |
| D4 | **Commands always become events** (`CommandReceived`) before an aggregate sees them. No exceptions. |
| D5 | Three validation tiers: **structural** (schema) → **permissions** (+ integrity) → **invariant** (aggregate state). |
| D6 | Commands rejected at structural / permissions / integrity **never reach the log**. Only invariant rejection is journaled. |
| D7 | `/events` is roughly symmetric with `/commands`, ingesting facts that happened **outside** the system. Structural + permissions validation only. |
| D8 | Request-reply must be **race-free**: a reply published before the caller subscribes must never be lost. |
| D9 | Read side is **full lifecycle** — declare, build, track position, rebuild, version — with escape hatches. |
| D10 | **Kafka support must be real**, proven by the same conformance suite as Pulsar, in CI. |
| D11 | **Queries are journaled**, on their own topics. They are `Fact`s like everything else. |
| D12 | Journaling queries **replaces request logging**; it is not additional cost. |
| D13 | Audit is **at-least-once, never at-most-once**, and the mechanism must be able to prove it. |
| D14 | **Record what you can't derive, derive what you can.** |
| D15 | Topology is **derived from registrations**. A separate `provision` image applies it with admin credentials. The runtime **verifies and never creates**. |
| D16 | **Aggregate types are static**, declared at build time. |
| D17 | **Partition assignment is the broker's job.** No custom cluster membership. |
| D18 | Three participants: **aggregates**, **reactors**, **projections**. Different rules for each. |
| D19 | **Events are self-contained.** Unknown fields pass through by default. |
| D20 | **Nothing on the log's critical path may depend on any system other than the broker.** |
| D21 | **Payloads are inline by default.** Claim-check is a deliberate availability-for-storage trade — opt-in, never in the aggregate path. |
| D22 | Poison records **halt the partition**. No automatic DLQ. Skips go in a registry consulted *before* consumption. |
| D23 | Schema compatibility is **`FULL_TRANSITIVE`**, enforced in the producer's CI. |
| D24 | **Upcasting is core**, not hardening. |
| D25 | **Pulsar is the primary adapter. Kafka-API is the second** (Redpanda for local/dev). |
| D26 | **The broker is the system of record. Any event store is derived** — rebuildable, disposable, with a broker-only fallback. |
| D27 | Hydration uses a **derived recovery store** (snapshots), in shared storage, keyed by aggregate id. |
| D28 | **Prescriptive about mechanism. Permissive about shape.** |
| D29 | Two modules: generic **`core`** (no dependencies) and opinionated **`starter`** built only on core's public API. |
| D30 | The test-domain module must import **`core` and nothing else** — no swissknife, no adapters. *(Weakened from "must compile without importing the framework", which D32 makes unachievable.)* |
| D31 | The **in-memory implementation must be semantically faithful** — real partitions, positions, per-key ordering, replay. Not a mock. |
| D32 | **Approach A.** Domain types implement framework interfaces; `Fact`/`Instruction`/`Event`/`Command`/`Query` are framework-owned. |
| D33 | **Exactly one owner answers for a command.** Ambiguous command bindings are rejected at registration. Events stay fan-out. |
| D34 | **Markers vs domain events is a type distinction**, so `apply(state, event)` cannot receive something replay must skip. |
| D35 | The log entry for a submitted command is **`CommandReceived`** — "accept"/"reject" is reserved for the owner's verdict. |

---

## Core model

```
Fact
├── Event                     ← the only thing aggregates consume or produce
│   ├── domain events         ← replay APPLIES these
│   └── markers               ← replay SKIPS these
│       ├── CommandReceived
│       └── CommandRejected
└── Instruction
    ├── Command               ← always wrapped as CommandReceived : Event
    └── Query<ANSWER>         ← wrapped as QueryReceived, on its own topic
```

Framework-owned, implemented by domain types (D32).

### Markers are a type distinction, not a flag (D34)

Only **one** category distinction earns its place, and it is *does replay apply this?* Everything else
you might reach for is metadata — internal-vs-external is **provenance**, which fact metadata already
carries, not a new category.

`CommandRejected` sits alongside `CommandReceived`: state did not change, so replay must skip it, but
the refusal belongs in the audit trail and the outcome relay needs something to publish.

Making it a **type** distinction rather than a boolean means `apply(state, event)` can only ever be
handed something applicable. Lattice has a literal `// TODO on replay: skip CommandReceived markers` — a
runtime filter someone has to remember. Making it unrepresentable costs one sealed level and removes the
whole bug class.

### Participants (D18)

| | State | Suspending | Side effects | Failure blast radius |
|---|---|---|---|---|
| **Aggregate** | Owns entity state, derived from its partition | No — pure | No | Its partition |
| **Reactor** | None (or external) | Yes | Yes — external calls | Its own consumption, must not stall aggregates |
| **Projection** | Owns read-model state | Yes | Writes to its store | Its own lag |

The reactor is the participant that receives an event, runs a suspending function that may call the
outside world, and emits a resulting event. It is **not** an aggregate — D3 keeps those pure — and the
distinction is load-bearing for D20: reactors can stall, so they must consume independently, and their
stalls must not stall derivation.

Consequence of at-least-once plus side effects: **reactors must be idempotent**, or the framework must
supply outbound idempotency keys. See Q2.

---

## The command boundary: one owner answers, everything else is choreography (D33)

### The problem that forced this

A single `Processed` outcome is a fiction if any number of things can react to `CommandReceived`. The
reactor set is open — new reactors get added later — so there is no moment at which "the command is
done", and no single position that represents "the effect".

The fix is to narrow what the outcome *means*, not to remove it:

> **The outcome is the designated owner's verdict.** It does not claim the causal chain completed.

### Why exactly one owner, and why that isn't orchestration

**Orchestration is about knowledge, not cardinality.** An orchestrator knows its participants and
sequences them — "call inventory, then payment, and if payment fails, release the inventory". The
workflow lives in the coordinator.

A command owner knows none of that. It receives a fact, checks its own invariant, emits a fact. It calls
nobody, sequences nothing, and cannot tell whether zero or fifty things react to what it emitted. Adding
a fraud checker tomorrow requires no change to it and it will never know.

The distinguishing test: **does the deciding component know about downstream participants?**
Orchestration says yes. One-owner-per-command says no. "Exactly one owner" states *where an invariant
lives*, not who tells whom to do what.

**Why one and not several — rejection is only meaningful if it prevents the effect.** Take
`Withdraw(account=A, amount=500)` handled by both account A and a `FraudCheck` aggregate:

- A evaluates `balance >= 500`, accepts, writes `Withdrawn`
- FraudCheck evaluates its own rules, rejects

Different partitions, no shared transaction, so A's write already happened. FraudCheck's "rejection"
prevented nothing. And the client received two contradictory answers with no basis for preferring
either.

That is the incoherence, stated precisely: **with multiple deciders, "reject" degrades into "abstain".**
It stops meaning *this did not happen* and starts meaning *I personally did not participate, but others
may have*. Every caller then has to reason about partial acceptance, and `Rejected(reason)` becomes a
lie.

### The seam

```
submit ──▶ [ one owner decides ] ──▶ CommandReceived ──▶ [ anyone reacts ] ──▶ ...
           refusable request                              facts
```

Before the write it is a **request** — refusable, so someone must be responsible for refusing it. That
is inherently singular. After the write it is a **fact** — facts cannot be refused, so no owner is
needed and any number of consumers may react, cascade, and produce further events without coordination.

Which is why **D4 is pro-choreography rather than a concession against it**: converting commands to
events immediately is precisely what creates the zone where choreography is well-defined. Without it,
commands would float around being multiply-handled with no coherent semantics.

**Choreography starts at `CommandReceived`, not at the result event.** Other things may legitimately
react to the command event itself — an audit sink, an advisory fraud pre-check. The owner is not
privileged because it reacts first; it is privileged because **its verdict is the one reported to the
client**. Everything else consuming that same event is already choreography.

### The rule that falls out

> If several things should act and **none can refuse** — that is an event. Publish it.
> If **one thing can refuse** — that is a command. One owner.

So the command/event distinction *is* the fan-out distinction. Nobody is forced into single-handling
when they wanted fan-out; they only have to be honest about whether refusal is possible.

### The multi-invariant case is a saga, and sagas here are choreographed

The strongest objection would be: "withdraw only if balance sufficient **and** not frozen **and** under
daily limit", with those invariants living in different aggregates. Two answers, neither needing an
orchestrator:

1. **They are the same aggregate.** If they must hold atomically they are one consistency boundary —
   that is what aggregate design means.
2. **It is a multi-step flow.** A accepts provisionally → `WithdrawalRequested` → FraudCheck reacts →
   `WithdrawalCleared` or `WithdrawalBlocked` → A reacts and finalises or compensates.

Every step in (2) is an event reaction. No component knows the whole flow; each knows only what it
reacts to and what it emits.

### What it costs

The framework must **detect and reject ambiguous command bindings at registration**. Two aggregates
binding `Withdraw`, even with different key extractors, becomes a startup error rather than a silent
fan-out.

Occasionally annoying: if the same command *shape* is genuinely wanted in two places, you need two
command types, or you reconsider whether it should be an event. The upside is that lattice's silent
first-match-wins (`commandBindings.find { ... }`) becomes impossible — it currently picks whichever
aggregate registered first and tells nobody.

---

## Command lifecycle and the client API

### Three identities, three jobs

| | Scope | Generated by | Job |
|---|---|---|---|
| **action id** | one user intent | client | idempotency key, namespaced by tenant/customer |
| **invocation id** | one request | client | the batch envelope — one request may carry several commands |
| **command id** | one command | framework (client may override) | **reply correlation** |

The command id **is** the occurrence id of the `CommandReceived` record — one submission, one record,
one identity. The receipt therefore carries both `commandId` (identity) and `position` (location) of the
same thing.

**Client-specifiable via a defaulted factory argument** — implicit in tests, explicit when a client must
reattach after a crash:

```kotlin
fun deposit(accountId: AccountId, amount: Long, id: Id = newId()) = Deposit(id, accountId, amount)
```

**Keep it orthogonal to dedup.** The command id is *identity*; the action id is the *idempotency key*.
The framework must not dedup on the command id — reusing one is a client bug, whereas reusing an action
id is a deliberate retry. Conflating them would let accidental id reuse silently swallow a legitimate
second command.

### The receipt makes the validation tiers visible in the type system

```kotlin
sealed interface Receipt {
    data class Accepted(val commandId: Id, val position: Position) : Receipt {
        suspend fun verdict(): Verdict
    }
    data class Rejected(val reason: String) : Receipt
}

fun Receipt.acceptedOrThrow(): Receipt.Accepted = when (this) {
    is Receipt.Accepted -> this
    is Receipt.Rejected -> throw CommandRejectedException(reason)
}
```

The two rejection kinds are **different types, not one type with different reasons**, because their
consequences differ:

- **Synchronous rejection** (tiers 1–2: structural, permissions, integrity) — nothing was written. Safe
  to retry a corrected command freely. This is D6 expressed in the API rather than in a comment.
- **Asynchronous refusal** (tier 3: the owner's invariants) — the attempt is journaled and permanently
  part of history.

`verdict()` lives on `Accepted` rather than being an extension: only accepted commands have verdicts, so
the type carries it. Tell-don't-ask applies cleanly here because `Accepted` is a framework type — no
prescription cost. Two extensions at most; `receipt.acceptedOrThrow().verdict()` is the whole ergonomic
story.

### Two positions, arriving at different times — and a trap

| | Available | Meaning |
|---|---|---|
| `Accepted.position` | **immediately** | where `CommandReceived` landed |
| `Verdict.Accepted.position` | after the owner decides | where the **result event** landed |

The immediate one is valuable on its own: a durable "your request is recorded at P" that is meaningful
even if the client never awaits. That is the real payoff of async submission.

> **Trap: the acceptance position is not sufficient for read-your-own-writes.** It looks like it should
> be. The effect is written at a *higher* offset in the same partition, so a read model that has consumed
> past P1 may not have seen P2. Passing the acceptance position to `atLeast` yields a test that passes
> most of the time.

This deserves to be written down because it is exactly the kind of thing that gets "optimised" into
place later by someone who notices they can skip the await.

### Verdict as the primitive, reactions as sugar

```kotlin
sealed interface Verdict {
    data class Accepted(val events: List<Event>, val position: Position) : Verdict
    data class Refused(val reason: String) : Verdict
}

suspend fun awaitVerdict(): Verdict                                          // primitive — decidable
suspend inline fun <reified T : Event> Receipt.Accepted.awaitReaction(): T   // sugar
```

The owner produces exactly one verdict, so **once it lands the framework knows the owner's contribution
is complete**. `awaitReaction<T>()` therefore throws immediately when the verdict arrives without a `T`,
rather than hanging until a timeout — a strictly better failure than any type bound would have bought.

**Scope discipline:** `awaitReaction` means *the owner's reaction*, not "any reaction anywhere". That is
what makes fast-fail possible. Awaiting a **downstream** reaction — another aggregate reacting to your
event — is genuinely undecidable, since the reactor set is open. If ever wanted it needs a separate,
explicitly timeout-bounded API, and it can offer neither guarantee. The naming must keep the two apart
so nobody expects the second from the first.

### Two-layer SDK: framework SDK, then domain SDK

Command→event causality is **domain knowledge**. The framework cannot know that a deposit results in
`DepositProcessed`, so encoding it in framework generics was always trying to make the framework hold
knowledge it does not have. A domain SDK holds it natively:

```kotlin
// company SDK — knows the domain, built on the framework SDK
class Accounts(private val lattice: Lattice) {
    suspend fun deposit(accountId: AccountId, amount: Long): DepositProcessed =
        lattice.submit(Deposit(accountId, amount))
               .acceptedOrThrow()
               .awaitReaction<DepositProcessed>()
}
```

Client code becomes `accounts.deposit(id, 100)` — no type parameter to supply, so none to get wrong.

**This settles the type-bound question.** `awaitReaction<T>()` stops being application-facing: it is
called once per business operation, in one file, by whoever defined the operation — the person least
likely to name the wrong event and most likely to notice immediately. The risk is concentrated rather
than scattered, and the runtime fast-fail covers it.

**The stronger reason to build it: it is a sharper test of the framework API than the tests are.** Tests
can quietly work around an awkward API — you write whatever incantation makes the assertion pass. An SDK
layer cannot; it has to wrap the framework *cleanly enough for someone else to use*, which surfaces
friction the tests would absorb silently. If `Accounts.deposit()` comes out ugly, the framework API is
wrong. It is also a second consumer, for the same reason two broker adapters beat one.

**Two guardrails:**

- **The domain SDK must not become an orchestrator.** If `transfer()` awaits step 1, submits step 2,
  awaits step 3, that is client-side orchestration wearing an SDK costume — and it breaks the moment the
  client dies mid-flow. The SDK's job is to *name* a business operation and know what to await.
  Sequencing stays in the system, choreographed.
- **Be explicit about await scope.** Owner-scoped waits are decidable and fail fast; downstream waits
  need a timeout and cannot. Those should look different in the SDK.

**Module shape:**

| Module | Contains | Imports |
|---|---|---|
| `company-stubs` | domain facts, context, identity — pure data | `core` only (D30) |
| `company-sdk` | business operations — `Accounts.deposit(...)` | framework SDK + stubs |
| `usage-example` | tests written the way a real client would write them | `company-sdk` |

Keeps D30 clean: stubs stay data, framework usage lives one layer up. Built lazily — one module, one
`deposit()`, growing as the tests do.

---

## Genericity and layering

The stated goal is a *highly opinionated* framework; the stated worry is over-prescription. Those only
conflict if applied to the same things (D28):

> **Mechanism** is what the framework does — commands become events, aggregates are pure, poison halts
> the partition, queries are journaled, partition ownership is actor ownership. Non-negotiable; it *is*
> the product. Softening it yields a DI container.
>
> **Shape** is what flows through — what an id is, what a command looks like, what a context contains.
> Entirely the developer's.

### Two modules (D29)

- **`core`** — generic, no defaults, **no dependencies** (coroutines at most). The thing developers code
  against stays pristine. Notably this means *not* exposing swissknife's `Id` in the public API: that
  would be prescription via the back door, since every user then inherits swissknife.
- **`starter`** — opinionated defaults: a stock id, a stock context, standard codecs, batteries.

Adapters (Pulsar, Kafka, NATS, http4k, Avro) depend on swissknife freely. The starter must be written
using only core's public API — if it needs something core doesn't expose, core is too closed.

### Type parameters

| | Verdict |
|---|---|
| Params on the **framework type** — `Framework<CTX, CMD, EVT>` | **Yes, minimum count, unbounded.** |
| Params on **developer-facing abstractions** — `Query<ANSWER>`, `Aggregate<C, E, S>`, `Decision<E>` | **Required.** These are the typesafety. |
| Params on **functions** — `fun <C, E, S> registerAggregate(...)` | **Good.** Inferred at the call site, don't escape into the framework's type. |
| **Reified** params for binding — `inline fun <reified C> command(...)` | **Good.** Lattice already does this. |

**Unbounded is the point.** If the params have no bounds, the framework *cannot* inspect those types and
is forced to obtain capabilities another way — the strongest possible statement of non-prescription. A
bound is prescription wearing a type parameter's clothes.

**Count matters, because Kotlin has no associated types** — you can't bundle them behind
`Framework<D : DomainTypes>` and pull `D.Event` out, the way Scala or Rust would. Each earns its slot:

- `EVT` — **yes**: `Flow<EVT>` gives exhaustive `when` over a sealed domain hierarchy; `Flow<Any>` throws
  that away.
- `CMD` — **yes**: `accept(cmd: CMD)` closes the world to *their* commands, which is tighter than any
  framework supertype can manage (a marker is open by construction).
- `CTX` — **yes, reluctantly**: context parameters on handler signatures need the type named somewhere.
- `ID` — **marginal**: include only if typed correlation ids in outcomes matter.
- `KEY` — **no**: internal plumbing, derived by functions, rarely surfaced.

**Virality is contained by two things:** a `typealias AcmeFramework = Framework<AcmeContext,
AcmeCommand, AcmeEvent>` at the composition root (docs should lead with it), and a **generic facade over
a concrete engine** — the engine works in wire/erased terms and the facade casts at exactly one
boundary, so internal code and third-party extensions don't thread parameters everywhere.

### Open fork: how capabilities are supplied

Both approaches are live. They differ in how the framework obtains what it needs from a domain type.

**Approach A — capability interfaces with a generic id type**

```kotlin
interface Command<out ID : Any> { val id: ID }

Framework<ID : Any, COMMAND : Command<ID>, ...>
```

Mandates *structure*, not *type* — materially different from lattice's `Fact { val id: Id }`, which
mandated both.

| Pros | Cons |
|---|---|
| Framework reads `command.id` directly; one less lambda per registration | Member name and shape are mandated — a type with `orderId` needs a delegating property, on every type |
| `ID` stays theirs — `Command<UUID>`, `Command<OrderId>` | Domain module acquires a framework dependency |
| Compiler enforces one id type across the app | Composite identity needs a wrapper |
| `out ID` gives useful covariance | Slippery slope: if `id`, why not `idempotencyKey`, causation, timestamp? Either it grows heavy or the stopping point is arbitrary |
| Logic and its caches live on objects with a clear lifecycle | Duplicates the envelope, which already owns occurrence identity |

**Approach B — extraction functions supplied at registration**

```kotlin
registerAggregate<AcmeCommand, AcmeEvent, AcmeState>(
    handler = BankAccountAggregate,   // types checked
    idOf    = { it.uuid.toString() }, // shape extracted
    keyOf   = { it.accountId },
)
```

| Pros | Cons |
|---|---|
| Domain types need **no** framework import — no supertype, no annotation | Registration is more verbose |
| Works with types you don't control | Discovery is harder — a lambda list doesn't autocomplete like an interface |
| Nothing mandated about member names or identity shape | Nowhere natural to hold a cache, if supplied as bare lambdas |

**The question underneath both:** is *occurrence id* domain data or fact metadata?

Note it is **not** invocation context — those are orthogonal (see three-parts above), and an earlier
version of this argument wrongly conflated them. It's also distinct from the **entity/routing key**
(`accountId`), which is unambiguously domain data and is extracted by bindings under either approach.

*For fact metadata (→ B):* `PlaceOrder` doesn't intrinsically have an occurrence id — constructing it
twice yields the same command, submitted as two occurrences. Identity of *the submission* is the
framework's concern, and if it lives in fact metadata then `Command<ID>` has no members left, leaving a
member-less bound that the unbounded type parameter dominates. Client-supplied idempotency keys arrive
as a parameter or header, as HTTP settled with `Idempotency-Key`, not as a body field.

*For domain data (→ A):* the command is the unit a client **retries**, so an id travelling with the
object is harder to get wrong than one passed alongside it. Identity stays attached to the thing it
identifies. And behaviour belongs on objects, which have lifecycles that can hold caches — though that
last point is satisfiable under B too, by grouping extractors into `fun interface`s.

### Resolved: Approach A (D32)

**Decision: domain types implement framework interfaces.** The `Fact`/`Instruction`/`Event`/`Command`/
`Query` hierarchy is framework-owned; capabilities arrive as interface members where they fit.

Rationale, in the user's words: *"I don't care about no dependency on framework. I'm happy with
interfaces the framework provides and expect to be implemented."*

What this costs and settles:

- **D30 weakens.** "The test-domain module must compile without importing the framework" becomes
  unachievable, because `Deposit : Command` *is* a framework import. It degrades to "imports `core` and
  nothing else" — still meaningful (it stops swissknife and adapter types leaking into domain code) but
  no longer the strongest, compiler-enforced form of the guarantee.
- **D29 survives unchanged.** `core` still has no dependencies. Domain types now depend on core, which
  is precisely the point.
- **`Record<EVT>` is unnecessary.** It existed only to keep a framework supertype off domain events. The
  log is `Flow<Event>`.
- **One thing still doesn't fit the interface shape**, whichever way the fork had gone: the idempotency
  key spans context *and* fact, so it sits naturally on neither object and remains a supplied function
  or a separate strategy object.

### Rejected: `Command<out EVENT>` binding the command to its owner's event family

Raised as a way to make `awaitReaction<T>()` type-safe — `Deposit : Command<AccountEvent>` would reject
`deposit.awaitReaction<ShipmentDispatched>()` at compile time.

Dropped for two reasons:

**Coupling.** The declaration says "whoever owns me emits `AccountEvent`s" — the request type encodes
its handler's internals. Re-home the command to a different aggregate and the command declaration
changes. In a choreographic design the request should be ignorant of who answers it.

**It's a strict subset of what runtime already catches.** Because the owner's verdict is decidable (see
below), the framework knows exactly which events a command produced the moment the verdict lands:

| | Wrong family (`ShipmentDispatched`) | Right family, wrong event (`Withdrawn`) |
|---|---|---|
| Type bound | compile error | compiles, then **hangs** |
| Verdict fast-fail | immediate, clear error | immediate, clear error |

The bound pays coupling for redundancy, and leaves the worse failure mode (a hang) uncovered.

**What is given up:** `awaitVerdict()` returns `List<Event>` rather than `List<AccountEvent>`, so no
exhaustive `when` over the family directly from the verdict. That was the one real argument for keeping
it, and it's largely mooted by the SDK layering below — application code doesn't call `awaitVerdict()`.

### Behaviour vs data vs lookup

Three kinds, and conflating them causes trouble:

| Kind | Example | Where it lives | Aggregate path? |
|---|---|---|---|
| **Behaviour** the object can perform | `handle(state, command)`, `apply(state, event)` | On the object — tell, don't ask | Yes |
| **Data the framework needs for its own work** | routing key, occurrence id | Extracted — the object can't route itself | Yes, must be pure |
| **Data that lives elsewhere** | idempotency records, correlation with prior events | A **suspending lookup** — a distinct capability | **No** — violates D3 |

The third is why just-in-time lookups exist: fetch *before* `handle`, write the result as an event, so
the aggregate stays pure and replay stays deterministic without an external call.

### Capability contract

Whichever approach wins, capabilities are **long-lived, shared, and called concurrently** from every
partition consumer. That's a contract, not an implementation detail:

- thread-safe caches, not naive maps
- **bounded** caches — same byte-bounded lesson as the blob cache
- framework owns construction and disposal; `AutoCloseable` where resources are held
- **on the aggregate path, pure-modulo-memoization**: a cache may change speed, never results. A
  capability that broke that would break replay silently

Caching is worth real money here: lattice does `commandBindings.find { it.factType.isAssignableFrom(...) }`
— a reflective linear scan **per message**. An object precomputes a `Class → binding` map once at
construction.

`fun interface` gets both ergonomics: a lambda at the call site, a stateful class when someone needs a
cache, no API change between them.

### The test-domain module is the enforcement mechanism (D30)

Define the fake company's domain in a test-scoped module — `OrderPlaced`, `OrderId`, `AcmeContext` — and
hold this invariant:

> **The test-domain module must compile without importing the framework.**

Then every accidental coupling — a marker interface that crept in, a required supertype, a framework
type in a signature — is a compile error in CI rather than something noticed in review months later. A
real module with a real dependency rule, not a convention.

*(Approach A cannot satisfy this literally, since `Command<ID>` is a framework import. If A wins, the
rule weakens to "the domain module imports `core` and nothing else" — worth knowing that choosing A
costs the strongest form of the guarantee.)*

---

## Event content: self-containment and pass-through

Long thread; recording the reasoning because two intermediate positions were wrong.

### Why self-contained (D19)

If a derived event only *references* its predecessor, every consumer that needs the predecessor's data
must load it. With no random-access store that means either an indexed event store per processor, or a
shared central one — a single point of failure and the thing being avoided. So events carry what
downstream needs.

**"Read the source fact from the log" is not a viable alternative.** Logs are good at sequential scans
and bad at random access; see the rejected lookback design below.

### Entity state does not substitute for chain context

The correction that settled it. Two different things:

- **Entity state** — accumulated across *many* chains. A balance is the fold over hundreds of past
  deposits. Long-lived, owned by the aggregate, keyed by entity.
- **Chain context** — the causal thread of *one* command in flight. Short-lived, crosses partitions and
  components, **owned by nobody**.

Replay gives you the first and nothing of the second. When an aggregate handles `PaymentSent`, its state
knows the balance but not that this chain began with `reference="invoice-42"` — unless a developer
anticipated the need and stashed the in-flight request into entity state, which is exactly the
saga/process-manager correlation boilerplate the framework should remove.

Same collapse for projections: they *could* keep a `correlationId → details` map, but that's every
projection hand-rolling the same join and subscribing to topics it otherwise wouldn't need.

So self-containment is a property of **every record in a chain**, not just boundary-crossing ones.

### Pass-through of unknown fields

The chain is the unit of work and nothing holds its context, so it travels with the chain. Pass-through
is what makes that maintainable: without it, adding a field means touching every intermediate
processor's schema and code purely to copy something it doesn't care about — coupling that worsens with
chain depth.

`enrich` vs `transform` survives as a distinction, but only about whether the output is *typed as* the
same fact family. Unknown fields survive either way.

*Implementation note:* Avro reader/writer resolution is **lossy by design** — unknown fields are dropped
during resolution. Pass-through therefore can't be built on the mapped type; the framework must retain
the raw record alongside it and merge at the wire level. Constraint on Q1.

### Precedent and its known failure mode

This is distributed-tracing **baggage** — propagating key-value context along a causal chain. Same
mechanism, same motivation, and the same documented problem: unbounded growth, because every hop adds
and nothing removes. A ten-hop chain carries the exchange-rate lookup detail long after anyone needs it.

So a scoping mechanism will eventually be wanted — fields marked *chain-scoped* (propagate) versus
*step-scoped* (drop after the hop that consumed them). Not building it now, but the growth is structural
rather than incidental, and the natural pruning point is chain termination.

### Three parts, not two

A wire record has three distinct sections, and conflating the first two causes trouble:

| | Owner | Content | Size |
|---|---|---|---|
| **Fact metadata** | Framework | Occurrence id, causation, position, timestamp, provenance, schema fingerprint | Bounded, fixed |
| **Invocation context** | Developer (opaque `CTX`) | Whatever *who is invoking, and under what circumstances* means to them — typically actor, tenant, invocation and action ids, locale, toggles | Bounded, theirs |
| **Domain payload** | Developer | The fact itself, with pass-through of unknown fields | Open-ended, grows with the chain |

These are orthogonal, not layered. **One invocation can submit several commands; one command can be
retried under different invocations.** swissknife's `correlation/*` models the second — but as a
*candidate* for the starter's stock context, never as something core mandates.

**Causation is mandatory in the fact metadata** — it's what makes "go read the source fact" a real
option when someone needs it. Only the third section is subject to the baggage-growth problem above.

### Three levels of identity

| | Scope | Generated by | Shared across | Lives in |
|---|---|---|---|---|
| **Action id** | One user intent | Client | Every invocation that intent spawns | Invocation context |
| **Invocation id** | One request | Client | Every fact derived from that request | Invocation context |
| **Occurrence id** | One record in the log | Framework | Nothing — unique per fact | Fact metadata |

**The idempotency key is the originating action id, namespaced by tenant/customer.** Not the occurrence
id, and not the invocation id.

- *Not the occurrence id* — one invocation produces several facts (`CommandReceived`, then the result
  event), which cannot share an occurrence id. Lattice collapses these
  (`idempotencyKey: Id get() = id`); that's wrong under this model.
- *Not the invocation id* — that only gives **transport-level** idempotency, catching a literal
  in-memory request retry. The action id gives **business-level** idempotency and survives more: client
  restart with a persisted pending operation, a fresh request id for the same intent, a retry issued by
  different code than the original. "The user meant this once" is the property worth enforcing.
- **Namespaced by tenant/customer** because action ids are client-generated. Without a namespace, a
  collision lets one tenant's action suppress another tenant's command — a suppression attack, not just
  a correctness bug.

It must be **client-supplied**, not framework-generated: a framework-generated id would differ on every
retry, which is exactly the case dedup exists to catch. Client ownership is load-bearing.

### Two capabilities from CTX, not one

The dedup key and the reply-correlation key are **different values**. One button press producing a query
and a command shares an action id but needs two distinct replies, so keying replies by action id would
collide.

| Purpose | Value | Needed as |
|---|---|---|
| Dedup — *this intent, once* | Namespaced action id | idempotency key, derived from context **and** fact |
| Reply correlation — *this request's answer* | Invocation id | invocation id, derived from context |

**How these are obtained is the same open fork as A-vs-B above**, applied to CTX rather than to facts —
`interface Context<out ID : Any> { val invocationId: ID }` versus `invocationIdOf: (CTX) -> Id`.
Undecided; try both when building.

Two pieces of input for that decision:

- **It may resolve differently here than for facts.** There are dozens of fact types, all domain model,
  each paying for a mandated supertype. There is typically *one* context type per application, it's
  infrastructural rather than domain, and the starter ships a stock one anyway. The interface is much
  cheaper on context than on events.
- **The dedup key doesn't fit the interface shape either way.** It spans context *and* fact, so it sits
  naturally on neither object and stays a supplied function or a separate strategy object regardless.
  Some mixture is likely however the fork resolves.

The dedup key needs the fact as well as the context, because **one action can produce several
commands** — a checkout button issuing `ReserveStock` and `ChargeCard` shares an action id, and keying
on that alone would silently drop the second. Access to the fact lets the developer add a discriminator,
and returning an already-composed, already-namespaced key means **the framework never needs to know what
a tenant is**.

Which refines the opacity rule: the framework **never inspects CTX structurally, but obtains values via
declared capabilities** — whether those arrive as interface members or supplied functions. Same pattern,
and same open fork, as the domain payload.

**A dedup hit returns the original outcome, not an error** — an idempotent retry is entitled to the
result of the first execution. So the outcome store needs an index by idempotency key, not only by
invocation id; otherwise a retry arriving with a fresh invocation id has no way to find its answer.

The action id also illustrates why CTX must stay open-ended: genuinely useful for correlating one user
action across several requests in audit and tracing, and something the framework would never have
invented on its own.

### The PII objection, and why it dissolves

Pass-through looks like it makes the erasure surface unbounded: add a personal-data field upstream and it
silently spreads into every derived event across topics with different retention. Two things kill that:

- **You don't need to understand a field to know it's sensitive.** Classification lives in schema
  metadata, so an intermediate processor with no idea what `beneficiaryTaxId` *means* still reads that
  it's restricted. Comprehension and classification travel separately.
- **Crypto-shredding makes duplication irrelevant to erasure.** Encrypt PII per subject at the producer;
  pass-through copies ciphertext; deleting the subject's key makes all copies unreadable at once, and you
  never had to find them.

Pass-through *needs* crypto-shredding to be safe, and crypto-shredding was already required by the query
audit thread. Same lever, second payoff.

### Storage duplication: measure, don't design around it

A chain of depth N stores the payload roughly N times. Repeated content within a topic compresses very
well, and tiered storage makes the cold tail cheap. Noise at moderate volume; real money at 10k/sec with
multi-KB payloads and infinite retention. Scale-dependent, therefore a knob to reach for after a
measurement — not a global design decision.

### Rejected: lookback to the predecessor in the same partition

Idea: reference the predecessor by message ID and read it back from the same partition, assuming
locality. Rejected on four counts, one fatal:

- **It fails where it's needed.** Same-key hops have the predecessor nearby — but those are the hops
  where entity state could plausibly have helped. Cross-key hops land in a different partition entirely,
  and those are the ones that actually need the data.
- **"Near" isn't bounded.** The gap is `chain latency × partition throughput`. A hop waiting three
  seconds on a gateway, on a partition doing 1k/sec, is thousands of events back.
- **Fatal: rebuild becomes random I/O.** Duplication costs once at write time. Lookback costs on every
  read forever, including every rebuild-from-zero (D9) — where the segments are in tiered storage, so
  each seek is an object-store GET. A sequential scan degrades into N cold random reads. Sequential scan
  is the one thing a log is unambiguously good at.
- **Multi-hop reads are serial and dependent** — you can't parallelise, because the next ID isn't known
  until the previous read returns. And the obvious fix, caching events by ID per processor, rebuilds the
  indexed event store being avoided.

Plus a hidden coupling: any topic something else references can no longer have its retention shortened
independently.

### Claim-check: available, deliberately demoted (D21)

If a payload genuinely can't be inlined, the mechanism is **content-addressed claim-check**, not
lookback — `hash → bytes` against an immutable blob is a fundamentally friendlier operation than a seek
into an ordered log, and immutability makes it perfectly cacheable.

It also delivers the "stored deduplicated, presented as whole" property brokers don't: every hop copies
the hash, so a 5MB blob crossing six hops is stored once. Just at the application layer.

Design, if used:

- **Field-level, not wholesale** — a projection reading `filename` shouldn't fetch 5MB.
- **Producer writes the blob before publishing the event**, or consumers race a reference to nothing.
  Orphan blobs on failure are harmless: content-addressed, dedupe on retry, cheap enough to never collect.
- **Lazy, suspending access** — pay only if touched.
- **Aggregates cannot dereference.** They're pure and non-suspending, so anything behind a claim-check
  is invisible to them. Correct on its own merits and load-bearing for D20.
- **One node-level content-addressed cache**, not a separate per-event memo — same code, plus cross-event
  and cross-retry hits. Must be **single-flight** (memoize the `Deferred`) and **bounded in bytes, not
  entries**: 100 concurrent events × 5MB pinned is 500MB of heap.
- **Never GC.** Infinite event retention means the reference count never reaches zero. Object-store
  lifecycle tiering for cost; crypto-shredding for erasure.

**Why demoted.** Inline keeps liveness a function of the broker alone (D20). Claim-check trades
availability for storage, and that's the wrong direction by default: storage is cheap, elastic, and
improving; availability is scarce and doesn't improve on its own.

**No broker offers this natively**, and the near-misses mislead. *Tiered storage* moves segments to object
storage — a retention-cost feature; consumers still receive every byte. *Pulsar chunking* works around
`maxMessageSize`; all the bytes still travel. A *compacted topic* gives latest-value-per-key but no
random access, so every consumer would need a full local replica.

If it's used, **NATS JetStream Object Store** is already in the stack (chunked, SHA-256 digests) and
avoids adding a dependency, up to the point where volume argues for object storage.

---

## The availability principle (D20)

> Nothing on the log's critical path may depend on any system other than the broker.

The asymmetry that produced this rule: **NATS down degrades delivery; a blob store down halts
derivation.** One means callers don't hear about outcomes that did happen; the other means the log stops
advancing. And because of head-of-line blocking, a stuck event holds the partition — so keys with *no*
lazy data stop being processed because they share a partition with one that does.

Two consequences beyond claim-check:

- **Aggregates must not write to NATS.** An aggregate publishing its outcome directly to NATS turns a
  NATS outage into a failed write → retry → backoff → halted partition. Instead the aggregate writes the
  outcome **as an event to its own log**, and a **separate relay** ships outcomes from the log to NATS.
  Delivery degrades; derivation continues.
- **Reactors must consume independently.** They make external calls, therefore they stall. Their stalls
  must stay theirs.

Mitigations if a remote dependency is unavoidable: **prefetch on consume rather than on access** (turns a
synchronous dependency into a pipelined one, with read-ahead runway) and a **durable local cache** (live
path mostly independent; only rebuild genuinely exposed). Neither removes the dependency — they shrink
the window from instant halt to minutes of runway.

**Deferred:** per-key parking, the general fix for head-of-line blocking, so one stuck key doesn't block
others on its partition. Awkward because commit offsets are watermarks — advancing past a pending record
needs a persistent pending set. Worth knowing it exists; not worth building before a second reason.

---

## Failure handling

### Taxonomy (D22)

| Failure | Behaviour |
|---|---|
| Deserialization / schema | Halt immediately, alert, needs a human |
| Handler failed, **retryable** (IO, timeout, 503) | Backoff retry, then halt + alert after a bound |
| Handler failed, **non-retryable** (validation, bug) | Halt immediately — retrying a bug is a hot loop |

Distinguishing the last two is the handler's job, not the framework's guess. Infinite reconsume is also
a halt, just a spinning one without an alert.

**"Halt the system" is really "halt the partition"** — but every aggregate instance hashed to that
partition is blocked, so the blast radius is wider than the affected entity.

**No automatic DLQ.** Dead-lettering means skipping a record and continuing, which breaks ordering and
leaves the aggregate deriving state from an incomplete stream. For a system of record that's strictly
worse than stopping.

### The common case is a broken reader, not a corrupt record

Corruption is rare. The frequent failure is **someone shipped an incompatible schema change**, and the
fix is to patch the consuming code and restart — no skip, no gap, no permanent damage. The two look
identical from outside, and reaching for skip when the reader is at fault silently corrupts derivation.
Skip must feel heavyweight.

Three things follow:

**Gate it upstream (D23).** Compatibility must fail the *producer's CI build*, not just its deploy. And
the level matters: most teams run `BACKWARD`, which is fine at seven-day retention. With infinite
retention and rebuild-from-zero, ten `BACKWARD`-compatible steps can drift until v10 code can't read v1
records — discovered during a rebuild eighteen months later. **`FULL_TRANSITIVE` is required**, and it's
restrictive precisely because it blocks the changes that cause this.

**The blast radius is wide.** A schema break halts every partition carrying that event type more or less
at once — effectively a full consumer outage until patched. Correct behaviour, but it means the frequent
failure is also the high-impact one, which argues for a non-bypassable gate.

**Make the diagnostic do the work.** The gap between a ten-minute fix and a two-hour investigation is
entirely the error message. Report topic/partition/offset, writer fingerprint *and resolved schema*,
reader schema, and **the specific incompatibility** — "field `amount` is required by the writer and
absent from the reader", not "deserialization failed". Avro's `SchemaCompatibility` produces this; it
just has to be surfaced.

**And the fix should usually be an upcaster (D24).** Branching on shape inside a handler puts
version-handling in domain code where it accumulates forever and nobody dares delete it. A registered
`v3 → v4` upcaster keeps handlers reading one shape, and the chain is inspectable, testable, and
removable when old records age out. The diagnostic should suggest it. This is why upcasting is core
rather than phase-8 hardening.

### Skip registry

Skip ≠ delete. **No record is ever removed** — which means the corrupted record is encountered again on
every future replay, so the skip decision must be consultable *before* the consumer reaches it.

An in-band marker can't do that:

```
offset 100  ← corrupted
offset 101  ← SkipDecision(100)
```

Replay from zero halts at 100 and never reaches 101. The remedy always arrives after the problem.

So: **a separate compacted topic (or KV), keyed by `(topic, partition, offset, consumer | global)`, read
at startup and consulted as the consumer advances.** Global for deserialization failures; per-consumer
for handler failures.

The justification is **rebuild-from-zero** (D9), not steady state — in steady state the cursor is already
past it. A projection rebuilt six months later starts at offset 0 and walks into the same poison record.
With a registry, replay is deterministic and repeatable forever.

**"Fixed" isn't an alternative to skipping.** Logs are immutable, so repair means appending a superseding
record: *fixed = skipped + superseded*. The registry entry carries a pointer to the replacement. Worth
being explicit that the correction lands at a later offset, so an order-sensitive aggregate sees it in a
different position than the original would have occupied — unavoidable, but it should be recorded rather
than discovered during an investigation.

**Mechanism: an admin command, not cursor bumping.** Manually moving a cursor is unrecorded, invisible to
future replays, and easy to overshoot.

```
skip --topic account-events --partition 7 --offset 100 --reason "..." --operator ...
```

Elevated permissions, so it belongs with the admin/provisioner artifact. **And surface it** — active
skips per topic in metrics, plus a startup line. "This derivation has three known gaps" should be visible
on every boot.

---

## Queries, audit, and why queries are journaled

### Rejected: "a query isn't a fact"

A definitional assertion doing the work of an argument. `Query` is a `Fact` already. The real questions
were separable: is it routed through the ordering machinery, and is it durably journaled? Both yes.

### Why journal: the comparison is journal-vs-log

You need an access record anyway. The alternative is logging, which is a poor audit substrate:
best-effort delivery that drops under backpressure, silently drifting schema, frequently **sampled**
(disqualifying for compliance), separate retention and access control, not replayable, no ordering,
rarely tamper-evident. Journaling moves an existing cost onto a better substrate.

### Payoff: one stream, many consumers

request logging · access audit (longer retention) · rate limiting and abuse detection · usage metering
and billing · query analytics · cache warming · replaying production read traffic against a new version

Constrains the schema: a query event carries actor, tenant, trace, timestamp, applied position, outcome
status, and latency.

### Separate topics, co-partitioned

| Fixed | |
|---|---|
| Retention conflict | Per-topic retention. Queries weeks, events forever. |
| Replay filtering | The aggregate reads its event topic and *never sees* a query. No skip logic to get wrong. |
| Rebuild cost | No longer streams past 99% queries to find the events. |
| Partition contention | Largely. |
| Queueing latency | Queries no longer sit behind commands. |

Costs and consequences:

- **Sequentiality with commands is lost**, making the read-your-own-writes position token load-bearing
  rather than incidental. Explicit beats accidental.
- **Co-partitioning is a correctness requirement.** Same key, same partition count as the event topic, or
  queries land on nodes that don't own the aggregate. The framework enforces it.
- **Contention moves into the actor's scheduler.** A query flood can still starve commands; the win is
  that the policy becomes a declared knob rather than whatever topic interleaving gave you.

### Audit durability

Fire-and-forget is **at-most-once** — a crash loses the in-flight producer batch, thousands of records
with batching. "Off the critical path" and "at-least-once" are incompatible as stated.

**The fix is overlap, not weakening.** Computing the answer and durably recording the query are
independent:

```
     ├── compute answer ────────────┐
t0 ──┤                              ├── respond
     └── publish audit → ack ───────┘
```

`max(compute, publish)` instead of the sum. A batched 1–3ms ack disappears under any compute that touches
a store. **And waiting for the ack before releasing the response means no disclosure without a durable
record** — so the strict behaviour is the default, and no `syncAudit` knob is needed.

Where overlap doesn't help — microsecond reads from actor memory — two declared options: a local durable
buffer with a crash-safe shipper (*this is the transactional outbox the recap doc rejected; a different
trade, but a conscious carve-out, not a quiet reintroduction*), or explicitly accepting at-most-once.

Consequences: **duplicates** (dedupe on `idempotencyKey`), and **gap detection** — per-producer monotonic
sequence numbers with alerting. "We record every read" is unverifiable otherwise, and it's what an
auditor will ask for.

### Recording the answer

| Content | Known when |
|---|---|
| Who, what was asked, tenant, actor, trace, timestamp | At arrival |
| **Applied position** | **At start of processing** |
| Answer digest, status, latency | After compute |

The actor knows its applied position *before* it computes, so the intent record can state which version
of the world is about to be read without waiting for the read. That makes it self-sufficient: query +
position **reconstructs** the answer by replaying to P (D14). Also keeps the erasure surface smaller,
since answer payloads are the PII-dense part.

**Two records, mirroring commands:** `QueryReceived` (durable, pre-disclosure, overlapped, carries the
position) and `QueryAnswered` (status, latency, size — may be at-most-once, since losing it costs
precision, not evidence).

Failure mode is correct by construction: crash between them and the auditor sees "X asked for Y at
position P, outcome unknown" — conservatively read as *assume disclosed*.

**When the answer must be recorded:** non-deterministic queries, regimes requiring disclosed content
rather than access, or replay-to-position being impractical as an audit lookup. Then it's serial.
Declared per query type.

### Erasure

Auditing reads creates a right-to-erasure obligation on the audit stream itself. Crypto-shredding via
swissknife's `protected-value/*` and `cryptography/*`. Must be designed in from the start — retrofitting
erasure into a live journal is expensive.

### Cross-aggregate queries

No single routing key, so nothing to partition by. Read models, as always.

---

## HTTP surface

```
POST /commands/<name>/v<major>
POST /queries/<name>/v<major>
POST /events/<name>/v<major>
```

Routes and OpenAPI derived at runtime from registrations (D2) — swissknife has `openapi/builder`,
`openapi/provider`, `openapi/validation/http4k`. Majors coexist; older ones carry a sunset header.

| Tier | Question | Where | Journaled? |
|---|---|---|---|
| Structural | Parses against the schema? | Endpoint | No — 400 |
| Permissions / integrity | Caller allowed, for this key? Self-consistent? | Edge (JWT + routing key) | No — 403/422 |
| Invariant | Does current aggregate state permit it? | Aggregate | Yes |

Events skip tier 3 (D7) and carry **provenance** — "we observed this" versus "an external party asserted
this".

---

## Request-reply

**sync** (wait inline), **poll** (fetch later), **push** (subscribe) — one mechanism, three styles. Mode
is a per-request client preference (`Prefer: respond-async`), not a separate endpoint.

> **Race-free invariant (D8):** every submission produces a durable outcome record, addressable by
> correlation id, readable for a bounded TTL. No delivery mode can lose it.

1. **Subscribe-before-publish** for sync — removes the race when the waiter is the submitter.
2. **Durable outcome store** as universal backstop. Late or reconnecting subscribers *subscribe, then
   read the store*.

Per D20 the aggregate writes the outcome **to its log**; a relay ships it to NATS.

**A sync timeout is never a `504`** — the command is still in flight. Return `202` + correlation id +
`Location`. "I stopped waiting" and "it failed" are different facts.

The outcome carries the **log position** of the result event.

---

## Ports and adapters

Every port gets a **test specification**; every adapter must pass it. Existing workspace pattern
(`swissknife/**/test/specification`), and what makes BYO adapter verifiable rather than aspirational.

### Broker port — four operations

1. Append a fact to a partitioned log under a partition key
2. Consume one partition sequentially, at-least-once, manual ack
3. Store and resume from a position
4. Durable retention

**Out of the port, or Kafka support becomes fake:** delayed delivery (build a timer service above),
per-message TTL (framework-level expiry), broker-side dedup (framework concern), `Key_Shared` (the port
says *partition*; both brokers give per-partition ordering, which is all that's needed).

### Chosen backends (D25)

- **Pulsar — primary.** Better fit for infinite retention: BookKeeper separates storage from compute, so
  segments offload cleanly and storage grows without rebalancing partitions across brokers. Kafka ties a
  partition to a broker's disk. Multi-tenancy (tenant/namespace) also matches the provisioning model
  better than Kafka's flat namespace plus prefix conventions.
- **Kafka-API — second.** One adapter covers Kafka, Redpanda, WarpStream, AutoMQ. **Redpanda for
  local/dev** — single binary, no JVM, no ZooKeeper, far lighter to run.

Two genuinely different protocols, so the port is actually proven. *(Redpanda alone wouldn't count:
same API as Kafka means one adapter, not two.)*

**NATS JetStream** stays for request-reply, outcome KV, and Object Store — not as the journal. No tiered
storage, so infinite retention means very large disks forever, and partition assignment is manual.

**Considered and not chosen:** *KurrentDB/EventStoreDB* — purpose-built for event sourcing, with
stream-per-aggregate-instance native (millions of streams is normal, because streams are cheap in a
database in a way topics aren't in a broker) and direct indexed reads of one aggregate's history. Would
dissolve the whole lookback problem. But partition-as-actor-ownership disappears, so D17's free ride ends
and you'd build sharding and membership yourself; throughput ceilings are also much lower. Worth an
afternoon before committing, so it's rejected deliberately rather than by default. *WarpStream / AutoMQ* —
S3-only, so hundreds of ms write latency; wrong for synchronous request-reply. *Chronicle Queue* —
single-node. *Fluvio* — too immature.

*Verify current licensing before committing — Redpanda's tiered storage and Kurrent's terms have both
shifted, and both are source-available rather than Apache 2.0. Matters more for a published framework
than a private one.*

---

## Durable storage: the broker, the store, and the staging between them

Three arrangements were considered. They differ only in where permanence lives and what the write path
is.

| | Write path | Broker retention | Truth |
|---|---|---|---|
| **A — broker is truth** *(chosen)* | aggregate → broker | Infinite, tiered | Broker |
| **B — store-first** *(rejected)* | aggregate → store → relay → broker | Short | Store |
| **C — broker as WAL** *(future)* | aggregate → broker → writer → store | Days | Store (permanence) |

### B is rejected

The store sits on the critical path: cascades wait on a store append before anything propagates. That
violates D20, roughly doubles per-hop latency, and requires the transactional outbox to avoid a
dual-write atomicity hole. C is strictly better on all three.

### A is chosen (D26)

The property that makes a derived store safe: **it always has a broker-only fallback.** Down, empty, or
corrupt, you replay the partition — slower but correct. It degrades rather than halts, which is what
makes it D20-compliant. Store-as-truth has no such fallback.

Also: no dual write, no atomicity problem, no relay on the critical path, rebuild whenever you like.

### C is a legitimate future, not a fork

C is not "the broker plus a second write". It's still one write to the broker, followed by asynchronous
derivation into a permanent archive; the *designation* of permanence moves, not the mechanics. The
broker becomes a write-ahead log in front of the archive.

| | A | C |
|---|---|---|
| Critical stateful systems | One (+ disposable cache) | Two |
| Indexed per-aggregate reads | No | Native |
| SQL over the truth | No | Yes |
| Backup / DR | Mirror a large cluster | `pg_dump` / PITR — solved |
| Rebuild-from-zero | Re-scan cold tiered segments | Indexed scan |
| Store lag | Harmless — it's a cache | **Hard deadline: lag < retention or data is lost** |
| Store corrupted | Rebuild from broker | Restore from backup, or it's gone |

**Objections to C that did not survive scrutiny**, recorded so they aren't re-raised:

- *"The database is the write throughput ceiling."* Not really. Batched ingestion from a log is close to
  the best case: ~50–100k/sec with 1k-row transactions, 100k–1M/sec with `COPY`. Append-only avoids the
  dead-tuple bloat that normally caps Postgres writes, and monotonic keys keep B-tree inserts on the
  right-hand side. Batch latency is free here since the store isn't the live read path. The writer stays
  idempotent with a unique constraint on `(partition, offset)` and `ON CONFLICT DO NOTHING`.
- *"Two projection code paths — live from broker, rebuild from store."* Dissolved: **the store can
  implement the broker port's read side** (operation 2 is already "consume one partition sequentially
  from a position"). Record `(partition, offset)` per row and index it, and projections consume through
  the port without knowing which side of the retention boundary they're on. One code path, two adapters,
  covered by the existing conformance suite.
- *"Sharding reintroduces partition assignment."* No — **the shard key is already decided by the
  broker.** `shard = f(partition) = f(hash(streamId))` is a pure function: no directory, no metadata
  service, no rebalance protocol. The writer already knows its partition, so it knows its shard and can
  co-locate with it. No cross-shard transactions, since an event belongs to exactly one stream.
  Resharding is *partition reassignment*, not rehashing — 256 partitions across 4 shards → 16 shards
  moves whole partitions with no keys rehashed.
- *"Cross-shard reads lose global ordering."* There is no global order in A either — brokers give
  per-partition ordering only, so projections already merge N streams. Same shape.

**The objection that stands:** store-writer lag exceeding broker retention is **permanent data loss** — a
failure category A does not have. Monitorable (the realistic risk is one partition silently stalling
while per-writer alerting looks healthy, not a month-long outage), but real.

**So A is chosen because it has fewer moving parts, not because C is wrong.** The write path is
identical, so this is a staging decision. Migration is: stand up the archive consumer, let it catch up,
verify, *then* shorten broker retention — one new consumer plus an ops change, no change to how
aggregates or handlers are written.

Triggers to revisit: infinite-retention storage cost becoming material · a product requirement for
indexed per-aggregate history at interactive latency · broker backup/DR becoming a genuine operational
problem.

Smaller coupling to remember if C happens: log positions age out. Outcomes, RYOW tokens, and snapshot
offsets are all broker positions, so once retention passes them they're unresolvable from the broker and
catch-up must route via the store, which needs a position mapping.

### Store technology: two workloads, not one

The question "time-series DB or Postgres" conflates two access patterns that want opposite storage:

| Workload | Access pattern | Wants |
|---|---|---|
| **Hydration / archive** | Point lookup by stream id, small result — *high cardinality* | Row store, B-tree |
| **Audit / analytics** | Time-ranged scans and aggregates over huge append-only volume | Column store |

TSDBs and columnar engines optimise for the second. Event sourcing's *primary* read is the first.

- **TimescaleDB — recommended over raw Postgres.** It *is* Postgres, so unique constraints, `ON
  CONFLICT`, transactions, and B-tree point lookups all survive. It supplies the declarative
  time-partitioning that infinite retention makes mandatory anyway, plus columnar compression on older
  chunks (typically 10–20x), which directly attacks the retention storage cost. Strictly better here at
  essentially no cost. *Verify the licence split — compression and continuous aggregates have
  historically been under the Timescale License rather than Apache 2.0.*
- **ClickHouse — right tool, different job.** Millions of inserts/sec and excellent scans, but weak on
  high-cardinality point lookups (sparse granule index), and **no unique constraints**, so the
  `ON CONFLICT` idempotency trick doesn't exist — `ReplacingMergeTree` dedupes eventually, not on
  insert, which is a poor match for at-least-once ingestion. Its natural home in this architecture is the
  **query/audit stream** (D11–D13), which is exactly time-ranged aggregate reads over high-volume
  append-only data — rate limiting, metering, analytics, access audit. That's a separate projection off
  the broker, so it's additive rather than a fork.
- **Skip:** InfluxDB (tag-cardinality explosion with millions of stream ids), Druid/Pinot (aggregate
  serving, not point lookups), QuestDB (immature ecosystem for this role).

Insert throughput isn't the binding constraint — 100k/sec is ~8.6 billion events/day, far past anything
this will see. Correctness properties are, which is why the row store wins for the archive.

This is the recap doc's "many views" table being right: *Aggregate Recovery Store* and *OLAP / Audit
Store* were always separate rows.

---

## Read side

Full lifecycle (D9): declare a projection (source events, initial state, apply, storage) · durable
position tracking · rebuild from zero while the old version keeps serving · versioned projections with
atomic cutover · lag and backpressure observability · **read-your-own-writes** via the position token ·
escape hatches (raw store access, custom apply, opt out of framework storage).

---

## Deployment and topology

Assumes a Pulsar or Kafka-API cluster already exists.

### Separate artifact, same codebase

Precedent in the workspace: `element-service-example` ships a separate image,
`element-service-example-postgres-migrator`, as a sibling module with its own Jib config and starter —
Liquibase runs with DDL credentials, the service with DML-only. Mirror that; don't invent a mode flag.

- **Separate identity and lifecycle** — provisioning needs Pulsar admin / Kafka `AdminClient` with
  cluster-level CREATE and ALTER. An HTTP-facing node must not hold credentials that can delete the
  system of record.
- **Same codebase** — topology is derived from registrations (D15). A separate description would drift.

The framework ships the provisioner machinery; an application adds a ~10-line module depending on its
registrations plus the framework starter.

This rules out runtime auto-creation: wrong defaults, racy creation, permanent admin rights everywhere.

### What `provision` does

Create per aggregate type an event topic and a **co-partitioned** query topic · create audit, outcome,
and skip-registry topics · set partition counts and retention classes · namespaces/tenants (Pulsar) or
prefix conventions (Kafka) · register schemas and set `FULL_TRANSITIVE` compatibility (D23) · grant the
runtime identity produce/consume on exactly those topics.

**`plan` before `apply`, and repartitioning is never automatic.** Increasing partition count rehashes
keys: ordering breaks for every existing key, and instances land on partitions that don't hold their
history. Print the diff; refuse to repartition silently. A data-integrity incident, not an outage.

### Runtime startup, per node

1. load registrations → compute expected topology
2. **verify** it exists and matches → fail fast
3. join the subscription / consumer group
4. receive partition assignments
5. hydrate owned actors (snapshot + replay delta)
6. report ready

### Partition assignment is the broker's job (D17)

Kafka consumer groups and Pulsar `Failover` subscriptions both guarantee exactly-one-active-consumer per
partition, and **partition ownership is actor ownership**. Akka Cluster and friends exist because they
have no partitioned log underneath.

**Rebalancing is the hard part.** Actors are evacuated and rehydrated; hydration is snapshot plus replay
delta, so **snapshot cadence determines rebalance pain** — which makes snapshots a prerequisite for
multi-node, not an optimisation. Prefer incremental cooperative rebalancing. Commands for migrating keys
queue during hydration, which the `202` + correlation-id design already handles.

### API tier and actor tier are separable

An endpoint publishes and waits; it needn't own the key. **Collocated** by default; **split** available
later. Consequence: two readiness signals — HTTP-ready quickly, actor-ready after hydration. Nothing
external routes on actor-ready, but the *deploy controller* gates on it or a rolling deploy thrashes.
swissknife's `readiness/domain` and `service/readiness/http4k` model this.

### Why aggregate types are static (D16)

Runtime types would need runtime topic creation, which needs permanent admin credentials on HTTP-facing
nodes. Explicit constraint rather than a discovered one.

---

## Prior art: lattice

A stubbed exploration — one passing test, most of the surface `TODO`. Not a reference implementation,
and its state isn't evidence of anything. Useful only as a source of ideas.

**Ideas worth taking:**

- **Module topology** — `sdk/api`, `sdk/test/specification`, `sdk/in-memory/tests`, `framework/api`,
  `framework/implementation/*`, `framework/connector/embedded`. Already the generic-facade /
  concrete-engine shape, with a connector seam for a future polyglot path, and careful wiring
  (`implementation(...)` keeps the engine off the consumer's classpath).
- **Test-specification-as-interface** — an interface of `@Test` methods with one abstract factory, so a
  new implementation is four lines. The right conformance mechanism for every port here.
- **The `Bindings` DSL shape** — `bindings { command<Withdraw> { it.accountId } }`. Routing declared at
  registration, domain type untouched. Ancestor of Approach B; generalise from routing keys to ids and
  codecs.
- `Decision` (Accept / AcceptWithResponse / Reject) · the three-way outcome split · outcomes not
  carrying the domain event.

**Ideas to avoid**, since they're the inverse of what's decided here: `Fact { val id: Id }` mandates both
a framework supertype and swissknife's `Id` on domain data; `Lattice` has no type parameters, so
`eventHistory(): Flow<Event>` loses exhaustive `when`; `Aggregate<COMMAND : Command, EVENT, STATE>`
bounds the parameter that shouldn't be and leaves unbounded the one the engine needs; dispatch is a
reflective linear scan per message with silent first-match-wins.

---

## The first test

### The governing property

**It must be impossible to pass by accident.** That is the trap the old lattice fell into: a synchronous
in-memory engine made its one test green while proving nothing about ordering, positions, or replay.
D31 exists because of it. Every choice below follows from this.

### Shape

```kotlin
val receipt = lattice.submit(deposit(accountId, 100))   // Accepted(commandId, P1) | Rejected(reason)
val accepted = receipt.acceptedOrThrow()
val event    = accepted.awaitReaction<DepositProcessed>()          // owner's verdict, position P2
val answer   = lattice.query(GetBalance(accountId), atLeast = P2)  // Answered(100, at = P3), P3 >= P2
```

### Deposit, not Withdraw

Deposit has no precondition, so **test 1 has no rejection path at all**. Withdraw-against-insufficient-
balance becomes test 2, where rejection is the point rather than an incidental branch.

### Three registrations, and the query must not go to the aggregate

- **Aggregate** — `handle(state, Deposit) → DepositProcessed`, `apply(state, event) → state`. Owns the
  invariant.
- **Read model** — `apply(state, event) → state`, `answer(state, GetBalance) → Long`.

The query goes to the **read model**. If it went to the aggregate the test would not exercise CQRS at
all, and the write/read split is the thing under test. Both consume the same event, for different
reasons — so the event-handler registration is doing real work rather than being ceremony.

### Asserting history is the most valuable assertion

It makes D4 *observable* rather than assumed:

```
[ CommandReceived(Deposit(100))     @ P1,
  DepositProcessed(100, 100)        @ P2 ]

P1 == receipt.position
P2 == verdict.position
P2 >  P1
```

Every position claim is checkable and nothing rests on timing.

### How we know propagation happened

`atLeast(P2)` on the query — the effect position from the verdict, never the acceptance position (see
the trap above). Deterministic; no sleeps, no polling, and the mechanism under test is the mechanism
being used.

**`atLeast` should be required but explicitly waivable** — `atLeast = Position.Unconstrained` rather
than a defaulted parameter. You cannot *forget* it, you can only *decide*. The failure mode of
forgetting is a test that is green nine times in ten, or a UI that silently goes backwards in
production.

### Make the in-memory implementation asynchronous on purpose

If the read model applies events synchronously inside `submit`, `atLeast` is a no-op and the test stays
green with the entire RYOW mechanism deleted. So the in-memory read model runs on **its own coroutine**
and genuinely lags.

### Test 1b — the adversarial companion

Everything above still passes if `atLeast` is a no-op and the projection merely happens to be fast. The
fix is a **test-controllable scheduler**:

1. hold the projection at P1
2. submit the query with `atLeast = P2` — assert it has **not** completed
3. release the projection to P2
4. assert it completes with the right answer

That proves RYOW rather than observing it, and the same lever serves rebalance, lag, and replay tests
later. Written as 1b rather than folded into 1, so test 1 stays a readable end-to-end example.

### Deliberately out of scope

No HTTP (same process), no broker, no audit stream, no dedup, no cascade, no snapshots, no action or
invocation ids. One aggregate, one read model, one partition per key.

Dropping action/invocation ids means the **receipt itself is the correlation handle** — which works
in-process and defers exactly one thing: the receipt is not restart-durable, so a client that dies
cannot reattach. When CTX arrives the invocation id becomes the durable key and the receipt becomes a
thin wrapper over it. The API shape must not assume in-process.

### Still open for test 1

- **Does `query` block or fail when the floor cannot be met within a timeout?** Blocking is friendlier;
  `Stale(currentPosition)` is more honest and lets the caller decide. Probably a per-query policy
  eventually — for test 1, blocking with a generous timeout is the least surface.
- **Naming for the outcome level.** `Receipt.Accepted` (edge) and `Verdict.Accepted` (owner) both use
  "accepted", and `Decision.Accept` is a third. Three levels need three distinguishable words.

---

## Open — needs a decision

**Q1 — serialization. Still the blocker.**

| Option | Source of truth | Cost | Multi-language |
|---|---|---|---|
| (a) `.avsc` resources + hand-written `GenericRecord` serde (swissknife today) | Schema | Boilerplate per fact type | Authors *and* consumers |
| (b) kotlinx.serialization + Avro derivation | Kotlin type | Compiler plugin, no source emitted | Consumers only |
| (c) `.avsc` → generated Kotlin classes | Schema | Generated source to maintain | Authors and consumers |

(c) is out. Given "not fussed about multi-language" plus the typesafety goal, **(b)**. Note the
pass-through constraint above: whichever is chosen must allow retaining the raw record alongside the
mapped type.

**Q2** — Idempotency. Key definition is settled (namespaced action id, via `idempotencyKeyOf`); the
mechanism isn't. The developer supplies a **pure key extractor**; the framework owns the store and does
the check/record. Open:

- Where the dedup store lives, and its retention (an action id must stay deduped for at least as long as
  a client might retry — hours? days?).
- **For reactors with side effects**: is the record written before or after the call? Before risks a
  false "already done" after a crash; after risks a duplicate call. No free lunch — probably a declared
  per-reactor choice between at-most-once and at-least-once semantics.
- Interaction with the outcome store, which must be indexed by idempotency key so a dedup hit can return
  the original outcome.

**Q4** — How much invocation context (tenant, actor, trace) is framework-owned vs application-owned?
swissknife's `correlation/*` models it and it must flow HTTP → log → aggregate → projection.

**Q5** — Actor scheduling policy between the command/event stream and the query stream. Fair-share, or
command-priority?

**Q6** — Snapshots (the derived recovery store, D27). Can't slip past phase 5 — rebalance pain is a
direct function of it. Shape mostly settled; the open parts are cadence and format:

- **Shared storage, keyed by aggregate id** — node-local defeats the purpose, since the point is that a
  *different* node can hydrate fast after a rebalance. NATS KV for small states, object storage for
  large.
- **Snapshot on eviction, plus periodically.** Snapshotting an actor as it's evacuated makes *planned*
  rebalances nearly free; periodic snapshots bound unplanned (crash) hydration time. Cadence is open.
- **A snapshot must record the offset it represents**, and replay resumes exactly there. Off-by-one is a
  duplicated or skipped event, and it will be silent.
- **Snapshots and upcasting interact badly.** A snapshot is serialised aggregate state; change the state
  shape, or register an upcaster that changes how events fold, and every existing snapshot is *wrong*,
  not merely stale. So snapshots carry a **generation tag**, and bumping it invalidates all of them and
  forces full replay for every aggregate — potentially hours. That must be a planned action with the
  cost known upfront, not a surprise following an innocuous-looking upcaster registration. Folds into
  D24.
- **Snapshots only, not a full derived event history.** Since the broker retains everything, a
  per-aggregate history store is an *index over the log*, not extra durability — so it only earns its
  place if something needs indexed per-aggregate reads. Today that's hypothetical.

**Q7** — Chain-context scoping (chain-scoped vs step-scoped fields). Deferred; revisit when baggage
growth is measurable.

*(Q3, upcasting, is now D24 — core, not open.)*

---

## Phasing

| Phase | Content | Proves |
|---|---|---|
| 0 | Resolve Q1. Repo skeleton: `core` (no deps) + `starter` + `company-stubs` + `company-sdk` + `usage-example`. | D29, D30, D32 |
| 1 | **Test 1 + 1b** (see above): core model, three participants, submit/receipt/verdict, one aggregate, one read model, RYOW via `atLeast`, history assertion. Semantically faithful in-memory implementation with a controllable scheduler. No broker, no HTTP. | D3, D4, D5, D18, D19, D28, D31, D33, D34, D35 |
| 2 | HTTP surface derived from registrations + OpenAPI. Tiers 1–2 at the edge. | D1, D2, D6, D7 |
| 3 | Request-reply: durable outcome store, three modes, outcome-as-event + relay. | D8, D20 |
| 4 | Broker port + conformance suite + **Pulsar and Kafka-API together** + `provision`. Schema registry, `FULL_TRANSITIVE`, upcasting. | D10, D15, D23, D24, D25 |
| 5 | Multi-node: partition assignment, derived recovery store (snapshots), hydration, rebalance, readiness gating. | D16, D17, D26, D27, Q6 |
| 6 | Failure handling: taxonomy, skip registry, diagnostics. | D22 |
| 7 | Query topics, audit stream, overlap-publish, gap detection. | D11–D14 |
| 8 | Read side: projections, positions, rebuild, versioning, read-your-own-writes. | D9 |
| 9 | Crypto-shredding, claim-check (if measured as needed), observability. | D21 |

Testing is a headline feature, not a phase: in-memory everything, deterministic time and ids,
`given(events) / when(command) / then(events)`.
