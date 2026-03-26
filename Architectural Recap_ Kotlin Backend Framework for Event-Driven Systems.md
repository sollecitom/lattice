# Architectural Recap: Kotlin Backend Framework for Event-Driven Systems

**Author:** Manus AI  
**Date:** March 17, 2026  

## 1. Executive Summary

This document captures the comprehensive architectural design for a proposed application development framework targeted primarily at Kotlin but compatible with all programming languages. The framework is designed to streamline backend development by enforcing strict architectural constraints based on Command Query Responsibility Segregation (CQRS), Event Sourcing (ES), and the Actor Model.

Through iterative design discussions, the architecture has converged on a highly scalable, robust model that leverages Apache Pulsar as the immutable event backbone and NATS for synchronous boundary interactions. The framework rejects the "Outbox Pattern" in favor of permanent event journals and utilizes specialized materialized views to serve different access patterns—from aggregate recovery to complex OLAP queries and local SDK-based caching.

## 2. Core Goals and Motivations

The framework was conceived with three primary goals:

1.  **Streamline Development:** Reduce the boilerplate and cognitive load associated with distributed systems, state management, and eventual consistency.
2.  **Enforce Strong Architectural Constraints:** Achieve high degrees of modularity, testability, readability, discoverability, and observability by strictly defining how components interact.
3.  **Simplify Concurrency and Scaling:** Eliminate race conditions, database locking, and complex thread management through partitioned, sequential processing.

These goals are heavily inspired by modern reactive architecture principles, particularly those articulated by Vaughn Vernon in his work on Domain-Driven Design (DDD) and the VLINGO XOOM platform [1].

## 3. The Converged Architecture

The architecture represents a synthesis of strict CQRS, Event Sourcing, and purpose-built infrastructure components. It is structured around the principle of "Turning the Database Inside Out," where an immutable log forms the system of record, and all other state is derived [2].

### 3.1 Strict CQRS and Boundary Endpoints

The system boundary strictly enforces CQRS. Commands, queries, and events are the only valid inputs. Boundary endpoints (API Gateways or BFFs) act as the bridge between the synchronous external world (HTTP/gRPC) and the asynchronous internal core.

To facilitate synchronous request-reply over an asynchronous backbone, the framework utilizes **NATS**. When an external client submits a command:
1.  The boundary endpoint publishes the command to the internal system.
2.  It simultaneously subscribes to a unique NATS subject based on the command ID.
3.  The internal aggregate processes the command, emits events, and publishes the outcome to that NATS subject.
4.  The boundary endpoint receives the outcome via NATS and responds to the client synchronously.

This design cleanly separates the fast, ephemeral request-reply routing (handled by NATS) from the durable, long-term event storage.

### 3.2 The Event Backbone: Apache Pulsar

**Apache Pulsar** serves as the unified event propagation mechanism and the durable event store. It was selected over alternatives like Kafka or NATS JetStream for this specific role due to its tiered storage architecture, which provides infinite retention without infinite disk costs, and its native support for partitioned topics [3].

Instead of attempting to use Pulsar as a direct database (which would require inefficient filtering or millions of dynamic topics), Pulsar acts as the immutable journal. Commands and events are routed to partitioned topics based on their aggregate ID. This ensures that all inputs for a specific aggregate are processed sequentially by the same consumer, fulfilling the core concurrency goal.

### 3.3 Internal Processing via the Actor Model

Internal processing is handled by aggregates deployed as actors. Each aggregate is responsible for a specific partition key. The actor processes commands and events in strict order. Because the actor ensures single-threaded access to its internal state, developers do not need to implement optimistic concurrency control or database locks [4]. 

The only side effect an aggregate is permitted to produce is the publication of events back to Pulsar. This makes the domain logic pure, highly testable, and strictly decoupled.

### 3.4 Materialized Views: The "Many Views" Approach

A critical architectural decision was the rejection of a single, monolithic database to serve all read patterns. Because Pulsar is an append-only log, it is not optimized for random access or complex queries. Therefore, the framework utilizes Pulsar to fan out events to specialized materialized views:

| View Type | Purpose | Technology Candidates | Update Mechanism |
| :--- | :--- | :--- | :--- |
| **Aggregate Recovery Store** | Fast point-reads for actor state rehydration upon crash. | NATS KV, Redis, Compacted Pulsar Topic, Embedded RocksDB | Updated exclusively from the aggregate's own output events (snapshots). |
| **Query Model Projections** | Serving application-specific CQRS read models. | PostgreSQL, Elasticsearch, MongoDB | Independent consumers tailing Pulsar topics. |
| **OLAP / Audit Store** | Time-travel debugging, time-series analysis, and complex analytics. | Apache Druid, ClickHouse, EventStoreDB | Independent consumers tailing Pulsar topics. |

### 3.5 SDKs as Local Hubs

To address the latency and cache invalidation challenges inherent in eventual consistency, the framework introduces SDKs that function as local hubs. 

These SDKs run in-process with the client application. They subscribe to relevant event streams from Pulsar and materialize lightweight views directly in memory. When a client issues a command, the SDK tracks the resulting event offset. Subsequent queries can specify this offset, and the SDK will wait until its local view has consumed up to that point before returning the result. This elegantly provides "read-your-own-writes" consistency while serving queries with zero network latency.

## 4. Discarded Alternatives and Trade-offs

During the design process, several alternatives were evaluated and explicitly discarded to maintain alignment with the core goals.

### 4.1 Discarded: The Outbox Pattern
The traditional Transactional Outbox pattern (saving events to a relational database table in the same transaction as state changes, then polling the table to publish) was rejected. As Vaughn Vernon argues, the Outbox pattern treats events as ephemeral and introduces fragile deletion processes [5]. Instead, the framework writes directly to Pulsar, treating the event stream as the permanent journal and the absolute system of record.

### 4.2 Discarded: Sidecar Proxy for Polyglot Support
To achieve polyglot compatibility, a sidecar proxy pattern (similar to Eigr Spawn or Dapr) was considered. This would involve a sidecar pulling messages from Pulsar and pushing them to the application via gRPC. 

This was rejected because it fundamentally inverts the control flow from pull-based to push-based. A push-based model requires complex backpressure negotiation, batch semantics, and flow control between the sidecar and the application. Because the framework requires strict sequential processing per partition, the actor must control the pace. 

**The Chosen Alternative:** The framework will provide embedded SDK libraries (starting with a native Kotlin implementation). The polyglot nature is achieved through a standardized wire protocol (e.g., Protobuf over gRPC) that defines commands, queries, and events. Other languages can implement this protocol via their own native SDKs, preserving the pull-based consumption model and zero-IPC overhead.

### 4.3 Discarded: Topic-Per-Aggregate in Pulsar
Creating a distinct Pulsar topic for every single aggregate instance was considered to provide clean, sequential event streams per aggregate without filtering. 

This was rejected due to operational concerns. At scale (millions of aggregates), dynamic topic creation introduces latency on the first command, and managing fine-grained permissions or metadata for millions of topics becomes a severe bottleneck. 

**The Chosen Alternative:** The framework uses partitioned topics per aggregate *type* (e.g., `orders`), relying on the Aggregate Recovery Store (Key-Value) to provide the latest snapshot, and only replaying the small delta of events from the shared partition.

## 5. Idempotency and Reliability

Because the system relies on at-least-once delivery semantics (inherent in distributed messaging), idempotency is a mandatory constraint. 

If an aggregate crashes and recovers from a snapshot, it may re-consume messages it has already processed but not yet acknowledged to Pulsar. The framework must handle this transparently to streamline development (Goal 1). The aggregate's state must include a record of recently processed command IDs. Upon receiving a message, the aggregate checks this record; if the offset or command ID indicates it has already been processed, the aggregate safely ignores it without emitting duplicate events, ensuring exactly-once processing semantics [6].

## 6. Concrete Domain Examples

To illustrate how these architectural components interact in practice, consider the following domain examples.

### 6.1 Consumer Banking (High-Throughput Financial Ledgers)
In a consumer banking application, the core aggregate is the `Account`. 
- **Topic and Partitioning:** All account-related commands (e.g., `Deposit`, `Withdraw`, `Transfer`) and events (`Deposited`, `Withdrawn`) are published to a single Pulsar topic, e.g., `persistent://banking/core/accounts`. The partition key is the `AccountID`. This guarantees that all transactions for a specific account are processed strictly sequentially by a single actor, eliminating race conditions that cause double-spending or overdrafts.
- **Idempotency:** A client app generates a unique `ActionID` (for the user's intent) and `InvocationID` (for the specific network request). If a user taps "Transfer" and the network drops, the retry uses the same IDs. The `Account` actor recognizes the duplicate `InvocationID` and ignores the second request, preventing a double transfer.
- **Materialized Views:** 
  - The *Recovery Store* holds the current balance and last processed offset for fast actor rehydration.
  - An *OLAP Store* (e.g., ClickHouse) ingests the event stream to detect real-time fraud patterns across millions of accounts.
  - A *Query Projection* (e.g., PostgreSQL) maintains a denormalized list of the user's last 50 transactions for the mobile app UI.

### 6.2 E-Commerce Order Fulfillment (Service Choreography)
In an e-commerce system, a checkout process involves multiple bounded contexts: `Order`, `Inventory`, and `Payment`.
- **Choreography over Orchestration:** Instead of a central orchestrator calling services synchronously, the system relies on event-driven reactions. 
- **Flow:** 
  1. The API Gateway publishes a `PlaceOrder` command to the `Order` partition.
  2. The `Order` actor processes it and emits an `OrderPlaced` event to Pulsar.
  3. The `Inventory` and `Payment` contexts subscribe to the `Order` topic. The `Inventory` actor reacts by reserving stock and emitting `StockReserved`. The `Payment` actor reacts by charging the card and emitting `PaymentAuthorized`.
  4. The `Order` actor listens for these downstream events. Once both are received, it emits `OrderConfirmed`.
- **Async Request-Reply:** Throughout this asynchronous dance, the API Gateway waits on a NATS subject (`reply.order.<CommandID>`). When the `OrderConfirmed` event is finally processed, a listener publishes the success to NATS, and the API Gateway returns an HTTP 200 to the browser.

## 7. Conclusion

This framework architecture provides a highly opinionated, robust foundation for backend development. By delegating synchronous request-reply to NATS, durable event journaling to Apache Pulsar, and internal processing to an embedded Kotlin Actor SDK, it achieves high throughput and strict consistency. The "Many Views" approach ensures that the system can serve diverse query patterns without compromising the integrity of the event store.

---

### References

[1] V. Vernon, *Reactive Messaging Patterns with the Actor Model*, Addison-Wesley Professional, 2015.  
[2] M. Kleppmann, "Turning the database inside-out with Apache Samza," Confluent Blog, 2015.  
[3] Apache Pulsar Documentation, "Tiered Storage," Apache Software Foundation.  
[4] D. Badel, "Actor Model and Event-Sourcing, a perfect combination for distributed applications," Medium.  
[5] V. Vernon, "Why the Outbox Pattern Falls Short – And the Smarter Alternative for Event-Driven Systems," Kalele Blog, May 2025.  
[6] Event-Driven.io, "Idempotent Command Handling," Nov 2024.
