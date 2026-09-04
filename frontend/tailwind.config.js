/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          navy: '#1e3a5f',
          blue: '#3b82f6',
          /** Accent teal — titres de section, en-têtes de page, dégradés. */
          teal: '#0f766e',
          tealLight: '#14b8a6',
          bg: '#f8fafc',
          bgSecondary: '#f1f5f9',
          text: '#1e293b',
          textMuted: '#64748b',
          border: '#e2e8f0',
        },
        success: '#10b981',
        warning: '#f59e0b',
        danger: '#ef4444',
        sidebar: {
          bg: '#EDF0F7',
          text: '#374151',
          textMuted: '#6B7280',
          category: '#0F766E',
          activeBg: '#E0EAFF',
          activeBar: '#3B82F6',
          activeText: '#334155',
          hover: '#E3E7F0',
          border: '#DDE2EC',
        },
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
