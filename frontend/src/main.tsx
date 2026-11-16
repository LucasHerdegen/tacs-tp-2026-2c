import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { initTelemetry } from './telemetry.ts'
import App from './App.tsx'

initTelemetry()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
