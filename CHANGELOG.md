# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-03-06

### Added
- Full VB.NET to C# migration pipeline via 6-step React wizard
- Claude Sonnet analysis — class identification, methods, dependencies, complexity rating
- Claude Sonnet implementation — generates full C# class from VB.NET behaviour
- Claude Haiku interface generation — extracts business logic method signatures
- Claude Haiku stub generation — implements interface with NotImplementedException
- NUnit test generation with Red-Green TDD cycle
- dotnet test execution via .NET SDK 10.0 Docker sidecar container
- .trx XML result parsing — test counts, pass/fail status, failing test names
- Retry with Claude — up to 3 attempts, sends failing test names for targeted fixes
- GitHub PR raised against vbgone-output via GitHub REST API
- Companion repo CI pipeline — Coverlet, CodeQL, CVE scan, dorny/test-reporter, Stryker.NET
- Bucket4j rate limiting — 100 requests/hour per IP
- Cloudflare Tunnel + Access for secure public deployment
- CORS locked to vbgone.online and localhost
- Input validation — .vb/.zip only, 1MB max upload
- Syntax-highlighted code viewers (prism-react-renderer)
- Collapsible code viewers with animated chevron
- Editable interface code viewer
- Confirm dialogs before every API call
- InfoTip contextual help on every wizard step
- Token tracking and cost display (USD + GBP)
- Prompt caching for cost reduction across Claude calls
- Plex media server colour scheme
- Demo file — real legacy VB.NET Windows Forms code
- Mock API toggle (VITE_USE_MOCKS) for frontend development
- Docker Compose — frontend (nginx), backend (Spring Boot), dotnet-runner (sidecar)
- 89 backend tests (JUnit 5, Mockito, MockMvc)
- 91 frontend tests (Vitest, React Testing Library)
- GitHub Actions CI — Vitest, ESLint, Prettier, Codecov
