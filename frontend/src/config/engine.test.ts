import { describe, it, expect } from 'vitest'
import {
  modelFor,
  modelLabel,
  modelLabelFor,
  hasOverrides,
  ROLES,
  DEFAULTS,
  MODELS,
  type ProviderId,
} from './engine'

const PROVIDERS: ProviderId[] = ['anthropic', 'copilot']

describe('modelFor', () => {
  it('returns the provider default for every role when there are no overrides', () => {
    for (const provider of PROVIDERS) {
      for (const role of ROLES) {
        expect(modelFor(provider, role, {})).toBe(DEFAULTS[provider][role])
      }
    }
  })

  it('returns the override when one is set for that role', () => {
    expect(modelFor('anthropic', 'reasoning', { reasoning: 'claude-opus-4-6' })).toBe('claude-opus-4-6')
    expect(modelFor('copilot', 'escalation', { escalation: 'openai/gpt-4.1-mini' })).toBe(
      'openai/gpt-4.1-mini',
    )
  })

  it('does not let an override for one role leak into another', () => {
    // Overriding reasoning must leave mechanical on its provider default.
    expect(modelFor('anthropic', 'mechanical', { reasoning: 'claude-opus-4-6' })).toBe(
      DEFAULTS.anthropic.mechanical,
    )
  })
})

describe('modelLabel', () => {
  it('maps a known id to its display label for both providers', () => {
    expect(modelLabel('anthropic', 'claude-opus-4-6')).toBe('Claude Opus 4.6')
    expect(modelLabel('copilot', 'openai/gpt-4.1')).toBe('GPT-4.1')
  })

  it('labels a disabled/unavailable model by its label too', () => {
    expect(modelLabel('copilot', 'openai/o3')).toBe('o3')
  })

  it('falls back to the raw id when the model is unknown', () => {
    expect(modelLabel('anthropic', 'made-up-model')).toBe('made-up-model')
  })
})

describe('modelLabelFor', () => {
  it('gives the default role label when there are no overrides', () => {
    expect(modelLabelFor('anthropic', 'escalation', {})).toBe('Claude Opus 4.6')
  })

  it('gives the override label when the role is overridden', () => {
    expect(modelLabelFor('anthropic', 'escalation', { escalation: 'claude-sonnet-4-6' })).toBe(
      'Claude Sonnet 4.6',
    )
  })

  it('falls back to the raw id when an override id is absent from MODELS', () => {
    expect(modelLabelFor('anthropic', 'reasoning', { reasoning: 'mystery-id' })).toBe('mystery-id')
  })
})

describe('hasOverrides', () => {
  it('is false for an empty override map', () => {
    expect(hasOverrides({})).toBe(false)
  })

  it('is true when any single role is overridden', () => {
    for (const role of ROLES) {
      expect(hasOverrides({ [role]: 'x' })).toBe(true)
    }
  })

  it('treats an explicit undefined value as no override (the != null guard)', () => {
    expect(hasOverrides({ reasoning: undefined })).toBe(false)
  })
})

describe('DEFAULTS/MODELS integrity (drift guard)', () => {
  it('every provider default is a real model id in that provider’s catalog and covers all roles', () => {
    for (const provider of PROVIDERS) {
      const ids = new Set(MODELS[provider].map((m) => m.id))
      for (const role of ROLES) {
        const id = DEFAULTS[provider][role]
        expect(id, `${provider}.${role} default`).toBeDefined()
        expect(ids, `${provider}.${role} default '${id}' must exist in MODELS`).toContain(id)
      }
    }
  })
})
