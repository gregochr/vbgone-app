# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased] — Phase 2 P1

### Added
- Zip/solution upload — extract all .vb files, analyse whole solution as a project
- Migration queue UI — class cards with status (Pending/In Progress/Complete/PR Raised), collapsible card list
- D3 force-directed dependency graph — interactive, status-aware node rendering, dependency-gated migration
- Per-class VB.NET source isolation — each class generates from its own source, not the combined project
- Project mode PR — single combined PR raised from queue when all classes are complete
- Code quality analysis — Claude rates each class POOR/FAIR/GOOD
- Code smells detection — God class, mixed concerns, deep nesting, magic numbers, poor naming
- Refactoring suggestions — Claude suggests specific improvements per class
- VB.NET anti-pattern detection — On Error Resume Next, GoTo, implicit type conversions, Hungarian notation
- Code Quality Issues section in Step 2 (Analysis) — shown for POOR or FAIR classes
- God class warning callout in Step 3 (Interface) — shown for HIGH complexity or POOR quality
- Complex demo file (OrderProcessor.vb) — 200+ line God class with realistic code smells
- Simple and complex demo buttons with InfoTip panels explaining each demo
- CodeQuality enum (POOR/FAIR/GOOD) in backend model
- ClassInfo record extended with codeQuality, codeSmells, refactoringSuggestions, vbAntiPatterns
- 122 backend tests (+33), 203 frontend tests (+112)

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
