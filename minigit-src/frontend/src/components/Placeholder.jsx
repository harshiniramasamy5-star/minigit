// Mount point for a teammate's page. Replace the route element in App.jsx with the real page.
export default function Placeholder({ title, owner, useCase }) {
  return (
    <section className="rounded-lg border border-dashed border-line bg-white p-8">
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-1 text-sm text-muted">{useCase} · built by {owner}. Replace this in <code className="font-mono">App.jsx</code>.</p>
    </section>
  )
}
