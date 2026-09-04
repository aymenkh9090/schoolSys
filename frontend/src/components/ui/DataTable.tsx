import { cn } from '@/lib/utils'

export interface Column<T> {
  key: string
  header: string
  render?: (row: T) => React.ReactNode
  className?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  data: T[]
  keyField: keyof T
  loading?: boolean
  emptyMessage?: string
  className?: string
}

export function DataTable<T>({
  columns,
  data,
  keyField,
  loading,
  emptyMessage = 'Aucune donnée',
  className,
}: DataTableProps<T>) {
  return (
    <div className={cn('w-full overflow-x-auto rounded-xl border border-brand-border dark:border-slate-700', className)}>
      <table className="w-full text-sm">
        <thead className="bg-brand-bgSecondary dark:bg-slate-800">
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                className={cn(
                  'px-4 py-3 text-left font-semibold text-brand-textMuted dark:text-slate-400 first:rounded-tl-xl last:rounded-tr-xl',
                  col.className
                )}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="bg-white dark:bg-slate-900 divide-y divide-brand-border dark:divide-slate-700">
          {loading ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-brand-textMuted dark:text-slate-400">
                <span className="inline-block w-6 h-6 border-2 border-brand-blue border-t-transparent rounded-full animate-spin" />
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-brand-textMuted dark:text-slate-400">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row) => (
              <tr
                key={String(row[keyField])}
                className="hover:bg-brand-bg dark:hover:bg-slate-800 transition-colors"
              >
                {columns.map((col) => (
                  <td key={col.key} className={cn('px-4 py-3 text-brand-text dark:text-slate-200', col.className)}>
                    {col.render
                      ? col.render(row)
                      : String((row as Record<string, unknown>)[col.key] ?? '')}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
