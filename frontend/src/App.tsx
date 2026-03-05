import './App.css'
import logo from './assets/vbgone-logo.svg'
import { WizardShell } from './components/wizard/WizardShell'

function App() {
  return (
    <div className="app">
      <header className="app-header">
        <img src={logo} alt="VBGone" className="app-logo" />
      </header>
      <main className="app-main">
        <WizardShell />
      </main>
    </div>
  )
}

export default App
