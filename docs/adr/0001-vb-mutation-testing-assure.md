# ADR-0001: Mutation testing for the Assure characterisation net (VB.NET)

**Status:** Accepted — implemented in PR #39 (bootstrap token-level generator; Roslyn VB still pending)
**Date:** 2026-07-08
**Deciders:** Chris (project owner); backend maintainers
**Related:** `VbCharacterisationRunner`, `CoverageParser` (line/branch coverage, PR #35), the throwaway proof at [`spike/vb-mutation/`](../../spike/vb-mutation/README.md)

## Context

Assure generates an MSTest **characterisation net** that pins how the *original, unmodified* VB.NET behaves today, then runs it against the real VB assembly on the Linux .NET sidecar (`VbCharacterisationRunner`: write VB project → `docker exec dotnet test` → parse `.trx`). GREEN means "behaviour unchanged," not "correct." The whole value proposition is: *when you later migrate this code, the net catches any behaviour change.*

We recently added line and branch **coverage** (Coverlet) as a confidence signal. But coverage only proves code *ran* — not that a test would *notice* a change. A characterisation suite can execute every line and branch of the legacy VB and still assert almost nothing, leaving the net full of holes.

**Mutation testing** is the metric that actually answers "is this net worthy?": deliberately inject a small fault (a *mutant*) into the code under test, re-run the suite, and see whether a test fails.
- Test fails → mutant **killed** (the net caught the regression — good).
- Suite still green → mutant **survived** (a blind spot — the net would miss this change).
- **Mutation score** = killed / (total non-equivalent mutants).

Mutation score strictly subsumes coverage: a line that no test asserts on cannot kill any mutant located on it. For a skeptical evaluator ("how do I trust AI-generated characterisation tests?"), a live "watch the net catch injected regressions, and here are the gaps" run is a far stronger artifact than any percentage.

**Constraints**
- **Stryker.NET is C#-only.** It mutates C# Roslyn syntax trees; it has no VB.NET mutators, so it cannot be pointed at a `.vbproj`. Mutating the legacy VB directly is off the table for Stryker.
- **Cost is O(mutants × suite-runtime).** Each mutant is a fresh compile + full test run — seconds each, minutes per class. This cannot live inside a synchronous wizard request (Bucket4j 100/hr, per-request timeouts).
- **Sidecar is Linux .NET SDK.** Fine for headless business-logic VB (the `OrderProcessor` demo path); UI-coupled VB doesn't compile there anyway — the same precondition Assure already enforces.
- **Equivalent mutants** (mutations that don't change behaviour) can never be killed, so 100% is unattainable and the score always needs that caveat.

## Decision

Add mutation testing to the Assure path as an **opt-in, asynchronous "Prove the net" job** that:

1. Generates VB mutants from the assurable source using a **curated operator set** (see *VB-generator design*).
2. For each mutant, **reuses `VbCharacterisationRunner`** to recompile and run the existing characterisation suite against the mutated assembly, interpreting RED as *killed* and GREEN as *survived*.
3. Aggregates a **mutation score** and, crucially, the list of **surviving mutants** (the actionable output — each is a concrete gap in the net).
4. Surfaces the score in the UI next to the coverage badge and streams progress; **never gates** GREEN/RED.

Target the generator at **Roslyn VB** (Option B) for production, but bootstrap the MVP with a **token-level mutator** (Option C) — the spike proves the end-to-end loop with the latter.

## Options Considered — mutant generation

### Option A: Stryker.NET
| Dimension | Assessment |
|-----------|------------|
| Complexity | Low (if it worked) |
| Fit | **Does not support VB.NET** — C# syntax trees only |
| Reuse | None for the VB path |

**Pros:** Mature, productised, standard report format.
**Cons:** Cannot mutate VB at all. (Still relevant for the *Migration* C# path — see Consequences — but that is a different feature.)
**Verdict:** Rejected for Assure/VB.

### Option B: Roslyn VB AST mutation (`Microsoft.CodeAnalysis.VisualBasic`)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Medium–High |
| Fidelity | High — mutate real syntax nodes, know exact spans, avoid string-matching false hits |
| Reuse | Runs in the sidecar; parses the actual VB |

**Pros:** Precise, robust to formatting; can classify mutant kind; extensible operator set; can skip un-compilable mutants cleanly.
**Cons:** Needs a small .NET tool (VB Roslyn) in the sidecar; more upfront work than text mutation.
**Verdict:** **Chosen as the production target.**

### Option C: Token/line-level source mutation (regex/tokeniser)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Low |
| Fidelity | Medium — can mis-hit operators inside strings/comments without care |
| Reuse | Language-agnostic; runs anywhere, no Roslyn needed |

**Pros:** Trivial to build; great for a spike/MVP; no new sidecar tooling.
**Cons:** Fragile (must avoid string literals, comments); limited operator awareness.
**Verdict:** **Chosen to bootstrap** the MVP and the spike; migrate to Option B as the generator matures.

### Option D: Mutate a C# transpilation of the VB
**Verdict:** Rejected. The characterisation suite runs against the *VB assembly*. Mutating a C# rendering would test the rendering, not the legacy behaviour — unfaithful and circular. It would also re-introduce the very translation risk Assure exists to de-risk.

## Trade-off Analysis

- **B vs C** is a fidelity/effort trade, not either/or: C ships the loop this week and proves value; B replaces the generator later behind the same interface. The expensive, risky part (compile + run per mutant) is *identical* for both and already exists in `VbCharacterisationRunner`.
- **Sync vs async** is not a real choice: at O(minutes) per class, the job must be async (submit → poll/stream), never a blocking POST. This is the main new infrastructure.
- **Score vs surviving-mutant list:** the *score* is the headline, but the *surviving mutants* are the actionable payload — each names a specific behaviour the net fails to pin. Prioritise surfacing those.

## VB-generator design

A mutant = (original source, one operator applied at one site). The curated operator set, ranked by signal-per-cost for legacy VB business logic:

| Operator | Example | Why it matters |
|----------|---------|----------------|
| Relational boundary | `<=` ↔ `<`, `>=` ↔ `>` | Off-by-one at thresholds — the classic pricing/tiering bug; only a *boundary* test kills it |
| Relational swap | `>` ↔ `<`, `=` ↔ `<>` | Inverted comparisons |
| Boolean connective | `AndAlso` ↔ `OrElse`, `And` ↔ `Or` | Short-circuit / precedence faults |
| Logical negation | insert/remove `Not` | Inverted guards |
| Arithmetic | `+` ↔ `-`, `*` ↔ `/` | Calculation drift |
| Literal tweak | `n` → `n±1`, `0` → `1` | Constant/threshold errors |
| Return / branch | force early `Return`, drop an `ElseIf` arm | Missing-path faults |

Generation rules:
- Operate on tokens **outside** string literals and comments (Option C) or on syntax nodes (Option B). Never mutate inside `"..."` or after `'`.
- One mutation per mutant (first-order). Emit `{ id, file, line, operator, before, after }`.
- A mutant that **fails to compile** is discarded (not counted) — a non-compiling mutant is not a behaviour change.
- Cap mutants per class (e.g. sample N per operator) so runtime stays bounded; **log what was sampled/skipped** — never silently truncate.
- Flag likely **equivalent mutants** heuristically where cheap; otherwise report them as "survived (possibly equivalent)" for human triage rather than penalising the score blindly.

## Background-job shape

```
POST /api/assure/mutation-test { sessionId, className }   → { jobId }         (returns immediately)
GET  /api/assure/mutation-test/{jobId}                    → { status, done/total, score?, survivors[] }
```

- Runs on a bounded worker pool off the request thread. Progress is polled or streamed (SSE) so the UI shows a live feed: *"`<=`→`<` @ line 42 … KILLED ✓"*, *"`AndAlso`→`OrElse` @ line 58 … SURVIVED ✗"*.
- Per mutant: reuse `VbCharacterisationRunner.run(...)` (or a thin extracted core) against the mutated VB + the recorded suite. `BuildStatus.RED` → killed; `GREEN` → survived; `ERROR` (won't compile) → discard.
- Baseline sanity check first: original + net must be GREEN, else abort with a clear message (a red baseline means the net isn't faithful yet — repair it before mutating).
- Persist per session (in-memory for v1, matching current `SessionStore`); no DB required.
- **Never gates.** Like coverage, mutation score is informational; it flags gaps, it does not block a PR.

## Consequences

**Easier**
- A defensible, independent answer to "are these characterisation tests worthy?" — and a compelling live demo for a skeptical audience.
- Surviving mutants become a concrete to-do list for strengthening the net (or feeding Assure's auto-repair loop a target).
- Reuses ~90% of existing machinery; the only genuinely new pieces are the generator and the async job runner.

**Harder / new**
- First async/background job in the backend — needs a job store, progress transport, cancellation, and worker-pool limits alongside Bucket4j.
- Runtime management: mutant caps, per-mutant timeouts, and honest reporting of sampling.
- A small VB-Roslyn tool in the sidecar for Option B.

**To revisit**
- Whether to also surface Stryker's mutation score on the **Migration C#** path (the `vbgone-output` repo already runs Stryker — this would just parse `mutation-report.json`, cheap, but a separate feature).
- Equivalent-mutant handling as the operator set grows.
- Incremental mutation (only changed/uncovered regions) if full runs get too slow on large classes.

## Action Items

1. [x] Land the throwaway spike (Option C, one operator, `GetDiscountTier`) to prove killed vs survived end-to-end on the sidecar — see [`spike/vb-mutation/`](../../spike/vb-mutation/README.md). *(PR #37)*
2. [x] Extract a suite-runnable core from `VbCharacterisationRunner` that takes (VB source, suite) and returns GREEN/RED, so the mutation job can call it per mutant without Spring wiring. *(`runAgainstSource`, PR #39)*
3. [x] Build the token-level generator with the boundary/boolean/arithmetic operators and string/comment exclusion. *(`VbMutator` behind `MutantGenerator`, PR #39)*
4. [x] Add the async job (endpoints, job store, worker pool, streamed progress) with a baseline-must-be-green precondition. *(single-worker executor + polled status, PR #39)*
5. [x] UI: mutation-score readout + surviving-mutant list on the Assure result, attributed and non-gating. *(`MutationPanel`, PR #39)*
6. [ ] Later: swap the generator to Roslyn VB (Option B); scope mutations to the class under test in a multi-class subset; evaluate surfacing Stryker score on the Migration path.
