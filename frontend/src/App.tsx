import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './components/Toast'
import GNB from './components/GNB'
import HomePage from './pages/HomePage'
import AuthPage from './pages/AuthPage'
import AdminPage from './pages/AdminPage'
import ExpoDetailPage from './pages/ExpoDetailPage'
import ChannelCreatePage from './pages/ChannelCreatePage'
import ExpoManagePage from './pages/ExpoManagePage'
import RoundManagePage from './pages/RoundManagePage'
import HostChannelPage from './pages/HostChannelPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <GNB />
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/auth" element={<AuthPage />} />
            <Route path="/expos" element={<HomePage />} />
            <Route path="/expos/:expoId" element={<ExpoDetailPage />} />
            <Route path="/admin" element={<AdminPage />} />
            <Route path="/host/channel" element={<HostChannelPage />} />
            <Route path="/host/channel/new" element={<ChannelCreatePage />} />
            <Route path="/host/expos/new" element={<ExpoManagePage />} />
            <Route path="/host/expos/:expoId/rounds" element={<RoundManagePage />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
