import type { ReactNode } from 'react'

export const STEPS: { label: string; tip: ReactNode }[] = [
  {
    label: 'Upload',
    tip: (
      <>
        <p>
          <strong>VBGone migrates legacy VB.NET business logic to modern, tested C#</strong> — one
          class at a time. Upload a <strong>.vb</strong> source file or a <strong>.zip</strong>{' '}
          containing multiple files to get started.
        </p>
        <p>
          Not sure what to expect? Use the <strong>Load Demo File</strong> button to load a real
          example — genuine legacy Windows Forms code from GitHub, with business logic embedded in
          button click handlers and no separation of concerns. Exactly the kind of code VBGone was
          built to modernise.
        </p>
        <p>
          VBGone will analyse your code, generate a C# interface and a comprehensive NUnit test
          suite, build and run the tests, and raise a Pull Request against your output repository —
          all with a full quality pipeline automatically triggered on every PR.
        </p>
      </>
    ),
  },
  {
    label: 'Analysis',
    tip: (
      <>
        <p>
          <strong>Claude Sonnet</strong> reads your VB.NET source and builds a complete picture of
          the codebase before a single line of C# is written. It identifies every class, its public
          methods, and dependencies between classes.
        </p>
        <p>
          Crucially, it looks past the Windows Forms noise — button click handlers, form load
          events, UI wiring — and extracts the <strong>pure business logic</strong> underneath. Each
          class is rated LOW, MEDIUM, or HIGH complexity based on method count, branching logic, and
          dependency depth.
        </p>
        <p>
          A suggested migration order is produced, starting with the simplest, most self-contained
          classes — building confidence and test coverage before tackling the complex ones.
        </p>
        <p>
          This analysis becomes the foundation for every subsequent step.{' '}
          <strong>Review it carefully</strong> — if the summary looks wrong, the generated tests
          will be wrong too.
        </p>
      </>
    ),
  },
  {
    label: 'Interface',
    tip: (
      <>
        <p>
          A C# interface is the cornerstone of the{' '}
          <strong>
            <a
              href="https://martinfowler.com/bliki/StranglerFigApplication.html"
              target="_blank"
              rel="noopener noreferrer"
            >
              Strangler Fig migration pattern
            </a>
          </strong>
          . By defining the contract first — the method signatures, parameter types, and return
          types — VBGone creates a seam between the legacy VB.NET system and its modern C#
          replacement.
        </p>
        <p>
          Both implementations satisfy the same interface, which means the legacy code can continue
          running in production while the C# replacement is built and tested alongside it. Only when
          the tests are green and the team is confident does the legacy implementation get retired.
        </p>
        <p>
          <strong>Claude Haiku</strong> handles interface generation — it is a mechanical task,
          extracting method signatures from the analysis and expressing them in idiomatic C#. All UI
          concerns are stripped: no Windows Forms dependencies, no event handler signatures, no UI
          types. What remains is pure business logic expressed as a clean contract.
        </p>
        <p>
          <strong>Review the interface carefully and edit if needed</strong> — every subsequent step
          is built on this foundation.
        </p>
      </>
    ),
  },
  {
    label: 'Tests',
    tip: (
      <>
        <p>
          <strong>This is the most important step in the migration.</strong> Claude Sonnet generates
          a comprehensive NUnit test suite based on the behaviour of the VB.NET source — covering
          the happy path, edge cases, boundary values, and error conditions like divide by zero and
          null inputs.
        </p>
        <p>
          The tests are written against the C# interface, not the implementation — so they will be
          valid regardless of how the class is eventually implemented.
        </p>
        <p>
          <strong>Claude Haiku</strong> then generates a stub implementation: a class that satisfies
          the interface but throws <strong>NotImplementedException</strong> for every method.{' '}
          <strong>.NET test</strong> is run immediately. Every test will fail.{' '}
          <strong>This is intentional — and it is a good sign.</strong>
        </p>
        <p>
          The failing tests are the migration contract. They define exactly what correct behaviour
          looks like in C#, verified against the original VB.NET logic. No implementation is correct
          unless all of these tests pass. This is the <strong>Red phase</strong> of
          Red-Green-Refactor TDD. The stub and the failing tests together are more valuable than any
          amount of documentation.
        </p>
      </>
    ),
  },
  {
    label: 'Implement',
    tip: (
      <>
        <p>
          <strong>You have two paths.</strong> The <strong>Claude path</strong> asks Claude Sonnet
          to generate a full C# implementation based on the VB.NET behaviour. It writes idiomatic
          modern C# — expression-bodied members, pattern matching, nullable reference types where
          appropriate. <strong>.NET test</strong> runs immediately and all tests should pass green.
          This is fast, useful for straightforward classes, and gives you a solid starting point
          even if you intend to refine the implementation afterwards.
        </p>
        <p>
          The <strong>Stub path</strong> downloads the stub and lets you implement the class
          yourself in Rider or Visual Studio. You make the tests go green. This is the recommended
          path for production migrations — the developer who implements the class reads the original
          VB.NET, understands the business logic, and owns every line of the C# replacement.
        </p>
        <p>
          The tests keep you honest: if they pass, the behaviour is correct. If they fail, something
          is wrong. Either path produces the same output — a C# class that satisfies the interface
          and passes the test suite. The difference is who writes it and how well they understand it
          afterwards.
        </p>
      </>
    ),
  },
  {
    label: 'Raise PR',
    tip: (
      <>
        <p>
          VBGone commits three files to a new branch in your output repository and raises a{' '}
          <strong>Pull Request</strong>: the C# interface, the implementation, and the NUnit test
          suite. The branch name reflects the class being migrated, making it easy to track progress
          across a large codebase.
        </p>
        <p>
          The moment the PR is raised, the <strong>GitHub Actions CI pipeline</strong> triggers
          automatically. <strong>Roslynator</strong> analyses the C# for outdated patterns and
          suggests modern alternatives. <strong>Coverlet</strong> measures test coverage and reports
          to Codecov, ensuring high coverage from day one. <strong>Stryker.NET</strong> runs
          mutation testing nightly — introducing deliberate bugs and verifying the tests catch them,
          measuring test suite quality rather than just coverage percentage. <strong>CodeQL</strong>{' '}
          scans for security vulnerabilities.
        </p>
        <p>
          Review the PR, address any pipeline findings, and merge when everything is green. The
          legacy VB.NET class can now be retired — its behaviour is preserved in C#, proven by
          tests, and protected by a quality pipeline that will catch regressions on every future
          change.
        </p>
        <p>
          <strong>No Claude call at this step</strong> — this is pure GitHub API.
        </p>
      </>
    ),
  },
]

// Assure mode renders four steps. Upload reuses the Migrate dropzone; Analysis is the
// same engine with a forensic persona; Baseline and Baseline Tests are Assure-only.
export const ASSURE_STEPS: { label: string; tip: ReactNode }[] = [
  {
    label: 'Upload',
    tip: (
      <>
        <p>
          <strong>Assure builds a safety net of tests around your legacy VB.NET</strong> — it
          changes nothing. Upload a <strong>.vb</strong> file or a <strong>.zip</strong> to start.
        </p>
        <p>
          Reach for Assure when the job is "we have CVEs in our libraries and no test coverage to
          patch safely" — not "we want off VB.NET". The safety net lets you upgrade a vulnerable
          dependency and prove nothing else changed.
        </p>
      </>
    ),
  },
  {
    label: 'Readiness',
    tip: (
      <>
        <p>
          A <strong>static, no-AI</strong> pass — nothing leaves your tenant. Before any model is
          spent, VBGone checks whether the source has{' '}
          <strong>business logic that runs on its own</strong>: a class that compiles and runs on
          the CLR without the WinForms screen.
        </p>
        <p>
          A portfolio is bucketed into <strong>Ready to assure</strong>,{' '}
          <strong>Needs Windows runner</strong>, and <strong>Tangled in the UI</strong>. Only ready
          classes can be assured today.
        </p>
      </>
    ),
  },
  {
    label: 'Baseline',
    tip: (
      <>
        <p>
          Assure doesn't build a new, cleaner version — we keep your original as-is.{' '}
          <strong>Claude Haiku</strong> records the concrete class's actual public surface, run
          against your real code, including the behaviours flagged as bugs.
        </p>
        <p>
          <strong>Green means unchanged, not correct.</strong> The baseline locks in current
          behaviour so that patching a dependency surfaces any change.
        </p>
      </>
    ),
  },
  {
    label: 'Baseline Tests',
    tip: (
      <>
        <p>
          <strong>Claude Sonnet</strong> writes a set of MSTest tests that check the real behaviour
          — the actual errors and converted values — then runs them against your original VB.NET on
          the CLR.
        </p>
        <p>
          The polarity flips from Migrate: <strong>green is the goal</strong>. A red result means a
          test is wrong — it expects behaviour the code doesn't actually have,{' '}
          <strong>not that your code is broken</strong>. Auto-repair rewrites just that test to
          match what the code does; once green, you have a safety net: upgrade a dependency, re-run,
          and any failure is a real change to investigate.
        </p>
      </>
    ),
  },
]

export const NEXT_TITLES = [
  'Analyse your VB.NET with Claude Sonnet',
  'Generate the C# interface with Claude Haiku',
  'Generate tests and run the red build',
  'Choose your implementation path',
  'Raise a Pull Request against vbgone-output',
]

export const BACK_TITLES = [
  '',
  'Back to upload',
  'Back to analysis',
  'Back to interface',
  'Back to tests',
  'Back to implementation',
]
