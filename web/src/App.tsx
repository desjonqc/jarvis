import { LoaderCircle } from 'lucide-react'
import { Navigate, Route, Routes } from 'react-router'
import { useAuth } from './auth/AuthContext'
import { Layout } from './components/Layout'
import { AlertsPage } from './pages/AlertsPage'
import { AutomationsPage } from './pages/AutomationsPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { SettingsPage } from './pages/SettingsPage'

export default function App() {
  const { status } = useAuth()

  if (status === 'loading')
    return (
      <div className="grid min-h-dvh place-items-center">
        <LoaderCircle className="size-8 animate-spin text-white/70" />
      </div>
    )

  // Not logged in : the login page, whatever the address (it is kept for after the login)
  if (status === 'anonymous') return <LoginPage />

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="automatisations" element={<AutomationsPage />} />
        <Route path="alertes" element={<AlertsPage />} />
        <Route path="reglages" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
