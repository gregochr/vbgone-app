import { useState } from 'react'
import './App.css'
import logo from './assets/vbgone-logo.svg'
import { WizardShell } from './components/wizard/WizardShell'
import { ProjectQueueView } from './components/wizard/ProjectQueueView'
import type { ProjectAnalysis } from './api/migrateApi'

type AppView = { type: 'wizard' } | { type: 'project'; analysis: ProjectAnalysis }

function App() {
  const [view, setView] = useState<AppView>({ type: 'wizard' })

  return (
    <div className="app">
      <header className="app-header">
        <img src={logo} alt="VBGone" className="app-logo" />
      </header>
      <main className="app-main">
        {view.type === 'wizard' ? (
          <WizardShell
            onProjectAnalysed={(analysis) => setView({ type: 'project', analysis })}
          />
        ) : (
          <ProjectQueueView
            analysis={view.analysis}
            onBackToUpload={() => setView({ type: 'wizard' })}
          />
        )}
      </main>
    </div>
  )
}

export default App
