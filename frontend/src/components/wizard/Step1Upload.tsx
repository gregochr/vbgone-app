import { useState, useRef, useCallback, useEffect } from 'react'
import JSZip from 'jszip'
import type { WizardState } from './WizardShell'
import {
  DEMO_VB_CONTENT,
  DEMO_FILENAME,
  DEMO_COMPLEX_CONTENT,
  DEMO_COMPLEX_FILENAME,
  DEMO_PROJECT_FILES,
  uploadProject,
} from '../../api/migrateApi'
import type { ProjectAnalysis } from '../../api/migrateApi'
import { InfoTip } from './InfoTip'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  onProjectAnalysed?: (analysis: ProjectAnalysis) => void
}

type UploadMode = 'single' | 'project'

export function Step1Upload({ state, update, onReady, onProjectAnalysed }: Props) {
  const [mode, setMode] = useState<UploadMode>('single')
  const [zipFile, setZipFile] = useState<File | null>(null)
  const [zipFiles, setZipFiles] = useState<{ path: string; size: number }[]>([])
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const zipInputRef = useRef<HTMLInputElement>(null)

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
      if (!file) return
      if (mode === 'single') {
        handleFile(file)
      } else if (file.name.toLowerCase().endsWith('.zip')) {
        handleZipSelected(file)
      }
    },
    [handleFile, mode],
  )

  const handleZipSelected = (file: File) => {
    setZipFile(file)
    setUploadError(null)
    // We can't read zip contents client-side without a library,
    // so show the file name and size. The server will extract files.
    setZipFiles([{ path: file.name, size: file.size }])
  }

  const handleUploadProject = async () => {
    if (!zipFile) return
    setUploading(true)
    setUploadError(null)
    try {
      const analysis = await uploadProject(zipFile)
      onProjectAnalysed?.(analysis)
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  const loadDemo = () => {
    update({ filename: DEMO_FILENAME, content: DEMO_VB_CONTENT })
    onReady()
  }

  const loadComplexDemo = () => {
    update({ filename: DEMO_COMPLEX_FILENAME, content: DEMO_COMPLEX_CONTENT })
    onReady()
  }

  const loadDemoProject = async () => {
    setUploading(true)
    setUploadError(null)
    try {
      const zip = new JSZip()
      DEMO_PROJECT_FILES.forEach((f) => {
        zip.file(f.path, f.content)
      })
      const blob = await zip.generateAsync({ type: 'blob' })
      const demoFile = new File([blob], 'DemoProject.zip', { type: 'application/zip' })
      setZipFile(demoFile)
      setZipFiles(
        DEMO_PROJECT_FILES.map((f) => ({
          path: f.path,
          size: new Blob([f.content]).size,
        })),
      )
      const analysis = await uploadProject(demoFile)
      onProjectAnalysed?.(analysis)
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  const clearFile = () => {
    update({
      filename: '',
      content: '',
      analysis: null,
      interfaceResult: null,
      tests: null,
      stubResult: null,
      redBuild: null,
      implementResult: null,
      greenBuild: null,
      prResult: null,
    })
  }

  const clearZip = () => {
    setZipFile(null)
    setZipFiles([])
    setUploadError(null)
  }

  const totalZipSize = zipFiles.reduce((sum, f) => sum + f.size, 0)

  return (
    <div>
      <h2 className="step-title">Upload VB.NET Source</h2>
      <p className="step-subtitle">
        {mode === 'single'
          ? 'Upload a single .vb class file. VBGone will migrate the business logic to modern, tested C#.'
          : 'Upload a .zip containing your VB.NET solution. VBGone will analyse all classes, build a dependency graph, and guide you through migrating each class in the optimal order.'}
      </p>

      {/* Mode toggle */}
      {!state.filename && !zipFile && (
        <div className="upload-mode-toggle">
          <button
            className={`upload-mode-tab ${mode === 'single' ? 'active' : ''}`}
            onClick={() => setMode('single')}
          >
            Single File
          </button>
          <button
            className={`upload-mode-tab ${mode === 'project' ? 'active' : ''}`}
            onClick={() => setMode('project')}
          >
            Project / Solution
          </button>
        </div>
      )}

      {/* Single file mode */}
      {mode === 'single' && !zipFile && (
        <>
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
                  Load Demo File (Simple)
                </button>
                <InfoTip label="About the simple demo">
                  <p>
                    <strong>
                      The demo uses a genuine VB.NET Windows Forms project sourced directly from
                      GitHub.
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
              <div className="demo-button-group" style={{ marginTop: 10 }}>
                <button className="btn-plex" onClick={loadComplexDemo}>
                  Load Demo File (Complex)
                </button>
                <InfoTip label="About the complex demo">
                  <p>
                    <strong>
                      This demo loads a realistic legacy VB.NET &ldquo;God class&rdquo;
                    </strong>{' '}
                    — a single file that handles order processing, refunds, reporting, validation,
                    database access, email sending, and file I/O all in one place. It is the kind of
                    class that accumulates over years of maintenance by multiple developers with no
                    code review.
                  </p>
                  <p>
                    <strong>Code smells Claude will identify:</strong>
                  </p>
                  <ul>
                    <li>
                      <strong>God class</strong> — too many responsibilities in a single 200+ line
                      file
                    </li>
                    <li>
                      <strong>Magic numbers</strong> — hardcoded tax rates, discount thresholds,
                      shipping costs
                    </li>
                    <li>
                      <strong>Deep nesting</strong> — 5 levels of nested If statements for discount
                      calculation
                    </li>
                    <li>
                      <strong>Mixed concerns</strong> — business logic, UI, database, file I/O, and
                      email in one class
                    </li>
                    <li>
                      <strong>Poor naming</strong> — single-letter variables (n, a, q, t, r),
                      Hungarian notation (txtN, btnS)
                    </li>
                    <li>
                      <strong>On Error Resume Next</strong> — silently swallows all exceptions
                    </li>
                    <li>
                      <strong>GoTo statements</strong> — used for flow control instead of If/ElseIf
                    </li>
                    <li>
                      <strong>SQL injection</strong> — string concatenation for SQL queries
                    </li>
                    <li>
                      <strong>Copy-paste duplication</strong> — discount and shipping logic
                      duplicated across methods
                    </li>
                    <li>
                      <strong>Hardcoded paths and connection strings</strong> — C:\OrderLog,
                      PROD-DB-01
                    </li>
                  </ul>
                  <p>
                    This is a <strong>much harder migration target</strong> than the simple demo.
                    Claude will need to separate the business logic from the infrastructure concerns
                    and generate a comprehensive test suite covering all the branching paths.
                  </p>
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
                <button className="btn-plex" onClick={clearFile}>
                  Choose a different file
                </button>
              </div>
              <div className="code-block">{state.content}</div>
            </div>
          )}
        </>
      )}

      {/* Project / solution mode */}
      {mode === 'project' && !state.filename && (
        <>
          {!zipFile && (
            <>
              <div
                className="upload-area"
                onClick={() => zipInputRef.current?.click()}
                onDrop={handleDrop}
                onDragOver={(e) => e.preventDefault()}
              >
                <input
                  ref={zipInputRef}
                  type="file"
                  accept=".zip"
                  hidden
                  onChange={(e) => {
                    const file = e.target.files?.[0]
                    if (file) handleZipSelected(file)
                  }}
                />
                <div className="upload-icon">{'\uD83D\uDCE6'}</div>
                <div className="upload-label">Drop a .zip file here or click to browse</div>
                <div className="upload-hint">Accepts .zip files containing .vb source files</div>
              </div>

              <div style={{ textAlign: 'center', marginTop: 20 }}>
                <div className="demo-button-group">
                  <button className="btn-plex" onClick={loadDemoProject}>
                    Load demo project
                  </button>
                  <InfoTip label="About this demo">
                    <p>
                      <strong>The demo project contains four VB.NET classes</strong> that form a
                      small dependency graph — ideal for demonstrating VBGone's multi-file analysis
                      and migration queue.
                    </p>
                    <p>
                      <strong>ValidationHelper</strong> and <strong>StringHelper</strong> are leaf
                      nodes with no dependencies. <strong>DateHelper</strong> depends on
                      ValidationHelper. <strong>Calculator</strong> depends on both StringHelper and
                      DateHelper.
                    </p>
                    <p>
                      VBGone will analyse all four files together, build a dependency graph, and
                      suggest a migration order starting with the simplest, most self-contained
                      classes.
                    </p>
                  </InfoTip>
                </div>
              </div>
            </>
          )}

          {zipFile && !uploading && !uploadError && (
            <div className="zip-summary">
              <div className="zip-header">
                <div className="zip-icon">{'\uD83D\uDCE6'}</div>
                <div>
                  <div className="file-name">{zipFile.name}</div>
                  <div className="zip-meta">
                    {zipFiles.length} file{zipFiles.length !== 1 ? 's' : ''} &middot;{' '}
                    {formatBytes(totalZipSize)}
                  </div>
                </div>
                <button className="btn-plex" onClick={clearZip} style={{ marginLeft: 'auto' }}>
                  Choose different file
                </button>
              </div>
              <div className="zip-file-tree">
                {zipFiles.map((f) => (
                  <div className="zip-file-entry" key={f.path}>
                    <span className="zip-file-icon">{'\uD83D\uDCC4'}</span>
                    <span className="zip-file-path">{f.path}</span>
                    <span className="zip-file-size">{formatBytes(f.size)}</span>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
                <button className="btn-plex" onClick={handleUploadProject}>
                  Analyse Project
                </button>
              </div>
            </div>
          )}

          {uploading && (
            <div className="info-card" style={{ textAlign: 'center' }}>
              <span className="spinner" />
              <span className="loading-text">
                Uploading and analysing project with Claude Sonnet...
              </span>
            </div>
          )}

          {uploadError && (
            <div className="info-card" style={{ borderColor: 'var(--red)' }}>
              <h3 style={{ color: 'var(--red)', marginBottom: 8 }}>Upload Failed</h3>
              <p style={{ color: 'var(--grey)' }}>{uploadError}</p>
              <button className="btn-plex" onClick={clearZip} style={{ marginTop: 12 }}>
                Try again
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
