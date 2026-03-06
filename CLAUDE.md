# VBGone App — Claude Code Context

> *VBGone — AI-assisted migration from legacy VB.NET to modern C#, one test at a time.*

## What This Project Is

VBGone is a Spring Boot + React application that automates the scaffolding of VB.NET to C# migrations. It uses the Anthropic Java SDK to call Claude, which analyses VB.NET source and generates C# interfaces, NUnit test suites, and stub implementations. The developer then implements the C# to make the tests pass.

**Live at [vbgone.online](https://vbgone.online)** — deployed via Cloudflare Tunnel from a Mac Mini running Docker Compose.

## Companion Repo

**vbgone-output** — the generated C# code lives here, with its own .NET quality pipeline (Roslynator, Coverlet, Stryker, CodeQL). VBGone raises PRs against this repo programmatically via the GitHub API.

## Architecture

```
Internet
    ↓
Cloudflare (DNS + Access + Tunnel)
    ↓
Mac Mini — Docker Compose
    ├── frontend (nginx :3000)
    │       ↓ /api/* proxy
    ├── backend (Spring Boot :8080)
    │       ↓ Anthropic Java SDK
    │   Claude API
    │       ↓
    │   Generated C# → shared Docker volume
    │       ↓
    ├── dotnet-runner (.NET SDK 10.0 sidecar)
    │       ↓ dotnet test → .trx results
    │   Results returned to frontend
    │       ↓
    └── GitHub API → PR against vbgone-output
```

## Tech Stack

| Layer | Tech |
|---|---|
| Frontend | React 19 + TypeScript + Vite |
| Backend | Spring Boot 3 / Java 21 |
| Claude integration | Anthropic Java SDK |
| Build tool | Maven |
| Containerisation | Docker Compose |
| .NET execution | .NET SDK 10.0 sidecar container |
| GitHub integration | GitHub REST API |
| Rate limiting | Bucket4j (100 req/hr/IP) |
| Security | Cloudflare Access (Google/GitHub OAuth) |
| Deployment | Cloudflare Tunnel → Mac Mini |

## Project Structure

```
vbgone-app/
├── backend/                        ← Spring Boot application
│   ├── src/main/java/
│   │   └── com/vbgone/
│   │       ├── VbGoneApplication.java
│   │       ├── controller/
│   │       │   └── MigrationController.java
│   │       ├── config/
│   │       │   ├── CorsConfig.java
│   │       │   └── RateLimitFilter.java
│   │       ├── service/
│   │       │   ├── AnalysisService.java
│   │       │   ├── GenerationService.java
│   │       │   ├── BuildService.java
│   │       │   ├── GitHubService.java
│   │       │   ├── CostService.java
│   │       │   └── ClaudeClient.java
│   │       ├── model/
│   │       │   ├── MigrationSession.java
│   │       │   ├── AnalysisResult.java
│   │       │   ├── BuildResult.java
│   │       │   ├── ImplementResult.java
│   │       │   ├── PullRequestResult.java
│   │       │   └── RetryRequest.java
│   │       └── session/
│   │           └── SessionStore.java
│   └── pom.xml
├── frontend/                       ← React wizard
│   ├── src/
│   │   ├── components/
│   │   │   └── wizard/
│   │   │       ├── WizardShell.tsx
│   │   │       ├── Step1Upload.tsx
│   │   │       ├── Step2Analysis.tsx
│   │   │       ├── Step3Interface.tsx
│   │   │       ├── Step4Tests.tsx
│   │   │       ├── Step5Implement.tsx
│   │   │       ├── Step6PR.tsx
│   │   │       ├── CodeBlock.tsx
│   │   │       ├── CollapsibleCode.tsx
│   │   │       ├── ConfirmDialog.tsx
│   │   │       └── InfoTip.tsx
│   │   ├── api/
│   │   │   └── migrateApi.ts       ← all REST calls + mock toggle
│   │   └── App.tsx
│   └── package.json
├── docker-compose.yml
├── .env.example
└── CLAUDE.md                       ← this file
```

## API Contracts

### POST /api/migrate/analyse
```json
{ "filename": "Form1.vb", "content": "Public Class Form1..." }
→ { "sessionId": "uuid", "classes": [...], "suggestedMigrationOrder": [...], "summary": "..." }
```

### POST /api/migrate/interface
```json
{ "sessionId": "uuid", "className": "Form1" }
→ { "sessionId": "uuid", "className": "Form1", "interfaceName": "IForm1", "code": "..." }
```

### POST /api/migrate/tests
```json
{ "sessionId": "uuid", "className": "Form1" }
→ { "sessionId": "uuid", "className": "Form1", "testClassName": "Form1Tests", "code": "...", "testCount": 49 }
```

### POST /api/migrate/stub
```json
{ "sessionId": "uuid", "className": "Form1" }
→ { "sessionId": "uuid", "className": "Form1", "code": "..." }
```

### POST /api/migrate/build
```json
{ "sessionId": "uuid" }
→ { "sessionId": "uuid", "buildStatus": "RED|GREEN|ERROR", "total": 49, "passed": 0, "failed": 49, "errors": [], "failedTests": [...] }
```

### POST /api/migrate/implement
```json
{ "sessionId": "uuid", "className": "Form1", "mode": "CLAUDE|STUB" }
→ { "sessionId": "uuid", "className": "Form1", "code": "...", "mode": "CLAUDE" }
```

### POST /api/migrate/retry-implement
```json
{ "sessionId": "uuid", "className": "Form1", "failingTests": ["Add_ReturnsSum"] }
→ { "sessionId": "uuid", "className": "Form1", "code": "...", "mode": "CLAUDE" }
```

### POST /api/migrate/pr
```json
{ "sessionId": "uuid", "repoOwner": "gregochr", "repoName": "vbgone-output", "branchName": "migrate/form1" }
→ { "sessionId": "uuid", "prUrl": "https://github.com/...", "branchName": "...", "filesCommitted": [...] }
```

### GET /api/migrate/cost/{sessionId}
```json
→ { "sessionId": "uuid", "steps": [{ "step": "analyse", "model": "...", "inputTokens": 200, "outputTokens": 100, "cost": 0.002 }], "totalCost": 0.008 }
```

## Wizard Flow

```
Step 1 — Upload            User uploads .vb file or uses demo
Step 2 — Analysis          Claude Sonnet analyses classes, methods, dependencies
Step 3 — Interface         Claude Haiku generates C# interface (editable)
Step 4 — Tests + Red       Claude Sonnet generates NUnit tests, Haiku generates stub, dotnet test → RED
Step 5 — Implementation    CLAUDE (AI implements) or STUB (human implements), dotnet test → GREEN
                           Retry with Claude (max 3 attempts) if tests fail
Step 6 — Raise PR          GitHub API commits files and raises PR against vbgone-output
```

## Session State

- Sessions held in memory — `ConcurrentHashMap<String, MigrationSession>` in `SessionStore`
- Keyed by UUID sessionId returned from `/api/migrate/analyse`
- Each session stores: VB.NET source, generated interface, tests, stub, implementation, build results, token usage
- No database required for v1.0

## Build Service — .NET SDK Sidecar

Spring Boot writes generated C# files to a shared Docker volume, then executes `dotnet test` via `ProcessRunner` in the .NET SDK sidecar container. Results parsed from `.trx` XML — test counts, pass/fail status, and individual failing test names extracted for the retry feature.

## Docker Compose

### Services
- **frontend** — nginx serving React build, proxies /api/* to backend
- **backend** — Spring Boot JAR, connects to Claude API and GitHub API
- **dotnet-runner** — .NET SDK 10.0 sidecar, shared volume for generated code

### Running Locally
```bash
cp .env.example .env
# Edit .env with real ANTHROPIC_API_KEY and GITHUB_TOKEN
docker compose up --build
```

## Security

- **Cloudflare Access** — Google/GitHub OAuth required to reach the app
- **Bucket4j** — 100 requests/hour per IP on all `/api/migrate/*` endpoints, returns HTTP 429
- **CORS** — locked to vbgone.online and localhost origins
- **Input validation** — filename must be .vb or .zip, max 1MB upload
- **Secrets** — API keys via environment variables, .env in .gitignore

## Test Coverage

- **Backend**: 89 tests (JUnit 5 + Mockito + MockMvc)
- **Frontend**: 91 tests (Vitest + React Testing Library)
- **CI**: GitHub Actions — Vitest, ESLint, Prettier, Codecov

## Current Phase

### v1.0.0 — Complete
- [x] 6-step React wizard with Plex colour scheme
- [x] Spring Boot backend with all endpoints
- [x] Claude Sonnet for analysis, tests, implementation
- [x] Claude Haiku for interface and stub generation
- [x] Prompt caching for cost reduction
- [x] Token tracking and cost display (USD + GBP)
- [x] Red-Green TDD cycle with dotnet test
- [x] Retry with Claude (max 3 attempts) on failing tests
- [x] Collapsible syntax-highlighted code viewers
- [x] Confirm dialogs before API calls
- [x] InfoTip contextual help on every step
- [x] GitHub PR raised against vbgone-output
- [x] Docker Compose deployment
- [x] Cloudflare Tunnel + Access
- [x] Bucket4j rate limiting
- [x] 89 backend + 91 frontend tests
- [x] Live at vbgone.online

### Phase 2 — Planned
- [ ] Zip/solution upload (multiple .vb files)
- [ ] Migration queue UI
- [ ] Session persistence (PostgreSQL)
- [ ] GitHub OAuth (replace Cloudflare Access)
- [ ] Configurable output repo
- [ ] Retry loop improvements
- [ ] Swap ProcessBuilder for Docker Java SDK
