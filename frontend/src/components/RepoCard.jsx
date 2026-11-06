import { Link } from 'react-router-dom'

// One row of a repository list (dashboard, explore). Looks like a GitHub repo list item.
export default function RepoCard({ repo }) {
  return (
    <li className="py-4">
      <div className="flex items-center gap-2">
        <RepoIcon className="shrink-0 text-muted" />
        <Link to={`/${repo.owner}/${repo.name}`} className="text-[17px] font-semibold text-link hover:underline">
          {repo.owner}/{repo.name}
        </Link>
        <span className="rounded-full border border-line px-2 py-px text-xs font-medium text-muted">
          {repo.visibility === 'PRIVATE' ? 'Private' : 'Public'}
        </span>
      </div>
      {repo.description && <p className="mt-1 text-sm text-muted">{repo.description}</p>}
      {repo.updatedAt && (
        <p className="mt-2 text-xs text-muted">Updated {new Date(repo.updatedAt).toLocaleDateString()}</p>
      )}
    </li>
  )
}

// Book icon, also used by ActivityOverview.
export function RepoIcon({ className = '' }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" className={className} aria-hidden>
      <path d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8ZM5 12.25a.25.25 0 0 1 .25-.25h3.5a.25.25 0 0 1 .25.25v3.25a.25.25 0 0 1-.4.2l-1.45-1.087a.249.249 0 0 0-.3 0L5.4 15.7a.25.25 0 0 1-.4-.2Z" />
    </svg>
  )
}
