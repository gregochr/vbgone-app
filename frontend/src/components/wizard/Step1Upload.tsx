import { useRef, useCallback, useEffect } from 'react'
import type { WizardState } from './WizardShell'
import { DEMO_VB_CONTENT, DEMO_FILENAME } from '../../api/migrateApi'
import { InfoTip } from './InfoTip'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step1Upload({ state, update, onReady }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (state.filename) {
      onReady()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleFile = useCallback(
    (file: File) => {
      const reader = new FileReader()
      reader.onload = (e) => {
        const content = e.target?.result as string
        update({ filename: file.name, content })
        onReady()
      }
      reader.readAsText(file)
    },
    [update, onReady],
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      const file = e.dataTransfer.files[0]
      if (file) handleFile(file)
    },
    [handleFile],
  )

  const loadDemo = () => {
    update({ filename: DEMO_FILENAME, content: DEMO_VB_CONTENT })
    onReady()
  }

  return (
    <div>
      <h2 className="step-title">Upload VB.NET Source</h2>
      <p className="step-subtitle">Upload a .vb file to begin the migration analysis.</p>

      <div
        className={`upload-area ${state.filename ? 'has-file' : ''}`}
        onClick={() => inputRef.current?.click()}
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".vb"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) handleFile(file)
          }}
        />
        {state.filename ? (
          <>
            <div className="upload-icon">{'\u2705'}</div>
            <div className="file-name">{state.filename}</div>
            <div className="upload-hint">Click to replace</div>
          </>
        ) : (
          <>
            <div className="upload-icon">{'\uD83D\uDCC1'}</div>
            <div className="upload-label">Drop a .vb file here or click to browse</div>
            <div className="upload-hint">Accepts .vb files</div>
          </>
        )}
      </div>

      {!state.filename && (
        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <div className="demo-button-group">
            <button className="btn-plex" onClick={loadDemo}>
              Load demo file
            </button>
            <InfoTip label="About this demo">
              <p>
                <strong>
                  The demo uses a genuine VB.NET Windows Forms project sourced directly from GitHub.
                </strong>{' '}
                It was not prepared, cleaned up, or modified in any way for this demonstration —
                what you see is real legacy code, exactly as it was committed by its author.
              </p>
              <p>
                The <strong>Form1.vb</strong> file is a good example of a common legacy pattern:
                business logic embedded directly inside UI event handlers, with no separation of
                concerns. This is precisely the kind of code VBGone is designed to analyse and
                migrate.
              </p>
              <p>
                <strong>Notable characteristics Claude will identify:</strong>
              </p>
              <ul>
                <li>Four arithmetic operations buried inside button click handlers</li>
                <li>No separation between UI and business logic</li>
                <li>Integer-only arithmetic via Int() casting — no decimal support</li>
                <li>No divide by zero protection</li>
                <li>No unit tests</li>
              </ul>
              <dl className="infotip-meta">
                <dt>Source Repository</dt>
                <dd>
                  <a
                    href="https://github.com/Abhijith14/VB.NET-PROJECTS"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    github.com/Abhijith14/VB.NET-PROJECTS
                  </a>
                </dd>
                <dt>Author</dt>
                <dd>Abhijith14</dd>
                <dt>Created</dt>
                <dd>30 March 2021</dd>
                <dt>Last Updated</dt>
                <dd>18 August 2022</dd>
              </dl>
            </InfoTip>
          </div>
        </div>
      )}

      {state.filename && (
        <div style={{ marginTop: 24 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 8,
            }}
          >
            <h3>Preview</h3>
            <button
              className="btn-plex"
              onClick={() => {
                update({
                  filename: '',
                  content: '',
                  analysis: null,
                  interfaceResult: null,
                  tests: null,
                  redBuild: null,
                  implementResult: null,
                  greenBuild: null,
                  prResult: null,
                })
              }}
            >
              Choose a different file
            </button>
          </div>
          <div className="code-block">{state.content}</div>
        </div>
      )}
    </div>
  )
}
