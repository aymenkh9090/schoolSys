import {
  createContext,
  useContext,
  useEffect,
  useState,
  useSyncExternalStore,
} from 'react'
import * as authStore from '@/auth/authStore'

interface AuthContextValue {
  /** false tant que la restauration de session n'a pas été tentée */
  initialized: boolean
}

const AuthContext = createContext<AuthContextValue>({ initialized: false })

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [initialized, setInitialized] = useState(false)

  useEffect(() => {
    authStore.restoreSession().finally(() => setInitialized(true))
  }, [])

  return (
    <AuthContext.Provider value={{ initialized }}>
      {children}
    </AuthContext.Provider>
  )
}

/** État réactif du store d'auth (tokens + init). */
export function useAuthState() {
  const { initialized } = useContext(AuthContext)
  const state = useSyncExternalStore(authStore.subscribe, authStore.getState)
  return { initialized, ...state }
}
