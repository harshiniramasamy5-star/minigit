import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import Placeholder from './components/Placeholder'
import DashboardPage from './pages/DashboardPage'
import ExplorePage from './pages/ExplorePage'
import GraphPage from './pages/GraphPage'
import LoginPage from './pages/LoginPage'
import RepoLayout from './pages/RepoLayout'
import NotFoundPage from './pages/NotFoundPage'
import NewRepoPage from './pages/NewRepoPage'
import RecycleBinPage from './pages/RecycleBinPage'
import RegisterPage from './pages/RegisterPage'
import RepoSettingsPage from './pages/RepoSettingsPage'

// Route map = who plugs in where. Swap a <Placeholder> for your real page in your feature branch.
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
        <Route path="explore" element={<ExplorePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="new" element={<ProtectedRoute><NewRepoPage /></ProtectedRoute>} />
        <Route path="recycle-bin" element={<ProtectedRoute><RecycleBinPage /></ProtectedRoute>} />

        {/* Repo pages are visible to guests for public repos; the API decides access. */}
        <Route path=":owner/:repo" element={<RepoLayout />}>
          <Route index element={<Placeholder title="Files + working copy" owner="Arisha" useCase="U3" />} />
          <Route path="commits" element={<Placeholder title="History, diff, restore" owner="Arisha" useCase="U4" />} />
          <Route path="commits/:hash" element={<Placeholder title="Commit detail" owner="Arisha" useCase="U4" />} />
          <Route path="graph" element={<GraphPage />} />
          <Route path="branches" element={<Placeholder title="Branches + undo" owner="Aakriti" useCase="U5, U10" />} />
          <Route path="merges/:sessionId" element={<Placeholder title="Resolve conflicts" owner="Aakriti" useCase="U6, U7" />} />
          <Route path="pulls" element={<Placeholder title="Pull requests" owner="Arisha" useCase="U8" />} />
          <Route path="pulls/:number" element={<Placeholder title="Pull request" owner="Arisha" useCase="U8" />} />
          <Route path="settings" element={<ProtectedRoute><RepoSettingsPage /></ProtectedRoute>} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
