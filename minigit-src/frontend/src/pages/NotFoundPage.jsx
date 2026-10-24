import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="max-w-md">
      <h1 className="text-2xl font-semibold tracking-tight">Page not found</h1>
      <p className="mt-2 text-sm text-muted">This page doesn't exist, or it's a private repository you don't have access to.</p>
      <Link to="/explore" className="btn-secondary mt-6">Explore public repositories</Link>
    </div>
  )
}
