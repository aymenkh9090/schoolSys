/**
 * Store d'authentification custom : appelle Keycloak en arrière-plan
 * (grant "password" / Direct Access Grants) sans jamais rediriger vers
 * la page de login Keycloak. Les pages de login restent des pages React.
 *
 * Prérequis Keycloak : activer "Direct Access Grants" sur le client
 * smartschool-frontend.
 */

import i18n from '@/i18n'

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL
const REALM = import.meta.env.VITE_KEYCLOAK_REALM
const CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID

const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`
const LOGOUT_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout`
const REFRESH_TOKEN_KEY = 'smartschool.refreshToken'

export interface TokenParsed {
  exp: number
  sub: string
  preferred_username?: string
  name?: string
  given_name?: string
  family_name?: string
  email?: string
  tenant_id?: string
  must_change_password?: string
  realm_access?: { roles: string[] }
  [key: string]: unknown
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  tokenParsed: TokenParsed | null
}

let state: AuthState = { accessToken: null, refreshToken: null, tokenParsed: null }
const listeners = new Set<() => void>()

function notify() {
  listeners.forEach((l) => l())
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function getState(): AuthState {
  return state
}

function parseJwt(token: string): TokenParsed {
  const payload = token.split('.')[1]
  return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
}

function setTokens(accessToken: string, refreshToken: string) {
  state = { accessToken, refreshToken, tokenParsed: parseJwt(accessToken) }
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  notify()
}

function clearTokens() {
  state = { accessToken: null, refreshToken: null, tokenParsed: null }
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  notify()
}

async function tokenRequest(body: Record<string, string>): Promise<void> {
  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: CLIENT_ID, ...body }),
  })

  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    let message = data.error_description ?? i18n.t('login.loginFailed')
    if (data.error === 'invalid_grant') {
      message =
        data.error_description === 'Account is not fully set up'
          ? i18n.t('login.accountNeedsAction')
          : i18n.t('login.invalidCredentials')
    }
    throw new Error(message)
  }

  const data = await response.json()
  setTokens(data.access_token, data.refresh_token)
}

/** Connexion email / mot de passe via le grant password de Keycloak. */
export async function login(username: string, password: string): Promise<void> {
  await tokenRequest({ grant_type: 'password', username, password, scope: 'openid' })
}

/**
 * Force un nouveau token même si le courant est encore valide — nécessaire après
 * complete-first-login pour que le JWT reflète must_change_password=false
 * (les attributs Keycloak sont relus à chaque émission de token).
 */
export async function refreshNow(): Promise<void> {
  const { refreshToken } = state
  if (!refreshToken) return
  await tokenRequest({ grant_type: 'refresh_token', refresh_token: refreshToken })
}

/** Rafraîchit le token s'il expire dans moins de `minValiditySeconds`. */
export async function updateToken(minValiditySeconds = 30): Promise<void> {
  const { tokenParsed, refreshToken } = state
  if (!refreshToken) return
  const expiresIn = (tokenParsed?.exp ?? 0) - Date.now() / 1000
  if (expiresIn > minValiditySeconds) return

  try {
    await tokenRequest({ grant_type: 'refresh_token', refresh_token: refreshToken })
  } catch {
    clearTokens()
    throw new Error(i18n.t('login.sessionExpired'))
  }
}

/** Restaure la session au chargement de l'app depuis le refresh token stocké. */
export async function restoreSession(): Promise<boolean> {
  const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!stored) return false
  try {
    await tokenRequest({ grant_type: 'refresh_token', refresh_token: stored })
    return true
  } catch {
    clearTokens()
    return false
  }
}

export async function logout(): Promise<void> {
  const { refreshToken } = state
  if (refreshToken) {
    // Révoque la session côté Keycloak (best effort)
    fetch(LOGOUT_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: CLIENT_ID, refresh_token: refreshToken }),
    }).catch(() => undefined)
  }
  clearTokens()
}
