# Lattice

A framework for event-driven, CQRS, event-sourced backend systems.

**Status: early. The framework does not exist yet.**

The repo currently holds a usage example and a set of company stubs. The design is being driven
outside-in from developer-facing tests; the framework gets extracted from that once the usage has a
shape worth generalising.

Design discussion, decisions, and open questions live in
[`events-framework.md`](events-framework.md). That is the source of truth — this README stays short
deliberately.

> An earlier exploration under this name (SDK/framework/connector split, `Fact` hierarchy, in-memory
> engine) was removed rather than continued. Its ideas are recorded under *Prior art: lattice* in the
> design doc; the code is in git history.

## Modules

| Module | Purpose |
|---|---|
| `company-stubs` | Stand-in for the types a consuming company defines — domain facts, invocation context, identity. **Must compile without importing the framework**; that invariant is how genericity stays enforced by the compiler rather than by discipline. |
| `usage-example` | Drives the design from a developer's perspective. Expected to become several examples over time. |

## Commands

```bash
just build          # build and test
just rebuild        # clean build with refreshed dependencies
just publish        # publish to mavenLocal, only when artifacts changed
just update-all     # internal deps, external deps, Gradle wrapper
just license-audit  # extended report; -compact for the workspace flow
```

Part of the workspace's `publishable` set, so `just refresh-workspace` builds and publishes it in
dependency order after swissknife.
