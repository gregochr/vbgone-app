/**
 * Client-side validation for the "Analyse a GitHub repo" input. Mirrors the backend
 * {@code RepoIngestService.parseSlug} so obviously-bad input is caught instantly (no network),
 * matching the design prototype's synchronous-then-async split.
 *
 * The curly apostrophe (’) and em dash (—) in the messages are intentional — the copy is hi-fi.
 */
export const REPO_MESSAGES = {
  empty: 'Paste a GitHub repository URL to analyse.',
  nonGithub: 'Only github.com repositories are supported right now.',
  malformed: 'That doesn’t look like a repo URL. Try github.com/org/repo.',
} as const

export type ParseRepoResult = { slug: string } | { error: string }

/**
 * Normalise a pasted GitHub reference to an `owner/repo` slug, or return a specific error message.
 * Ported verbatim from the design prototype's `parseRepo` (same order + regexes): strip a leading
 * `https://`/`git@`, accept `org/repo` shorthand, require a github.com / www.github.com host, then
 * extract owner/repo (tolerating a `.git` suffix, trailing paths, and `#`/`?` fragments).
 */
export function parseRepo(raw: string): ParseRepoResult {
  const v = String(raw ?? '').trim()
  if (!v) return { error: REPO_MESSAGES.empty }

  const noScheme = v.replace(/^https?:\/\//i, '').replace(/^git@/i, '')

  // A bare "org/repo" (no host) is accepted immediately.
  if (/^[\w.-]+\/[\w.-]+$/.test(noScheme)) {
    const [org, repo] = noScheme.split('/')
    return { slug: `${org}/${repo.replace(/\.git$/i, '')}` }
  }

  const host = noScheme.split(/[/:]/)[0].toLowerCase()
  if (!/^(www\.)?github\.com$/.test(host)) return { error: REPO_MESSAGES.nonGithub }

  const m = noScheme.match(/github\.com[/:]([\w.-]+)\/([\w.-]+?)(?:\.git)?(?:[/#?].*)?$/i)
  if (!m) return { error: REPO_MESSAGES.malformed }

  return { slug: `${m[1]}/${m[2].replace(/\.git$/i, '')}` }
}
