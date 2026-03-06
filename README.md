# VBGone

> AI-assisted migration from legacy VB.NET to modern C#, one test at a time.

**Live at [vbgone.online](https://vbgone.online)**

VBGone automates the scaffolding of VB.NET to C# migrations using Claude. Upload a `.vb` file, and VBGone will analyse the code, generate a C# interface, write a comprehensive NUnit test suite, build and run the tests, optionally implement the class with AI, and raise a Pull Request — all through a guided 6-step wizard.

## Architecture

```
Internet
    |
Cloudflare (DNS + Access + Tunnel)
    |
Mac Mini -- Docker Compose
    |-- frontend (nginx :3000)
    |       | /api/* proxy
    |-- backend (Spring Boot :8080)
    |       | Anthropic Java SDK
    |   Claude API
    |       |
    |   Generated C# --> shared Docker volume
    |       |
    |-- dotnet-runner (.NET SDK 10.0 sidecar)
    |       | dotnet test --> .trx results
    |   Results returned to frontend
    |       |
    +-- GitHub API --> PR against vbgone-output
```

## Features

- **Claude Sonnet** analyses VB.NET source — identifies classes, methods, dependencies, complexity
- **Claude Haiku** generates C# interfaces and stub implementations
- **Claude Sonnet** generates comprehensive NUnit test suites and full implementations
- **Red-Green TDD** — stub fails all tests (RED), implementation passes them (GREEN)
- **Retry with Claude** — up to 3 attempts if tests fail, sending failing test names for targeted fixes
- **Prompt caching** — system prompts cached across calls, reducing input token costs by up to 90%
- **Token tracking** — real-time cost display in USD and GBP
- **Collapsible code viewers** — syntax-highlighted with prism-react-renderer
- **GitHub PR** — commits interface, implementation, and tests to vbgone-output
- **CI pipeline** — Coverlet coverage, CodeQL security scanning, dorny/test-reporter, Stryker.NET mutation testing

## Tech Stack

| Layer | Tech |
|---|---|
| Frontend | React 19 + TypeScript + Vite |
| Backend | Spring Boot 3 / Java 21 |
| Claude | Anthropic Java SDK |
| .NET execution | .NET SDK 10.0 sidecar container |
| Rate limiting | Bucket4j (100 req/hr/IP) |
| Security | Cloudflare Access (Google/GitHub OAuth) |
| Deployment | Cloudflare Tunnel |

## Running Locally

```bash
# Clone the repo
git clone https://github.com/gregochr/vbgone-app.git
cd vbgone-app

# Set up environment variables
cp .env.example .env
# Edit .env with your real values:
#   ANTHROPIC_API_KEY=your-anthropic-api-key
#   GITHUB_TOKEN=your-github-token
#   DOTNET_RUNNER_CONTAINER=vbgone-app-dotnet-runner-1

# Start everything
docker compose up --build

# App available at http://localhost:3000
```

## Environment Variables

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude calls |
| `GITHUB_TOKEN` | GitHub personal access token for raising PRs |
| `DOTNET_RUNNER_CONTAINER` | Name of the dotnet sidecar container |

See `.env.example` for the template.

## Tests

```bash
# Frontend (91 tests)
cd frontend && npm run test

# Backend (89 tests)
cd backend && ./mvnw test
```

## Wizard Flow

1. **Upload** — Upload a `.vb` file or load the built-in demo
2. **Analysis** — Claude Sonnet identifies classes, methods, and migration order
3. **Interface** — Claude Haiku generates the C# interface (editable)
4. **Tests + Red Build** — Claude Sonnet generates NUnit tests, Haiku generates stub, `dotnet test` runs — all tests fail (RED phase)
5. **Implementation** — Choose AI or manual implementation, `dotnet test` runs — tests pass (GREEN phase)
6. **Raise PR** — Files committed and PR raised against vbgone-output

## Companion Repo

[vbgone-output](https://github.com/gregochr/vbgone-output) — receives the generated C# code with its own quality pipeline: Roslynator, Coverlet, Stryker.NET, and CodeQL.

## Phase 2 Roadmap

**P1 — Must have:**
- Zip/solution upload — extract all .vb files, analyse whole solution, build dependency graph, migration queue
- Migration queue UI — class list with status per class (Pending/In Progress/Green/PR Raised), progress bar
- Session persistence — PostgreSQL, survive page refresh, resume migration session

**P2 — Should have:**
- GitHub OAuth — replace Cloudflare Access, PR against user's own repo
- Configurable output repo
- Retry loop — 3 attempts before stub download fallback

**P3 — Nice to have:**
- Stryker results display — mutation score in UI
- Migration report — PDF summary
- Cost report — total tokens and cost across migration

## Licence

Private repository.
