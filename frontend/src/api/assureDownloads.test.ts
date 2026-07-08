import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  assureClassTestsUrl,
  assureTestsBundleUrl,
  downloadClassTests,
  downloadTestsBundle,
} from './migrateApi'

describe('Assure test-suite download helpers', () => {
  it('builds the per-class .cs URL under /api/assure', () => {
    expect(assureClassTestsUrl('sess-9', 'OrderService')).toBe(
      '/api/assure/sess-9/tests/OrderService',
    )
  })

  it('builds the bundle .zip URL under /api/assure', () => {
    expect(assureTestsBundleUrl('sess-9')).toBe('/api/assure/sess-9/tests.zip')
  })

  it('encodes the session id and class name', () => {
    expect(assureClassTestsUrl('a b', 'Order/Svc')).toBe('/api/assure/a%20b/tests/Order%2FSvc')
  })

  describe('anchor-triggered downloads', () => {
    let captured: { href: string; download: string } | null

    beforeEach(() => {
      captured = null
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
        this: HTMLAnchorElement,
      ) {
        captured = { href: this.href, download: this.download }
      })
    })
    afterEach(() => vi.restoreAllMocks())

    it('downloadClassTests streams the class .cs with a matching filename', () => {
      downloadClassTests('sess-9', 'OrderService')
      expect(captured?.href).toContain('/api/assure/sess-9/tests/OrderService')
      expect(captured?.download).toBe('OrderServiceTests.cs')
    })

    it('downloadTestsBundle streams the assembled MSTest project zip', () => {
      downloadTestsBundle('sess-9')
      expect(captured?.href).toContain('/api/assure/sess-9/tests.zip')
      expect(captured?.download).toBe('VBGone-Assure-Tests.zip')
    })

    it('removes the temporary anchor after triggering', () => {
      downloadClassTests('sess-9', 'OrderService')
      expect(document.querySelector('a[download]')).toBeNull()
    })
  })
})
