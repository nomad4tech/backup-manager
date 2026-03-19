/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'bg-base': '#0f1117',
        'bg-surface': '#1a1d27',
        'bg-elevated': '#212533',
        'border-default': '#2a2d3a',
        'border-subtle': '#1f2230',
        'accent': '#3b82f6',
        'accent-hover': '#2563eb',
        'success': '#22c55e',
        'error': '#ef4444',
        'warning': '#f59e0b',
        'text-primary': '#e2e8f0',
        'text-secondary': '#94a3b8',
        'text-muted': '#475569',
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
    },
  },
  plugins: [],
}
