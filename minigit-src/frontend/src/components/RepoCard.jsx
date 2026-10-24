import { Link } from 'react-router-dom'

export default function RepoCard({ repo }) {
  return (
    <li className="py-4">
      <div className="flex items-baseline gap-2">
        <Link to={`/${repo.owner}/${repo.name}`} className="font-medium text-commit hover:underline">
          {repo.owner}/{repo.name}
        </Link>
        {repo.visibility === 'PRIVATE' && (
          <span className="rounded border border-line px-1.5 text-xs text-muted">Private</span>
        )}
      </div>
      {repo.description && <p className="mt-1 text-sm text-muted">{repo.description}</p>}
      {repo.updatedAt && (
        <p className="mt-1 text-xs text-muted">Updated {new Date(repo.updatedAt).toLocaleDateString()}</p>
      )}
    </li>
  )
}
