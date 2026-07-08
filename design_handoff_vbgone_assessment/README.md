# Handoff: VBGone — Protect "Portfolio Readiness" Assessment (MVP)

> Design handoff for **Claude Design**. Produce a high-fidelity interactive reference
> (`.dc.html` + `support.js`, same format as `design_handoff_vbgone_protect`) for the
> screens below. This document is the spec; it covers only the **new** Assessment surface.
> Reuse the existing shell, tokens, and components as-is.

> **Implementation status (this repo, 2026-07):** built and shipped as **Assure** (the mode was
> renamed Protect → Assure; the internal `bucket` enum values `net-ready` / `windows-gated` /
> `refactor-first` are unchanged). The readiness report, single-file verdict, and per-class
> Baseline → Baseline Tests flow are live. The **Test-suite download** (see the section below) is
> implemented against real backend artifact endpoints under `/api/assure` — the prototype's
> in-browser zip trick was intentionally **not** ported.

## Overview

VBGone has two modes (see `design_handoff_vbgone_protect/README.md`):
- **Migrate** — replaces legacy VB.NET with fresh tested C#/Java, one class at a time (6 steps).
- **Protect** — leaves the VB.NET running and builds a **behavioural net** around it so vulnerable dependencies can be patched without silent regressions (4 steps: Upload · Analysis · Baseline · Baseline Tests).

Protect has a hard precondition we learned the expensive way: it compiles the user's **original, unmodified VB.NET headless on a Linux CLR runner** and runs an MSTest characterisation suite against it. That runner **cannot host WinForms**. So a class is only "nettable" if its business logic is reachable as a **public, UI-free surface**. Logic welded into `Button_Click` handlers that read `TextBox.Text` can't be netted as-is.

Today the user finds this out at the *end* (the run fails with a compile error). For a real estate — a financial-services client with a large legacy portfolio — that's backwards. **This feature moves the verdict to the front: a Portfolio Readiness Assessment that triages an uploaded codebase and tells you, up front, how much of it Protect can net.**

**One-line mental model:** *the assessment is the front gate of Protect — it answers "how much of this can we net, today?" before any AI is spent.*

## The three buckets (the heart of this feature)

Every business-logic method is classified into exactly one bucket. The whole UI is organised around these three, and they map cleanly onto the existing status colours:

| Bucket | Colour token | Means | What the user does |
|---|---|---|---|
| **Net-ready** | `--green` `#34d399` | Public, no WinForms coupling — compiles & runs on the CLR **today** | Net it now (proceed) |
| **Windows-gated** | `--amber` `#e3a83c` | Pure logic, but trapped in a WinForms-referencing class — needs a Windows runner (future) | Queue for later; not nettable yet |
| **Refactor-first** | `--red` `#fb6f73` | Reads/writes controls or pops dialogs — entangled with the UI | Must be separated first (a Migrate-style job) |

The headline number is always: **"of N classes / M methods — X% net-ready · Y% Windows-gated · Z% refactor-first."**

## Where it sits in the flow

The input scale decides the shape — exactly like Migrate already branches Single File vs Project/Solution:

- **Single `.vb`** → stays the linear Protect wizard. The assessment is a **degenerate inline verdict** at Step 2 (Analysis): one class, instant bucket. Net-ready → Next enabled to Baseline. Otherwise → **Next disabled** with the bucket reason.
- **Zip / package** (a portfolio) → a dedicated **Readiness view** that replaces the per-class wizard as the "home," then drills into the net-ready classes one at a time.

**Reuse:** the portfolio Readiness view is the Protect analogue of Migrate's existing **`ProjectQueueView`** (`queue-header` + `queue-summary` stat tiles + `queue-progress` bar + dependency graph + per-class cards gated by status, drilling into the per-class wizard). Design the Readiness view by **re-skinning that pattern** — same layout DNA, bucket semantics instead of migration status. Minimal net-new UI.

### The demos (use these as the canonical states)
- **Simple demo** (`Form1.vb`, a WinForms calculator with all logic in `Button_Click`) → **0% net-ready, 100% refactor-first** → the gate **blocks**, Next disabled, with "this is UI-entangled — the logic has to be separated first."
- **Protect complex demo** (the clean `OrderProcessor`) → **100% net-ready** → proceed.
- **Realistic mixed sample** (NEW — see Open Questions) → e.g. **40% net-ready · 35% Windows-gated · 25% refactor-first** → the breakdown is the hero; proceed with the net-ready subset.

---

## Screens to design

### A. Upload (Step 1) — minimal delta
Identical to the existing Protect Upload. Only the framing shifts for a project input: the footer pill already notes the headless precondition ("Point it at a business-logic class — UI-coupled forms can't run headless"). Keep it. No structural change.

### B. Assessing (busy state)
A scan is fast and **static** (no AI) — design a busy state that conveys "scanning the estate," ideally with a live count ("Scanned 312 / 480 classes…"). Reuse the spinner/busy-row pattern. This is not an AI run-card; it's a local pass, so **no model chip, no cost**.

### C. Readiness report (the hero screen) — portfolio input
Mirror `ProjectQueueView`'s frame:

1. **Header** — kicker `STEP 02 · READINESS`, title **"Protect readiness"**, subtitle = a plain-language verdict, e.g. *"68 of 142 classes can be netted today. 41 unlock with a Windows runner; 33 need refactoring first."*
2. **Headline breakdown** — the single most important element. A **horizontal stacked bar** segmented green / amber / red by share, with the three counts + percentages called out as **three stat tiles** beneath (reuse `queue-stat` tiles): Net-ready (green), Windows-gated (amber), Refactor-first (red). Designer's call on bar-vs-donut, but the three-way split must read at a glance.
3. **Confidence note** — a quiet `--text-dim` mono line: *"Static estimate — heuristic classification, no code sent to the model. Drill into any class to see the call."* (This matters for a regulated client: nothing left the tenant to produce this number.)
4. **Bucket filter chips** — All · Net-ready · Windows-gated · Refactor-first (counts on each), filtering the table.
5. **Per-class table** — one row per class (reuse the class-card / row styling):
   - class name (Geist Mono), file path (`--text-dim`)
   - a **bucket tag** (coloured like the complexity `badge`s) — green/amber/red
   - method rollup: e.g. `5 methods · 3 net-ready`
   - a one-line **reason** (`--text-muted`), e.g. *"inherits Form; CalcTotal is pure but private"*
   - **action**: Net-ready → primary **"Net this class →"**; Windows-gated → disabled chip **"Needs Windows runner"**; Refactor-first → disabled chip **"Refactor first"**. A row expands to show its per-method buckets + reasons.
6. **Footer / proceed** — primary CTA **"Net the {X} ready classes →"** (enters the queue). As classes get netted, show progress (reuse `queue-progress`).

### D. The gate (blocked state) — 0% net-ready
When nothing is net-ready (the simple demo), replace the proceed CTA with a prominent **blocked panel** (amber, the inverted-notice styling from Protect's Baseline step):
- Heading: **"Nothing nettable yet"**
- Body: *"Every class here couples its logic to the WinForms UI, so there's no headless surface to pin. Protect can't net it as-is — the business logic has to be separated into UI-free classes first (that's a Migrate job), or wait for the Windows runner for the methods that are pure but UI-bound."*
- The breakdown bar still shows (it's all red/amber). **No proceed.** Next disabled.

### E. Net-ready queue + per-class netting
Picking "Net this class" drills into the **existing Protect netting flow** for that one class — Baseline (pin surface) → Baseline Tests (generate suite + run against the original VB → green/red net). This is the current Protect Steps 3–4 in "project mode," exactly as Migrate drills `ProjectQueueView` → `WizardShell(projectMode)`. On green, the class is marked done in the queue and you return to pick the next. **No new design needed here** beyond the queue's done-state — reuse what Protect already has.

### F. Single-file inline verdict (Step 2, non-portfolio)
For a single `.vb`, Step 2 shows a compact **verdict card** instead of the full report: the bucket tag + reason. Net-ready → green card, Next enabled. Windows-gated / Refactor-first → amber/red card explaining why, **Next disabled**. (This is the front-loaded version of the compile-failure hint that exists today.)

---

## Test-suite download

Once a class is assured, its generated baseline test suite is downloadable — individually per class
or as a bundle. Three touchpoints, all on the readiness report (the hub the queue returns to after
each class is assured):

1. **Per-class** — a row whose baseline has gone **green** shows the **✓ Assured** chip plus a
   secondary **↓ tests** button that downloads that one class's `{Class}Tests.cs`. Only classes with
   a recorded (green) suite get the button; a class left early or quarantined still shows **✓ Assured**
   but no download, and gated/tangled classes have no suite at all.
2. **Mid-flow bulk** — the *Assurance progress* card gains a **↓ Download all tests (N)** button as
   soon as ≥1 class's baseline is green (`N` = number of downloadable suites, not the queue length).
3. **Completion** — when every ready class has been through the queue, the proceed panel flips from
   the "keep going" CTA to **All ready classes assured** with a primary **↓ Download all tests (.zip)**
   CTA (shown only when at least one suite is downloadable).

**Bundle contents** (the zip, assembled server-side):
- `tests/{Class}Tests.cs` — one MSTest file per assured class: the real suite that ran green against
  the untouched VB.NET, served **as-is** (not regenerated from mock data).
- `VBGone.Assure.Tests.csproj` — a `net8.0` MSTest project (Microsoft.NET.Test.Sdk + MSTest
  TestAdapter/Framework) with a placeholder comment to reference the user's original VB.NET project.
- `README.md` — generated: class list, per-file test counts, and `dotnet test` run instructions.

**Wired to real backend artifacts — not an in-browser zip.** The suites already exist server-side as
artifacts of the Baseline-Tests step (retained per class on the session the moment they go green).
The buttons hit:
- Per class → `GET /api/assure/{sessionId}/tests/{className}` → the `.cs` file (attachment).
- Bundle → `GET /api/assure/{sessionId}/tests.zip` → the assembled MSTest project, streamed.

The bulk buttons are hidden until ≥1 class's baseline is green, and a row's **↓ tests** appears only
once that class's baseline is green — the frontend gates on the set of green-assured classes (a subset
of the queue), so it never offers a download the backend has no artifact for.

> **Design-vs-repo note:** the design bundle names these endpoints under `/api/protect` and the
> artifacts `VBGone.Protect.Tests.*`; this repo uses its established `/api/assure` base path and
> `VBGone.Assure.Tests.*` names to stay consistent with the Protect → Assure rebrand.

---

## Copy (exact)

- Buckets: **Net-ready**, **Windows-gated**, **Refactor-first**.
- Net-ready tag tooltip: *"Public, UI-free — runs on the CLR today."*
- Windows-gated tag tooltip: *"Pure logic inside a WinForms class — needs a Windows runner."*
- Refactor-first tag tooltip: *"Touches controls or dialogs — separate the logic first."*
- Confidence line: *"Static estimate · heuristic classification · no code sent to the model."*
- Proceed CTA: *"Net the {X} ready classes →"*.
- Blocked heading / body: as in screen D.
- Single-file net-ready: *"Net-ready — this class has a headless business-logic surface."*
- Single-file blocked: *"Can't net this as-is — {reason}."*

## Colours & tokens

**No new tokens.** Reuse the redesign palette: `--accent #6d6af2`, `--green #34d399`, `--amber #e3a83c`, `--red #fb6f73`, surfaces `--bg-base #0c0d10` / `--bg-panel #15171b` / `--bg-code #0e1013`, borders `--border #23262d`, text `--text #eef1f6` / `--text-muted #9aa1ad` / `--text-dim #6b727e` / `--text-faint #5b616b`, fonts Geist / Geist Mono. Bucket tags reuse the existing `badge` shape; bucket colours as in the table above.

## Reuse (from existing app + prior handoffs)
- **`ProjectQueueView`** — the frame to re-skin: header, stat tiles (`queue-stat`), progress bar (`queue-progress`), per-class rows, dependency graph.
- **Protect Baseline / Baseline Tests** — the per-class netting the queue drills into (unchanged).
- Dropzone + file-chosen card, stepper, badges, spinner/busy-row, the amber inverted-notice (for the blocked state), the header (MODE/TARGET/ENGINE) — all unchanged.

---

## Data shape (so design & implementation align)

The assessment is a backend pass returning a `ReadinessReport`. Design to this shape:

```jsonc
{
  "sessionId": "uuid",
  "totals": {
    "classes": 142, "methods": 1180,
    "netReady": 68, "windowsGated": 41, "refactorFirst": 33,   // class counts
    "methodNetReady": 540, "methodWindowsGated": 360, "methodRefactorFirst": 280
  },
  "confidence": "static",                         // static | llm-refined  (MVP = static)
  "classes": [{
    "name": "OrderService", "file": "Services/OrderService.vb",
    "bucket": "net-ready",                        // worst-case rollup of its methods
    "reason": "public, no WinForms references",
    "methods": [
      { "name": "CalculateTotal", "visibility": "public",
        "bucket": "net-ready", "reason": "params in, value out; no control access" },
      { "name": "ComputeFee", "visibility": "private",
        "bucket": "windows-gated", "reason": "pure, but class inherits Form (needs reflection)" }
    ]
  }]
}
```

Classification is **static-first** (heuristics over parsed VB — WinForms imports / `Inherits Form` / `Handles X.Click` / control field access / `MsgBox`). The existing `looksUiCoupled()` heuristic is the seed, lifted to **per-method** granularity. An optional Claude pass to sharpen the Windows-gated↔Refactor-first boundary is **deferred** (`confidence: "llm-refined"`) — design the confidence line to accommodate both.

## Scope

**MVP (design this):**
- Zip/package input → static method-level classifier → Readiness report (breakdown + per-class table + drill-in) → gate (0% net-ready blocks; >0% proceeds) → net the ready classes via the reused queue.
- Single-file inline verdict at Step 2.

**Deferred (note, don't design):**
- **GitHub-URL ingestion** — a regulated client won't hand over a token; realistic paths are a local checkout / read-only GitHub App / running inside their tenant. MVP uses zip upload, which sidesteps the auth/egress question entirely.
- **LLM-refined bucketing** — static is enough for a defensible headline.
- **The Windows runner itself** — Windows-gated classes are *reported and queued*, but netting them is a later capability (Azure Windows worker). The bucket exists so the report is honest and the runner ROI is quantified.

## Codebase touch-points (for the implementer, post-design)
- `frontend/src/App.tsx` — route Protect + portfolio input to a new `ProtectReadinessView` (parallel to `ProjectQueueView`).
- New `frontend/src/components/wizard/protect/ProtectReadinessView.tsx` — re-skin of `ProjectQueueView`.
- `frontend/src/components/wizard/Step2Analysis.tsx` (or a new Step) — single-file inline verdict.
- `frontend/src/config/engine.ts` — `looksUiCoupled` → per-method classifier helper.
- Backend — a new static-analysis assessment endpoint (e.g. `POST /api/protect/assess`) returning `ReadinessReport`; no AI call in the MVP path.
- `frontend/src/api/migrateApi.ts` — `assess()` client + mock returning the three demo reports (0% / 100% / mixed).

## Open questions (for design / product)
1. **Headline viz** — stacked bar + tiles (recommended) vs donut vs grouped bars?
2. **Windows-gated rows** — show as actionable "queue for the Windows runner (coming)" vs simply greyed with a tooltip? (Leaning: greyed + tooltip in MVP, since the runner doesn't exist yet.)
3. **A realistic mixed demo sample is required** — both current demos are extremes (0% / 100%). The pitch value is the *breakdown*, so we need a synthetic-but-believable mixed estate (≈40/35/25). Design should mock against that.
4. **Method vs class as the headline unit** — methods are the honest unit (a class is often mixed); classes are the navigable unit. Recommended: headline in **methods**, table navigated by **class**.
