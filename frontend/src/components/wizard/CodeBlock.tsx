import { useState } from 'react'
import { Highlight, themes } from 'prism-react-renderer'

interface Props {
  code: string
  editable?: boolean
  onEdit?: (code: string) => void
}

export function CodeBlock({ code, editable = false, onEdit }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(code)

  if (editable && editing) {
    return (
      <div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <button
            className="btn-plex"
            onClick={() => {
              onEdit?.(draft)
              setEditing(false)
            }}
          >
            Done
          </button>
        </div>
        <textarea
          className="code-block code-edit-textarea"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          spellCheck={false}
        />
      </div>
    )
  }

  return (
    <div>
      {editable && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
          <button
            className="btn-plex"
            onClick={() => {
              setDraft(code)
              setEditing(true)
            }}
          >
            Edit
          </button>
        </div>
      )}
      <Highlight theme={themes.vsDark} code={code} language="csharp">
        {({ style, tokens, getLineProps, getTokenProps }) => (
          <pre className="code-block" style={{ ...style, background: 'var(--bg-secondary)' }}>
            {tokens.map((line, i) => (
              <div key={i} {...getLineProps({ line })}>
                {line.map((token, key) => (
                  <span key={key} {...getTokenProps({ token })} />
                ))}
              </div>
            ))}
          </pre>
        )}
      </Highlight>
    </div>
  )
}
