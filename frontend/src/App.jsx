import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import DashboardPage from './pages/DashboardPage'
import ExplorePage from './pages/ExplorePage'
import GraphPage from './pages/GraphPage'
import LoginPage from './pages/LoginPage'
import RepoLayout from './pages/RepoLayout'
import NotFoundPage from './pages/NotFoundPage'
import BranchesPage from './pages/BranchesPage'
import CodePage from './pages/CodePage'
import ConflictPage from './pages/ConflictPage'
import CommitDetailPage from './pages/CommitDetailPage'
import CommitsPage from './pages/CommitsPage'
import NewPullPage from './pages/NewPullPage'
import NewRepoPage from './pages/NewRepoPage'
import PullDetailPage from './pages/PullDetailPage'
import PullsPage from './pages/PullsPage'
import RecycleBinPage from './pages/RecycleBinPage'
import RegisterPage from './pages/RegisterPage'
import RepoSettingsPage from './pages/RepoSettingsPage'

// Route map. Repo pages are nested under :owner/:repo.
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
          <Route index element={<CodePage />} />
          <Route path="commits" element={<CommitsPage />} />
          <Route path="commits/:hash" element={<CommitDetailPage />} />
          <Route path="graph" element={<GraphPage />} />
          <Route path="branches" element={<BranchesPage />} />
          <Route path="merges/:sessionId" element={<ProtectedRoute><ConflictPage /></ProtectedRoute>} />
          <Route path="pulls" element={<PullsPage />} />
          <Route path="pulls/new" element={<ProtectedRoute><NewPullPage /></ProtectedRoute>} />
          <Route path="pulls/:number" element={<PullDetailPage />} />
          <Route path="settings" element={<ProtectedRoute><RepoSettingsPage /></ProtectedRoute>} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
