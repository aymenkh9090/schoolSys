import { useEffect } from 'react'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { Toaster } from 'sonner'
import { useTranslation } from 'react-i18next'
import { router } from '@/router'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

const RTL_LANGS = new Set(['ar'])

export default function App() {
  const { i18n } = useTranslation()

  useEffect(() => {
    document.documentElement.dir = RTL_LANGS.has(i18n.language) ? 'rtl' : 'ltr'
    document.documentElement.lang = i18n.language
  }, [i18n.language])

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster
        position="top-center"
        richColors
        expand
        duration={4000}
        toastOptions={{
          style: {
            fontSize: '1rem',
            padding: '16px 20px',
          },
          className: 'font-medium shadow-lg',
        }}
      />
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  )
}
