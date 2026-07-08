#!/usr/bin/env bash
#
# THROWAWAY SPIKE — VB.NET mutation testing on the Assure net (ADR-0001, Path B).
#
# Proves the killed-vs-survived loop end to end with ONE operator mutation:
#   - Class under test: DiscountRules.GetDiscountTier (lifted from the OrderProcessor demo).
#   - Mutation: a single relational-boundary flip  `<= 100`  ->  `< 100`.
#     Behaviour changes ONLY at the boundary value (subtotal = 100): NONE -> BRONZE.
#   - Faithful net (asserts the 100 boundary)  -> mutant KILLED   (test goes red)  = net is worthy here
#   - Weak net     (no boundary assertion)     -> mutant SURVIVED (stays green)    = blind spot exposed
#
# It reuses the SAME machinery as VbCharacterisationRunner: a net8.0 VB project +
# an MSTest project that references it, run with `dotnet test`. RED (non-zero) = killed.
#
# Runner autodetect: local `dotnet` -> use it; else the .NET sidecar container -> docker exec;
# else generate everything, show the mutant diff, and print the commands to run on the box.
#
# Usage:   spike/vb-mutation/run.sh
# Env:     DOTNET_CONTAINER (default: vbgone-app-dotnet-runner-1)
set -uo pipefail

CONTAINER="${DOTNET_CONTAINER:-vbgone-app-dotnet-runner-1}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/vbmut.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

bold() { printf '\033[1m%s\033[0m\n' "$1"; }
rule() { printf '%s\n' "------------------------------------------------------------"; }

# ── Detect a .NET runner ─────────────────────────────────────────────────────
RUNNER=none
if command -v dotnet >/dev/null 2>&1; then
  RUNNER=local
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$CONTAINER"; then
  RUNNER=docker
fi

# ── Generate the VB project (the "original, unmodified" code under test) ──────
mkdir -p "$WORK/Rules.Vb" "$WORK/Net.Tests"

cat > "$WORK/Rules.Vb/Rules.vbproj" <<'EOF'
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <RootNamespace></RootNamespace>
    <Nullable>disable</Nullable>
  </PropertyGroup>
</Project>
EOF

# GetDiscountTier is boundary-sensitive: exactly 100 is still NONE; 101 becomes BRONZE.
cat > "$WORK/Rules.Vb/Rules.vb" <<'EOF'
Public Class DiscountRules
    Public Function GetDiscountTier(subtotal As Double) As String
        If subtotal <= 0 Then Return "NONE"
        If subtotal <= 100 Then Return "NONE"
        If subtotal <= 500 Then Return "BRONZE"
        If subtotal <= 1000 Then Return "SILVER"
        Return "GOLD"
    End Function
End Class
EOF

# ── The MSTest project (mirrors VbCharacterisationRunner's BASELINE_CSPROJ) ───
cat > "$WORK/Net.Tests/Net.Tests.csproj" <<'EOF'
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <Nullable>disable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <IsPackable>false</IsPackable>
    <IsTestProject>true</IsTestProject>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.11.1" />
    <PackageReference Include="MSTest.TestAdapter" Version="3.6.1" />
    <PackageReference Include="MSTest.TestFramework" Version="3.6.1" />
  </ItemGroup>
  <ItemGroup>
    <Using Include="Microsoft.VisualStudio.TestTools.UnitTesting" />
  </ItemGroup>
  <ItemGroup>
    <ProjectReference Include="../Rules.Vb/Rules.vbproj" />
  </ItemGroup>
</Project>
EOF

# Faithful net — pins the 100 boundary, so it CATCHES the mutation.
FAITHFUL_NET='[TestClass]
public class DiscountRulesBaselineTests
{
    private readonly DiscountRules sut = new DiscountRules();

    [TestMethod] public void Tier_Zero_IsNone()       => Assert.AreEqual("NONE",   sut.GetDiscountTier(0));
    [TestMethod] public void Tier_Fifty_IsNone()      => Assert.AreEqual("NONE",   sut.GetDiscountTier(50));
    [TestMethod] public void Tier_Exactly100_IsNone() => Assert.AreEqual("NONE",   sut.GetDiscountTier(100)); // boundary
    [TestMethod] public void Tier_101_IsBronze()      => Assert.AreEqual("BRONZE", sut.GetDiscountTier(101));
    [TestMethod] public void Tier_300_IsBronze()      => Assert.AreEqual("BRONZE", sut.GetDiscountTier(300));
    [TestMethod] public void Tier_1500_IsGold()       => Assert.AreEqual("GOLD",   sut.GetDiscountTier(1500));
}'

# Weak net — same code runs green, but never asserts the boundary, so the mutant SLIPS THROUGH.
WEAK_NET='[TestClass]
public class DiscountRulesBaselineTests
{
    private readonly DiscountRules sut = new DiscountRules();

    [TestMethod] public void Tier_Fifty_IsNone()  => Assert.AreEqual("NONE",   sut.GetDiscountTier(50));
    [TestMethod] public void Tier_300_IsBronze()  => Assert.AreEqual("BRONZE", sut.GetDiscountTier(300));
    [TestMethod] public void Tier_1500_IsGold()   => Assert.AreEqual("GOLD",   sut.GetDiscountTier(1500));
}'

# ── Apply ONE mutation: relational-boundary flip on line "subtotal <= 100" ────
# NB: anchor on the trailing " Then" so we don't also hit "subtotal <= 1000" (a substring
# match would). This regex fragility is exactly why ADR-0001 targets Roslyn VB for production.
ORIGINAL_VB="$(cat "$WORK/Rules.Vb/Rules.vb")"
MUTANT_VB="$(printf '%s' "$ORIGINAL_VB" | sed 's/subtotal <= 100 Then/subtotal < 100 Then/')"

bold "VB mutation-testing spike (ADR-0001, Path B)"
rule
echo "Mutation operator : relational boundary  <=  ->  <"
echo "Site              : GetDiscountTier, guard for tier NONE"
echo "  before:  If subtotal <= 100 Then Return \"NONE\""
echo "  after :  If subtotal <  100 Then Return \"NONE\""
echo "Effect            : GetDiscountTier(100)  NONE  ->  BRONZE   (changes only at the boundary)"
rule

# ── The run helper: returns 0=GREEN(all pass) 1=RED(a test failed) 2=ERROR(build) ─
write_net() { printf '%s\n' "$1" > "$WORK/Net.Tests/BaselineTests.cs"; }
write_vb()  { printf '%s\n' "$1" > "$WORK/Rules.Vb/Rules.vb"; }

run_net() {
  local out code
  if [ "$RUNNER" = local ]; then
    out="$(cd "$WORK" && dotnet test Net.Tests --nologo 2>&1)"; code=$?
  else
    docker exec "$CONTAINER" sh -c 'rm -rf /tmp/vbmut && mkdir -p /tmp/vbmut' >/dev/null 2>&1
    docker cp "$WORK/." "$CONTAINER:/tmp/vbmut" >/dev/null 2>&1
    out="$(docker exec "$CONTAINER" dotnet test /tmp/vbmut/Net.Tests --nologo 2>&1)"; code=$?
  fi
  LAST_SUMMARY="$(printf '%s' "$out" | grep -E 'Passed!|Failed!' | tail -1 | sed 's/^[[:space:]]*//')"
  if printf '%s' "$out" | grep -qiE 'Build FAILED|error BC|error CS'; then return 2; fi
  return $code
}

verdict() { case "$1" in 0) echo GREEN;; 1) echo RED;; *) echo ERROR;; esac; }

if [ "$RUNNER" = none ]; then
  echo "No .NET runner here (no local 'dotnet', sidecar '$CONTAINER' not running)."
  echo "The projects were generated under: $WORK  (deleted on exit — copy it if you want it)."
  echo
  echo "To see killed vs survived, run this script on the box with the sidecar up, or with dotnet:"
  echo "  1) faithful net + ORIGINAL  -> expect Passed  (baseline: net is faithful)"
  echo "  2) faithful net + MUTANT    -> expect Failed  => KILLED   (boundary test catches it)"
  echo "  3) weak net     + MUTANT    -> expect Passed  => SURVIVED (blind spot at the boundary)"
  echo
  echo "The one thing to notice: runs 2 and 3 use the SAME mutant. Only the net differs."
  echo "Coverage can't tell them apart — both nets execute GetDiscountTier — but mutation can."
  exit 0
fi

bold "Runner: $RUNNER${RUNNER:+ (${CONTAINER})}"
echo "(first run restores NuGet packages; give it a moment)"
rule

# 1) Baseline sanity — faithful net against the ORIGINAL must be green, else the net isn't faithful.
write_vb "$ORIGINAL_VB"; write_net "$FAITHFUL_NET"
run_net; B=$?
printf '1. faithful net + ORIGINAL : %-6s  %s\n' "$(verdict $B)" "$LAST_SUMMARY"
if [ "$B" -ne 0 ]; then
  echo "   Baseline is not green — the net doesn't faithfully pin the original. Fix that before mutating."
  exit 1
fi

# 2) Faithful net + MUTANT -> should go RED = mutant killed.
write_vb "$MUTANT_VB"
run_net; K=$?
printf '2. faithful net + MUTANT   : %-6s  %s\n' "$(verdict $K)" "$LAST_SUMMARY"

# 3) Weak net + MUTANT -> should stay GREEN = mutant survived.
write_net "$WEAK_NET"
run_net; S=$?
printf '3. weak net     + MUTANT   : %-6s  %s\n' "$(verdict $S)" "$LAST_SUMMARY"

rule
bold "Result"
[ "$K" -eq 1 ] && echo "KILLED   by the faithful net  — a boundary assertion caught the injected regression."
[ "$K" -ne 1 ] && echo "(run 2 did not go red as expected: $(verdict $K)) — inspect output above."
[ "$S" -eq 0 ] && echo "SURVIVED the weak net         — same mutant, no boundary test, so the net missed it."
[ "$S" -ne 0 ] && echo "(run 3 did not stay green as expected: $(verdict $S)) — inspect output above."
rule
echo "Takeaway: both nets have coverage over GetDiscountTier, yet only the faithful one is 'worthy'."
echo "Mutation score is what tells them apart. Multiply this loop over an operator set = Path B."
