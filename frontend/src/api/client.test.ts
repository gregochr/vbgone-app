import { describe, it, expect, beforeEach, vi } from 'vitest'

// Shared axios method spies, hoisted so the vi.mock factory can reference them.
const h = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))

vi.mock('axios', () => ({
  default: {
    create: () => ({
      post: h.post,
      get: h.get,
      defaults: { baseURL: '/api/assure' },
      interceptors: { response: { use: vi.fn() } },
    }),
  },
}))

// Imported after the mock so the module's axios.create() calls hit the mock.
import { realApi, surfaceErrorBody } from './client'

describe('surfaceErrorBody', () => {
  it('unwraps a { error } response body into an Error message', async () => {
    await expect(
      surfaceErrorBody({ response: { data: { error: 'Interface must be generated first' } } }),
    ).rejects.toThrow('Interface must be generated first')
  })

  it('passes the original error through when the body has no string error field', async () => {
    const original = { response: { data: { somethingElse: true } } }
    await expect(surfaceErrorBody(original)).rejects.toBe(original)
  })

  it('passes through when error is present but not a string', async () => {
    const original = { response: { data: { error: 500 } } }
    await expect(surfaceErrorBody(original)).rejects.toBe(original)
  })

  it('passes through a non-object body', async () => {
    const original = { response: { data: 'plain gateway error' } }
    await expect(surfaceErrorBody(original)).rejects.toBe(original)
  })

  it('passes through when there is no response at all', async () => {
    const original = {}
    await expect(surfaceErrorBody(original)).rejects.toBe(original)
  })
})

describe('realApi', () => {
  beforeEach(() => {
    h.post.mockReset()
    h.get.mockReset()
  })

  it('analyse posts filename/content to /analyse and returns the data', async () => {
    h.post.mockResolvedValue({ data: { sessionId: 's1', classes: [] } })

    const result = await realApi.analyse('Form1.vb', 'Public Class Form1')

    expect(h.post).toHaveBeenCalledWith('/analyse', {
      filename: 'Form1.vb',
      content: 'Public Class Form1',
    })
    expect(result).toEqual({ sessionId: 's1', classes: [] })
  })

  it('build posts the sessionId to /build', async () => {
    h.post.mockResolvedValue({ data: { buildStatus: 'RED' } })

    const result = await realApi.build('s1')

    expect(h.post).toHaveBeenCalledWith('/build', { sessionId: 's1' })
    expect(result).toEqual({ buildStatus: 'RED' })
  })

  it('uploadProject posts multipart form data to /upload-project', async () => {
    h.post.mockResolvedValue({ data: { sessionId: 's1', classes: [] } })

    await realApi.uploadProject(new File(['zip-bytes'], 'project.zip'))

    const [path, body, config] = h.post.mock.calls[0]
    expect(path).toBe('/upload-project')
    expect(body).toBeInstanceOf(FormData)
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
  })

  it('getMutationJob GETs the job status by id on the assure client', async () => {
    h.get.mockResolvedValue({ data: { total: 12, done: 12, status: 'DONE' } })

    const result = await realApi.getMutationJob('job-123')

    expect(h.get).toHaveBeenCalledWith('/mutation-test/job-123')
    expect(result).toEqual({ total: 12, done: 12, status: 'DONE' })
  })

  it('ingestRepo posts the url to /ingest-repo and returns the report', async () => {
    h.post.mockResolvedValue({ data: { sessionId: 's1', totals: { classes: 3 } } })

    const result = await realApi.ingestRepo('https://github.com/org/legacy-app')

    expect(h.post).toHaveBeenCalledWith('/ingest-repo', {
      url: 'https://github.com/org/legacy-app',
    })
    expect(result).toEqual({ sessionId: 's1', totals: { classes: 3 } })
  })

  it('ingestRepo surfaces a backend { error } as a rejected Error via the interceptor', async () => {
    // The interceptor (surfaceErrorBody) turns a 400 { error } body into an Error(message);
    // here we assert ingestRepo propagates a rejection rather than swallowing it.
    h.post.mockRejectedValue(
      new Error('Cloned org/docs — no .vb source files found in this repository.'),
    )

    await expect(realApi.ingestRepo('org/docs')).rejects.toThrow(
      'no .vb source files found in this repository',
    )
  })
})
