import { useState } from 'react'
import './App.css'
import { AppHeader } from './components/AppHeader'
import { EnginePanel } from './components/EnginePanel'
import { WizardShell } from './components/wizard/WizardShell'
import { ProjectQueueView } from './components/wizard/ProjectQueueView'
import { WizardConfigProvider } from './config/WizardConfigContext'
import type { ProjectAnalysis } from './api/migrateApi'

type AppView = { type: 'wizard' } | { type: 'project'; analysis: ProjectAnalysis }

function AppContent() {
  const [view, setView] = useState<AppView>({ type: 'wizard' })

  return (
    <div className="app">
      <AppHeader />
      <main className="app-main">
        {view.type === 'wizard' ? (
          <WizardShell onProjectAnalysed={(analysis) => setView({ type: 'project', analysis })} />
        ) : (
          <ProjectQueueView
            analysis={view.analysis}
            onBackToUpload={() => setView({ type: 'wizard' })}
          />
        )}
      </main>
      <EnginePanel />
    </div>
  )
}

function App() {
  return (
    <WizardConfigProvider>
      <AppContent />
    </WizardConfigProvider>
  )
}

export default App
