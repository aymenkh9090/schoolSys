/**
 * Authentification mobile : même flux que le web (grant `password` de
 * Keycloak, cf. `frontend/src/auth/authStore.ts`), volontairement.
 *
 * OIDC/PKCE serait le choix correct pour une application publiée sur un store —
 * le mobile ne peut pas garder un secret. Ici l'application n'est pas publiée,
 * elle est démontrée sur un réseau local, et implanter PKCE + navigateur
 * système coûterait une journée pour ne rien démontrer de plus du sujet du
 * PFE. Le choix est assumé et documenté, il n'est pas subi.
 */

import AsyncStorage from '@react-native-async-storage/async-storage'

import { KEYCLOAK_CLIENT_ID, KEYCLOAK_REALM, keycloakUrl } from '../config'

const REFRESH_TOKEN_KEY = 'smartschool.refreshToken'

const tokenEndpoint = () =>
  `${keycloakUrl()}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`
const logoutEndpoint = () =>
  `${keycloakUrl()}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`

export interface TokenParsed {
  exp: number
  sub: string
  preferred_username?: string
  name?: string
  given_name?: string
  family_name?: string
  email?: string
  tenant_id?: string
  realm_access?: { roles: string[] }
}

interface AuthState {
  accessToken: string | null
  tokenParsed: TokenParsed | null
}

let refreshToken: string | null = null
let state: AuthState = { accessToken: null, tokenParsed: null }
const listeners = new Set<() => void>()

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function getState(): AuthState {
  return state
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

/**
 * Décodage base64url → texte UTF-8, écrit à la main.
 *
 * Ni `atob` ni `Buffer` ne sont garantis présents dans Hermes selon la version,
 * et un jeton qu'on ne sait pas lire, c'est une application qui ne démarre pas.
 * Vingt lignes sans dépendance valent mieux qu'un pari sur le moteur. La sortie
 * doit être décodée en UTF-8 et non octet par octet : les noms tunisiens sont
 * accentués, et « Béchir » deviendrait sinon « BÃ©chir ».
 */
function base64UrlDecode(input: string): string {
  const b64 = input.replace(/-/g, '+').replace(/_/g, '/')
  const bytes: number[] = []

  for (let i = 0; i < b64.length; i += 4) {
    const chunk = [0, 1, 2, 3].map((k) => B64.indexOf(b64[i + k] ?? '='))
    bytes.push((chunk[0] << 2) | (chunk[1] >> 4))
    if (chunk[2] >= 0) bytes.push(((chunk[1] & 15) << 4) | (chunk[2] >> 2))
    if (chunk[3] >= 0) bytes.push(((chunk[2] & 3) << 6) | chunk[3])
  }

  // Octets → caractères : %XX puis decodeURIComponent fait la conversion UTF-8.
  return decodeURIComponent(
    bytes.map((b) => '%' + b.toString(16).padStart(2, '0')).join('')
  )
}

function parseJwt(token: string): TokenParsed {
  return JSON.parse(base64UrlDecode(token.split('.')[1]))
}

function notify() {
  listeners.forEach((l) => l())
}

async function setTokens(access: string, refresh: string) {
  refreshToken = refresh
  state = { accessToken: access, tokenParsed: parseJwt(access) }
  await AsyncStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  notify()
}

async function clearTokens() {
  refreshToken = null
  state = { accessToken: null, tokenParsed: null }
  await AsyncStorage.removeItem(REFRESH_TOKEN_KEY)
  notify()
}

async function tokenRequest(body: Record<string, string>): Promise<void> {
  const response = await fetch(tokenEndpoint(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: KEYCLOAK_CLIENT_ID, ...body }).toString(),
  })

  if (!response.ok) {
    const data = await response.json().catch(() => ({}) as Record<string, string>)
    if (data.error === 'invalid_grant') {
      throw new Error(
        data.error_description === 'Account is not fully set up'
          ? 'Ce compte doit être finalisé depuis le web avant la première connexion mobile.'
          : 'Identifiants incorrects.'
      )
    }
    throw new Error(data.error_description ?? 'Connexion impossible.')
  }

  const data = await response.json()
  await setTokens(data.access_token, data.refresh_token)
}

export async function login(username: string, password: string): Promise<void> {
  await tokenRequest({ grant_type: 'password', username, password, scope: 'openid' })
}

/** Rafraîchit le jeton s'il expire dans moins de `minValiditySeconds`. */
export async function updateToken(minValiditySeconds = 30): Promise<void> {
  if (!refreshToken) return
  const expiresIn = (state.tokenParsed?.exp ?? 0) - Date.now() / 1000
  if (expiresIn > minValiditySeconds) return

  try {
    await tokenRequest({ grant_type: 'refresh_token', refresh_token: refreshToken })
  } catch {
    await clearTokens()
    throw new Error('Session expirée, reconnectez-vous.')
  }
}

/** Reconnexion silencieuse au lancement — l'enseignant ne ressaisit pas son mot de passe. */
export async function restoreSession(): Promise<boolean> {
  const stored = await AsyncStorage.getItem(REFRESH_TOKEN_KEY)
  if (!stored) return false
  try {
    await tokenRequest({ grant_type: 'refresh_token', refresh_token: stored })
    return true
  } catch {
    await clearTokens()
    return false
  }
}

export async function logout(): Promise<void> {
  if (refreshToken) {
    // Révocation best effort : l'utilisateur est déconnecté localement quoi qu'il arrive.
    fetch(logoutEndpoint(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: KEYCLOAK_CLIENT_ID,
        refresh_token: refreshToken,
      }).toString(),
    }).catch(() => undefined)
  }
  await clearTokens()
}

export function roles(): string[] {
  return state.tokenParsed?.realm_access?.roles ?? []
}

export function displayName(): string {
  const p = state.tokenParsed
  return p?.name ?? p?.preferred_username ?? 'Enseignant'
}
