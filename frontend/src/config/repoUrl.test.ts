import { describe, it, expect } from 'vitest'
import { parseRepo, REPO_MESSAGES } from './repoUrl'

/** Narrow the union to the success/error branch for terse assertions. */
const slugOf = (r: ReturnType<typeof parseRepo>) => ('slug' in r ? r.slug : undefined)
const errorOf = (r: ReturnType<typeof parseRepo>) => ('error' in r ? r.error : undefined)

describe('parseRepo', () => {
  it('rejects an empty / whitespace-only field with the empty message', () => {
    expect(errorOf(parseRepo(''))).toBe(REPO_MESSAGES.empty)
    expect(errorOf(parseRepo('   '))).toBe(REPO_MESSAGES.empty)
  })

  it('accepts a bare org/repo shorthand', () => {
    expect(slugOf(parseRepo('octocat/hello-world'))).toBe('octocat/hello-world')
  })

  it('accepts a full https github.com URL', () => {
    expect(slugOf(parseRepo('https://github.com/org/legacy-app'))).toBe('org/legacy-app')
  })

  it('accepts the www.github.com host', () => {
    expect(slugOf(parseRepo('https://www.github.com/org/legacy-app'))).toBe('org/legacy-app')
  })

  it('accepts a git@ SSH-style prefix', () => {
    expect(slugOf(parseRepo('git@github.com:org/legacy-app'))).toBe('org/legacy-app')
  })

  it('strips a trailing .git suffix (URL and shorthand)', () => {
    expect(slugOf(parseRepo('https://github.com/org/legacy-app.git'))).toBe('org/legacy-app')
    expect(slugOf(parseRepo('org/legacy-app.git'))).toBe('org/legacy-app')
  })

  it('tolerates trailing paths and #/? fragments', () => {
    expect(slugOf(parseRepo('https://github.com/org/legacy-app/tree/main/src'))).toBe(
      'org/legacy-app',
    )
    expect(slugOf(parseRepo('https://github.com/org/legacy-app#readme'))).toBe('org/legacy-app')
    expect(slugOf(parseRepo('https://github.com/org/legacy-app?tab=readme'))).toBe('org/legacy-app')
  })

  it('rejects a non-github host with the non-github message', () => {
    expect(errorOf(parseRepo('https://gitlab.com/org/legacy-app'))).toBe(REPO_MESSAGES.nonGithub)
    expect(errorOf(parseRepo('https://bitbucket.org/org/repo'))).toBe(REPO_MESSAGES.nonGithub)
  })

  it('rejects a github URL with no repo path as malformed', () => {
    expect(errorOf(parseRepo('https://github.com'))).toBe(REPO_MESSAGES.malformed)
    expect(errorOf(parseRepo('https://github.com/'))).toBe(REPO_MESSAGES.malformed)
  })

  it('treats a bare two-segment string as org/repo shorthand (design quirk: runs before host check)', () => {
    // `github.com/org` fits the shorthand pattern, so it is read as owner="github.com", repo="org"
    // rather than rejected — matching the prototype's parseRepo ordering exactly.
    expect(slugOf(parseRepo('github.com/org'))).toBe('github.com/org')
  })
})
