# VBGone App — Claude Code Context

> *VBGone — AI-assisted migration from legacy VB.NET to modern C#, one test at a time.*

## What This Project Is

VBGone is a Spring Boot + React application that automates the scaffolding of VB.NET to C# migrations. It uses the Anthropic Java SDK to call Claude, which analyses VB.NET source and generates C# interfaces, NUnit test suites, and stub implementations. The developer then implements the C# to make the tests pass.

## Companion Repo

**vbgone-output** — the generated C# code lives here, with its own .NET quality pipeline (Roslynator, Coverlet, Stryker, CodeQL). VBGone raises PRs against this repo programmatically via the GitHub API.

## Architecture

```
React Frontend (Wizard UI)
        ↓ REST
Spring Boot Backend (Java 21 / Spring Boot 3)
        ↓ Anthropic Java SDK
Claude API
        ↓
Generated C# files written to shared Docker volume
        ↓
.NET SDK sidecar container runs dotnet test
        ↓
Results returned to frontend
        ↓
GitHub API raises PR against vbgone-output
```

## Tech Stack

| Layer | Tech |
|---|---|
| Frontend | React + TypeScript |
| Backend | Spring Boot 3 / Java 21 |
| Claude integration | Anthropic Java SDK |
| Build tool | Maven |
| Containerisation | Docker Compose |
| .NET execution | .NET SDK 8 sidecar container |
| GitHub integration | GitHub REST API (JGit or OkHttp) |

## Project Structure

```
vbgone-app/
├── backend/                        ← Spring Boot application
│   ├── src/main/java/
│   │   └── com/vbgone/
│   │       ├── VbGoneApplication.java
│   │       ├── controller/
│   │       │   └── MigrationController.java
│   │       ├── service/
│   │       │   ├── AnalysisService.java
│   │       │   ├── GenerationService.java
│   │       │   ├── BuildService.java
│   │       │   └── GitHubService.java
│   │       ├── model/
│   │       │   ├── MigrationSession.java
│   │       │   ├── AnalysisResult.java
│   │       │   ├── BuildResult.java
│   │       │   └── PullRequestResult.java
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
│   │   │       └── Step6PR.tsx
│   │   ├── api/
│   │   │   └── migrateApi.ts       ← all REST calls in one place
│   │   └── App.tsx
│   └── package.json
├── docker-compose.yml
└── CLAUDE.md                       ← this file
```

## API Contracts

### POST /api/migrate/analyse
```json
// Request
{
  "filename": "ArithmeticOperations.vb",
  "content": "Public Class ArithmeticOperations..."
}

// Response
{
  "sessionId": "uuid-1234",
  "classes": [
    {
      "name": "ArithmeticOperations",
      "methods": ["Add", "Subtract", "Multiply", "Divide", "Modulus"],
      "dependencies": [],
      "complexity": "LOW"
    }
  ],
  "suggestedMigrationOrder": ["ArithmeticOperations"],
  "summary": "One class found with 5 arithmetic methods. No dependencies. Good candidate for migration."
}
```

### POST /api/migrate/interface
```json
// Request
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations"
}

// Response
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations",
  "interfaceName": "IArithmeticOperations",
  "code": "public interface IArithmeticOperations..."
}
```

### POST /api/migrate/tests
```json
// Request
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations"
}

// Response
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations",
  "testClassName": "ArithmeticOperationsTests",
  "code": "using NUnit.Framework;\n[TestFixture]...",
  "testCount": 30
}
```

### POST /api/migrate/stub
```json
// Request
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations"
}

// Response
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations",
  "code": "public class ArithmeticOperations : IArithmeticOperations..."
}
```

### POST /api/migrate/build
```json
// Request
{
  "sessionId": "uuid-1234"
}

// Response
{
  "sessionId": "uuid-1234",
  "buildStatus": "RED",
  "total": 30,
  "passed": 0,
  "failed": 30,
  "errors": []
}
```

### POST /api/migrate/implement
```json
// Request
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations",
  "mode": "CLAUDE"
}

// Response
{
  "sessionId": "uuid-1234",
  "className": "ArithmeticOperations",
  "code": "public class ArithmeticOperations : IArithmeticOperations...",
  "mode": "CLAUDE"
}
```

### POST /api/migrate/pr
```json
// Request
{
  "sessionId": "uuid-1234",
  "repoOwner": "chrisgregory",
  "repoName": "vbgone-output",
  "branchName": "migrate/arithmetic-operations"
}

// Response
{
  "sessionId": "uuid-1234",
  "prUrl": "https://github.com/chrisgregory/vbgone-output/pull/1",
  "branchName": "migrate/arithmetic-operations",
  "filesCommitted": [
    "ArithmeticOperations/IArithmeticOperations.cs",
    "ArithmeticOperations/ArithmeticOperations.cs",
    "ArithmeticOperations.Tests/ArithmeticOperationsTests.cs"
  ]
}
```

## Wizard Flow

```
Step 1 — Upload
        POST /api/migrate/analyse
        User uploads .vb file or .zip

Step 2 — Analysis
        Review Claude's summary — classes, methods, dependencies, migration order

Step 3 — Interface
        POST /api/migrate/interface
        Review generated C# interface — edit if needed

Step 4 — Tests + Red Build
        POST /api/migrate/tests
        POST /api/migrate/stub
        POST /api/migrate/build  ← explicit call by wizard
        Show 🔴 N tests failing — expected, stub is empty

Step 5 — Implementation
        User chooses: STUB (human implements) or CLAUDE (AI implements)
        POST /api/migrate/implement
        POST /api/migrate/build  ← explicit call by wizard
        CLAUDE → 🟢 N tests passing
        STUB   → 🔴 still failing — download and implement in Rider

Step 6 — Raise PR
        POST /api/migrate/pr
        Show PR link → vbgone-output GitHub Actions pipeline triggers automatically
```

## Session State

- Sessions held in memory — `ConcurrentHashMap<String, MigrationSession>` in `SessionStore`
- Keyed by UUID sessionId returned from `/api/migrate/analyse`
- Each session stores: VB.NET source, generated interface, tests, stub, implementation, build results
- No database required for PoC

## Build Service — .NET SDK Sidecar

Spring Boot executes `dotnet test` in a .NET SDK sidecar container via `ProcessBuilder`:

```java
ProcessBuilder pb = new ProcessBuilder(
    "docker", "exec", "dotnet-runner",
    "dotnet", "test", "/workspace/" + sessionId,
    "--logger", "trx;LogFileName=results.trx"
);
```

Results parsed from `.trx` XML file on shared volume. This is the PoC approach — production would use Docker Java SDK.

## Docker Compose

### Services
```yaml
services:
  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    depends_on:
      - backend

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - GITHUB_TOKEN=${GITHUB_TOKEN}
    depends_on:
      - dotnet-runner
    volumes:
      - generated-code:/workspace

  dotnet-runner:
    image: mcr.microsoft.com/dotnet/sdk:10.0
    volumes:
      - generated-code:/workspace
    command: tail -f /dev/null

volumes:
  generated-code:
```

### Dockerfile — Backend
Multi-stage build:
- Stage 1: Maven + Java 21 — build the JAR
- Stage 2: Java 21 JRE — run the JAR
- Exposes port 8080

### Dockerfile — Frontend
Multi-stage build:
- Stage 1: Node — npm ci && npm run build
- Stage 2: nginx — serves the React build
- nginx.conf proxies /api/* to backend:8080
- Exposes port 3000

### Environment Variables
- Copy .env.example to .env and populate before running
- .env is in .gitignore — never committed
- .env.example is committed — shows required variables without values

### Running Locally
```bash
# First time setup
cp .env.example .env
# Edit .env with real values

# Start everything
docker compose up --build

# Stop everything
docker compose down
```

### .NET SDK Version
Use mcr.microsoft.com/dotnet/sdk:10.0 — matches Mac Mini installation.
## Deployment Intent

VBGone will be publicly accessible via Cloudflare Tunnel from a Mac Mini (Intel, 32GB RAM) running Docker Desktop. A cheap domain (£1 via Fasthosts) will be pointed at Cloudflare, which tunnels to the Mac Mini without exposing inbound ports — the same pattern used for PhotoCast.

```
Internet
    ↓
Cloudflare (DNS + Access + Tunnel)
    ↓
Mac Mini — Docker Compose
    ├── frontend:3000
    ├── backend:8080
    └── dotnet-runner (sidecar)
```

## Security

VBGone calls the Anthropic API on every migration — unprotected public access would expose API costs to abuse. The following security layers are applied.

**Security is implemented at the end of Phase 3, immediately before first public deployment. Do not implement security during Phase 1 or Phase 2 — the app runs locally only during those phases.**

**Exception: add Bucket4j as a Maven dependency during Phase 2 so it is ready to configure in Phase 3.**

### Cloudflare Access (outer layer)
- Free tier Cloudflare Access wraps the entire app
- Google or GitHub OAuth login required to reach the app
- No code changes to Spring Boot required
- Configured in Cloudflare dashboard

### Rate Limiting — Bucket4j (inner layer)
- Bucket4j applied to all `/api/migrate/*` endpoints in Spring Boot
- Limits per IP — 10 migrations per hour for PoC
- Returns HTTP 429 if limit exceeded
- Belt and braces protection even if Cloudflare Access is bypassed

```java
// Applied via Spring Boot filter or annotation
@RateLimited(capacity = 10, refillTokens = 10, refillPeriod = 1, refillPeriodUnit = HOURS)
@PostMapping("/api/migrate/analyse")
public ResponseEntity<AnalysisResult> analyse(...) { }
```

### CORS
- Spring Boot CORS configured to allow only the VBGone frontend domain
- Prevents cross-origin API abuse

### Secrets Management
- `ANTHROPIC_API_KEY` injected via environment variable — never hardcoded
- GitHub personal access token for PR creation — injected via environment variable
- `.env` file excluded from Git via `.gitignore`
- Cloudflare Access credentials managed in Cloudflare dashboard

### What We Are NOT Doing for PoC
- No user authentication within the app itself — Cloudflare Access handles this
- No persistent user sessions or database
- No audit logging — production hardening phase

## Development Approach

- **API first** — contracts defined before implementation
- **React wizard built against mocks first** — backend wired in once FE flow is agreed
- **ProcessBuilder for PoC** — Docker Java SDK is the production hardening path
- **sessionId** ties all steps together — stateless REST, stateful server

## Current Phase

### Phase 1 — React Wizard ✅ Complete
- [x] Wizard shell with step navigation
- [x] Back/Next buttons — not visible on Step 1 / Step 6 respectively
- [x] Step 1 — file upload + Load Demo File button
- [x] Step 2 — analysis display
- [x] Step 3 — interface code viewer
- [x] Step 4 — tests + 🔴 build result
- [x] Step 5 — implementation choice + 🟢 build result
- [x] Step 6 — PR link display
- [x] Plex media server colour scheme
- [x] VBGone logo
- [x] InfoTip with real VB.NET demo source attribution
- [x] 60 Vitest tests — all passing
- [x] GitHub Actions CI — Vitest, ESLint, Prettier, Codecov

### Phase 2 — Spring Boot Backend
- [x] MigrationController with all endpoints
- [x] AnalysisService — Anthropic Java SDK integration
- [x] GenerationService — interface, tests, stub, implement
- [x] BuildService — ProcessBuilder + .trx parsing
- [x] GitHubService — commit and raise PR
- [x] SessionStore — in memory ConcurrentHashMap
- [x] Add Bucket4j Maven dependency — configure in Phase 3
- [x] Wire React to real backend

### Phase 3 — Docker Compose + First Public Deployment
- [ ] backend/Dockerfile — multi-stage Java 21 build
- [ ] frontend/Dockerfile — multi-stage Node/nginx build
- [ ] nginx.conf — serves React, proxies /api/* to backend
- [ ] docker-compose.yml — all three services + shared volume
- [ ] .env.example — template with placeholder values
- [ ] .env in .gitignore confirmed
- [ ] docker compose up --build — single command start
- [ ] End-to-end test — real Claude calls, Red/Green build, PR raised
- [ ] Security — implement before going public:
  - [ ] Bucket4j rate limiting
  - [ ] CORS locked to vbgone domain
  - [ ] Cloudflare Tunnel configured
  - [ ] Cloudflare Access — Google or GitHub OAuth
- [ ] £1 domain purchased via Fasthosts
- [ ] DNS pointed at Cloudflare
- [ ] First public URL shared

### Phase 4 — Hardening
- [ ] Swap ProcessBuilder for Docker Java SDK
- [ ] Error handling throughout
- [ ] CI pipeline for vbgone-app itself
- [ ] Support for zip upload of multiple .vb files