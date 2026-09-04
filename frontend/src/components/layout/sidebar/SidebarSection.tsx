interface SidebarSectionProps {
  title?: string
  collapsed?: boolean
  children: React.ReactNode
}

/** Regroupe une catégorie de menus sous un petit titre (masqué en mode réduit). */
export function SidebarSection({ title, collapsed, children }: SidebarSectionProps) {
  return (
    <div>
      {title && !collapsed && (
        <p className="mb-3 px-3 text-[11px] font-bold uppercase tracking-wider text-sidebar-category dark:text-teal-400">
          {title}
        </p>
      )}
      <div className="space-y-1.5">{children}</div>
    </div>
  )
}
