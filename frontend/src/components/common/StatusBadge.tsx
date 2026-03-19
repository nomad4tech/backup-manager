interface StatusBadgeProps {
  dot: string
  label: string
}

export function StatusBadge({ dot, label }: StatusBadgeProps) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className="inline-block w-2 h-2 rounded-full flex-shrink-0"
        style={{ backgroundColor: dot }}
      />
      <span className="text-xs font-medium uppercase tracking-wide" style={{ color: dot }}>
        {label}
      </span>
    </span>
  )
}
