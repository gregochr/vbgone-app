# Spike: VB.NET mutation testing on the Assure net

**Throwaway proof for [ADR-0001](../../docs/adr/0001-vb-mutation-testing-assure.md) (Path B).** Not production code — one script, no wiring into the app. Delete once the ADR is accepted and the real feature lands.

## What it proves

Coverage tells you code *ran*; mutation testing tells you a test would *notice a change*. This spike injects **one** operator mutation into a piece of the `OrderProcessor` demo and shows the same mutant getting **killed** by a worthy net and **surviving** a weak one — the killed-vs-survived loop, end to end, reusing the exact `dotnet test` machinery `VbCharacterisationRunner` already uses.

### The mutation

`DiscountRules.GetDiscountTier` is boundary-sensitive — exactly `100` is still `NONE`; `101` becomes `BRONZE`. One relational-boundary flip:

```
If subtotal <= 100 Then Return "NONE"      →      If subtotal < 100 Then Return "NONE"
```

Behaviour changes at **one input**: `GetDiscountTier(100)` goes `NONE → BRONZE`. Everything else is unchanged.

### The two nets, same mutant

| Run | Net | Against | Expected | Meaning |
|-----|-----|---------|----------|---------|
| 1 | faithful (asserts the 100 boundary) | **original** | green | baseline: the net faithfully pins the original |
| 2 | faithful | **mutant** | red | **KILLED** — the boundary test caught the regression |
| 3 | weak (no boundary assertion) | **mutant** | green | **SURVIVED** — a blind spot; the net missed it |

Runs 2 and 3 use the *same* mutant. Only the net differs. Both nets have coverage over `GetDiscountTier` — coverage can't tell them apart. Mutation can. That's the whole point.

## Run it

```bash
spike/vb-mutation/run.sh
```

Runner autodetect:
- **local `dotnet`** → runs directly;
- else the **.NET sidecar** (`vbgone-app-dotnet-runner-1`, override with `DOTNET_CONTAINER`) → `docker exec`;
- else → generates the projects, prints the mutant diff and the commands, and exits (what you get on a machine with neither — e.g. the dev laptop this was written on).

The first real run restores NuGet packages, so give it a moment.

## A lesson already baked in

The first draft mutated with `sed 's/subtotal <= 100/.../'` — which also hit `<= 1000` (substring match), producing **two** mutations instead of one. That regex fragility is precisely why ADR-0001 targets **Roslyn VB** (syntax-tree aware) for the production generator rather than text substitution. The spike uses a `Then`-anchored match to stay honest; production shouldn't rely on that trick.
