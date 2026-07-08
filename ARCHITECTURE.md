# VBGone — System Architecture

*High-level architecture of the system as it exists today (v1.0 + Phase 2 P1).*

## 1. Overview

VBGone is an AI-assisted tool that scaffolds the migration of legacy VB.NET code to modern C#, one test at a time. A developer uploads VB.NET source; Claude analyses it and generates a C# interface, an NUnit test suite, and a stub implementation. The developer (or Claude) then implements the C# until the tests pass, and VBGone raises a pull request against a companion output repository.

It is a single-tenant, internally-facing web application, deployed from a Mac Mini and exposed to the internet through a Cloudflare Tunnel. Scale is deliberately small: low concurrent usage, no database in v1.0, and in-memory session state.

**Assumptions this document reflects**

- Small, internal, low-traffic workload (single-digit concurrent users).
- Existing stack is fixed: Spring Boot 3 / Java 21 on the backend, React 19 / TypeScript on the frontend.
- Correctness and cost-transparency matter more than horizontal scale.

## 2. Context — how the pieces connect

```
                          Internet
                             │
                             ▼
        ┌─────────────────────────────────────────┐
        │  Cloudflare  (DNS + Access + Tunnel)     │
        │  OAuth gate (Google / GitHub)            │
        └─────────────────────────────────────────┘
                             │  tunnel
                             ▼
   ┌──────────────────────────────────────────────────────┐
   │            Mac Mini — Docker Compose                  │
   │                                                        │
   │   ┌────────────┐   /api/*    ┌───────────────────┐    │
   │   │  frontend  │ ──────────▶ │     backend       │    │
   │   │ nginx :3000│             │ Spring Boot :8080 │    │
   │   │  React SPA │ ◀────────── │                   │    │
   │   └────────────┘             └───────┬───────────┘    │
   │                                      │                 │
   │                     Anthropic Java SDK│  GitHub REST API│
   │                                      │         │        │
   │                                      ▼         ▼        │
   │                               Claude API   GitHub       │
   │                                      │    (vbgone-output│
   │                     generated C#     │      PRs)         │
   │                          ▼           │                  │
   │                 ┌─────────────────┐  │                  │
   │                 │ shared volume   │◀─┘                  │
   │                 └────────┬────────┘                     │
   │                          │  dotnet test                 │
   │                          ▼                              │
   │                 ┌─────────────────┐                     │
   │                 │  dotnet-runner  │                     │
   │                 │ .NET SDK 10.0   │ → .trx results      │
   │                 └─────────────────┘                     │
   └──────────────────────────────────────────────────────┘
```

External dependencies: the Anthropic API (Claude Sonnet + Haiku) for all generation, and the GitHub REST API for raising PRs against the `vbgone-output` companion repository.

## 3. Components

### Frontend — React wizard
A React 19 + TypeScript SPA built with Vite, served as static assets by nginx. nginx also reverse-proxies `/api/*` to the backend. The UI is a six-step wizard (upload → analysis → interface → tests+red → implement+green → raise PR), plus a Phase-2 migration queue and a D3 dependency graph for whole-solution (zip) uploads. All REST calls and a mock toggle live in a single `migrateApi.ts` module.

### Backend — Spring Boot
A Spring Boot 3 / Java 21 application, the orchestration core. A single `MigrationController` exposes the `/api/migrate/*` endpoints. Behind it sit focused services: `AnalysisService`, `GenerationService`, `BuildService`, `GitHubService`, `CostService`, and a thin `ClaudeClient` wrapper over the Anthropic Java SDK. Cross-cutting config covers CORS and a Bucket4j rate-limit filter.

### Claude integration
The Anthropic Java SDK calls Claude with a deliberate model split: **Sonnet** for the heavier reasoning tasks (analysis, test generation, implementation) and **Haiku** for the cheaper structural tasks (interface and stub generation). Prompt caching is used to reduce cost, and every call's token usage is tracked so the UI can display USD and GBP cost per step.

### dotnet-runner — .NET SDK sidecar
A .NET SDK 10.0 container that executes the red-green loop. The backend writes generated C# to a shared Docker volume, then runs `dotnet test` in the sidecar via a `ProcessRunner`. Results are parsed from the `.trx` XML — total/passed/failed counts, RED/GREEN status, and the names of individual failing tests, which feed the retry feature.

### GitHub integration
`GitHubService` uses the GitHub REST API to commit generated files and open a PR against `vbgone-output`, either per-class or as a single combined PR once every class in a project-mode queue is complete.

## 4. Data flow — one migration

1. User uploads a `.vb` file (or a `.zip` solution). `POST /api/migrate/analyse` → Claude Sonnet returns classes, methods, dependencies, complexity, code-quality rating, code smells, refactoring suggestions, and VB.NET anti-patterns. A `sessionId` (UUID) is minted.
2. `POST /api/migrate/interface` → Haiku generates an editable C# interface. A God-class warning is shown when complexity is HIGH or quality is POOR.
3. `POST /api/migrate/tests` → Sonnet generates the NUnit suite; `POST /api/migrate/stub` → Haiku generates a stub. `POST /api/migrate/build` runs `dotnet test`, which is expected to come back **RED**.
4. `POST /api/migrate/implement` (mode `CLAUDE` or `STUB`) produces the implementation; the build is expected to turn **GREEN**. On failure, `retry-implement` re-prompts Claude with the failing test names, up to three attempts.
5. `POST /api/migrate/pr` commits the files and raises the PR. `GET /api/migrate/cost/{sessionId}` returns per-step token usage and total cost.

## 5. State management

Session state lives entirely in memory — a `ConcurrentHashMap<String, MigrationSession>` in `SessionStore`, keyed by the `sessionId` from `/analyse`. Each session holds the VB.NET source, the generated interface, tests, stub, implementation, build results, and token usage. There is no database in v1.0.

**Consequence:** a backend restart or a browser refresh loses the session. Adding PostgreSQL for session persistence and resume is the top remaining Phase-2 P1 item — see trade-offs below.

## 6. Cross-cutting concerns

**Security.** Cloudflare Access gates the whole app behind Google/GitHub OAuth. Bucket4j enforces 100 requests/hour/IP on all `/api/migrate/*` endpoints (HTTP 429 on breach). CORS is locked to `vbgone.online` and localhost. Uploads are validated (`.vb`/`.zip`, max 1 MB). Secrets (Anthropic key, GitHub token) are injected as environment variables, with `.env` git-ignored.

**Cost control.** The Haiku/Sonnet split, prompt caching, and per-step token tracking keep spend low and visible.

**Testing.** 122 backend tests (JUnit 5 + Mockito + MockMvc) and 203 frontend tests (Vitest + React Testing Library), with CI on GitHub Actions (Vitest, ESLint, Prettier, Codecov). The generated C# has its own quality pipeline in `vbgone-output` (Roslynator, Coverlet, Stryker, CodeQL).

**Deployment.** Docker Compose on a Mac Mini — three services (frontend, backend, dotnet-runner) plus a shared volume — reachable via Cloudflare Tunnel at vbgone.online.

## 7. Key trade-offs

| Decision | Why it fits today | What it costs / when to revisit |
|---|---|---|
| **In-memory sessions, no DB** | Simplest possible v1; fine for low traffic and short-lived sessions | No durability — restart/refresh loses work. Revisit now (Phase-2 P1: PostgreSQL persistence + resume). |
| **Haiku + Sonnet split** | Big cost saving on structural generation | Slightly more prompt/model plumbing; occasional quality gap on borderline interface generation. |
| **.NET sidecar over a shared volume** | Cleanly separates JVM orchestration from .NET execution; `.trx` gives structured results | Shared-volume coupling and process orchestration add operational moving parts; no isolation between concurrent builds. |
| **Single Mac Mini + Cloudflare Tunnel** | Zero cloud cost, easy to operate, OAuth for free via Access | Single point of failure, no horizontal scale, home-network dependent. Adequate while internal-only. |
| **Cloudflare Access for auth** | No auth code to build or maintain | Ties identity to Cloudflare; Phase-2 P2 plans a move to GitHub OAuth so PRs target the user's own repo. |

## 8. What I'd revisit as it grows

- **Persistence first.** In-memory state is the sharpest limitation; PostgreSQL for sessions unlocks refresh-survival, resume, and a foundation for history and cost reporting.
- **Concurrent-build isolation.** One shared volume and one sidecar is fine for a single user; multi-user use needs per-session working directories or a build queue.
- **Auth ownership.** Moving from Cloudflare Access to GitHub OAuth lets each user raise PRs against their own repo instead of the fixed `vbgone-output`.
- **Observability.** For anything beyond internal use, add structured logging and metrics around Claude latency/cost and the dotnet-test loop.

## 9. Assure mode — components and the cross-language runner

Alongside Migrate (rewrite VB.NET into fresh C#), VBGone has a second workflow, **Assure**. Its goal is the opposite: don't rewrite anything. Instead, wrap the *original* VB.NET in a characterisation-test safety net so a vulnerable dependency can be patched with confidence that behaviour hasn't changed. The guiding principle is "green means unchanged, not correct" — the tests deliberately capture today's behaviour, bugs included.

### Components

Assure adds its own controller and services on the existing backend, and reuses the shared workspace volume and the .NET SDK sidecar:

- `AssureController` (`/api/assure/*`) — the Assure endpoints: `assess` / `assess-project` (readiness), `baseline`, `baseline-tests`, and `rerun-baseline-tests`.
- `AssureAssessmentService` — the **front gate**. A purely static pass (no AI, nothing leaves the tenant) that classifies every business-logic method into three readiness buckets: **net-ready** (self-contained, UI-free — assurable today), **windows-gated** (pure logic trapped in a WinForms-referencing class), and **refactor-first** (logic genuinely entangled with the UI).
- `AssureService` — orchestrates the two AI steps: pinning the class's real public surface (mechanical model) and generating the MSTest characterisation suite (reasoning model), with the same token/cost accounting as Migrate.
- `VbCharacterisationRunner` — the crux. Writes two .NET projects to the shared volume and runs the suite against the untouched original on the sidecar.

### The cross-language test runner

The interesting mechanic — how C# tests exercise VB.NET code — falls straight out of how .NET works. .NET is a multi-language runtime: VB.NET and C# both compile to the same intermediate language (IL), which executes on the **CLR** (Common Language Runtime). Because the compiled output is language-agnostic, a C# assembly can reference and call a VB.NET assembly as if they were one program.

So `VbCharacterisationRunner` writes:

1. a **VB project** (`{Class}.Vb/{Class}.vbproj`) containing the original, untouched VB.NET source, and
2. a **C# MSTest project** (`{Class}.Baseline`) whose `.csproj` has a `ProjectReference` to that VB project.

It then runs `docker exec <dotnet-runner> dotnet test <baseline path>` on the sidecar. The .NET SDK compiles both projects to IL, and the C# test assembly calls directly into the compiled-from-VB assembly — so the tests run against the genuine legacy code, not a translation. Output is parsed from `.trx`: GREEN when every assertion holds (the net is faithful), RED when assertions fail, ERROR when the VB or suite fails to compile.

```
        ┌──────────────────────────────────────────────┐
  VB.NET (untouched) ──compile──▶  VB → IL assembly     │
                                          ▲             │  ← one CLR
  C# MSTest (Claude) ──compile──▶  C# tests → IL ───────┘     runs both
                                   (calls the real VB methods)
                                          │
                                          ▼
                                   results.trx  →  GREEN / RED
```

Because a failing test can only mean the *test* is wrong (the code was never touched), a red run drives an auto-repair loop (mechanical → reasoning → escalation, up to three attempts). Anything that can't be pinned deterministically — e.g. a value derived from the current time — is quarantined rather than faked, and every edit is checked and logged to an audit trail.

## 10. The Windows-runner unblock

The one real constraint today is environmental, not fundamental. The sidecar runs on **Linux**, whose .NET runtime can compile VB and run MSTest but cannot load **WinForms** (`System.Windows.Forms` is Windows-only). That is the sole reason a class can be gated: a WinForms-referencing assembly won't load headless on Linux, so it surfaces as a compile ERROR rather than a pass. This is a side effect of the current Linux operational environment — not a limitation of Assure itself.

Running the **same** sidecar on a Windows host — a Windows Docker container, a Windows CI runner, or a Windows box — with the Windows Desktop .NET runtime removes that barrier: WinForms-referencing assemblies compile and execute unchanged. Concretely, this promotes the entire **windows-gated** bucket (pure logic that merely lives in a WinForms-bound class) to assurable with **zero code changes** — it is purely an environment swap. The **refactor-first** bucket (logic that actively reads and writes live controls) is a genuine code-structure issue and would still benefit from separating the logic out for reliable, deterministic tests, even on Windows.

In short: swap the runner's OS to Windows and the gating largely disappears; the readiness buckets exist to be honest about what today's Linux environment can cover, not to describe a permanent boundary.
