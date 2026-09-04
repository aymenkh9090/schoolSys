import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { CommandPalette } from './CommandPalette'
import { cn } from '@/lib/utils'

const COLLAPSED_KEY = 'smartschool.sidebarCollapsed'

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSED_KEY) === 'true')
  const [paletteOpen, setPaletteOpen] = useState(false)

  useEffect(() => {
    localStorage.setItem(COLLAPSED_KEY, String(collapsed))
  }, [collapsed])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  return (
    <div className="min-h-screen bg-brand-bg dark:bg-slate-950">
      <div className="print:hidden">
        <Topbar
          onMenuClick={() => {
            // lg breakpoint (Tailwind) : au-delà, la sidebar est toujours visible → on bascule le mode réduit.
            // En dessous, c'est un tiroir mobile → on bascule son ouverture.
            if (window.innerWidth >= 1024) setCollapsed((v) => !v)
            else setSidebarOpen((v) => !v)
          }}
          onSearchClick={() => setPaletteOpen(true)}
        />
        <Sidebar
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          collapsed={collapsed}
          onToggleCollapsed={() => setCollapsed((v) => !v)}
        />
        <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
      </div>

      <main
        className={cn(
          'pt-[var(--topbar-height)] min-h-screen transition-[margin] duration-300',
          'print:pt-0 print:ml-0 print:mr-0 print:min-h-0',
          collapsed
            ? 'lg:ml-[var(--sidebar-width-collapsed)] rtl:lg:ml-0 rtl:lg:mr-[var(--sidebar-width-collapsed)]'
            : 'lg:ml-[var(--sidebar-width)] rtl:lg:ml-0 rtl:lg:mr-[var(--sidebar-width)]'
        )}
      >
        <div className="p-6 print:p-0">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
