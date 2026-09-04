/**
 * Appels HTTP : `fetch` plutôt qu'axios, pour ne pas embarquer une dépendance
 * de plus dans un bundle qui transite par le Wi-Fi à chaque rechargement.
 *
 * Deux bases, comme sur le web : le backend Java et le service Python portent
 * le **même** jeton Keycloak, mais seul le premier reçoit `X-Tenant-ID` —
 * l'assistant décide son périmètre à partir du compte porté par le jeton.
 */

import { aiUrl, apiUrl } from '../config'
import * as authStore from '../auth/authStore'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message)
  }
}

interface Options {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  /** Le chat IA tourne sur CPU : une réponse peut demander plus d'une minute. */
  timeoutMs?: number
}

async function request<T>(base: string, path: string, opts: Options, tenantHeader: boolean): Promise<T> {
  await authStore.updateToken(30)
  const { accessToken, tokenParsed } = authStore.getState()

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (tenantHeader && tokenParsed?.tenant_id) headers['X-Tenant-ID'] = tokenParsed.tenant_id

  // Sans borne, une requête vers une IP injoignable reste en attente sans fin :
  // sur mobile c'est l'écran qui tourne indéfiniment, le symptôme le moins
  // exploitable qui soit quand le Wi-Fi n'est pas le bon.
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), opts.timeoutMs ?? 15_000)

  let response: Response
  try {
    response = await fetch(`${base}${path}`, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      signal: controller.signal,
    })
  } catch (e) {
    const aborted = (e as Error).name === 'AbortError'
    throw new ApiError(
      aborted
        ? "Le serveur ne répond pas. Vérifiez l'adresse et que le téléphone est sur le même Wi-Fi."
        : "Serveur injoignable. Vérifiez l'adresse saisie à la connexion.",
      0
    )
  } finally {
    clearTimeout(timer)
  }

  if (!response.ok) {
    const data = (await response.json().catch(() => ({}))) as { message?: string; detail?: string }
    throw new ApiError(
      data.message ?? data.detail ?? `Erreur ${response.status}`,
      response.status
    )
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** Backend Java. */
export const api = <T>(path: string, opts: Options = {}) => request<T>(apiUrl(), path, opts, true)

/** Microservice Python (assistant). */
export const ai = <T>(path: string, opts: Options = {}) => request<T>(aiUrl(), path, opts, false)

/** Construit une query string en ignorant les paramètres absents. */
export function query(params: Record<string, string | number | boolean | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== '')
  return entries.length ? `?${entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&')}` : ''
}

/**
 * Envoi d'un fichier en multipart.
 *
 * Fonction séparée de `request` : celle-ci sérialise son corps en JSON et pose
 * un `Content-Type`. Ici il ne faut faire ni l'un ni l'autre — c'est le moteur
 * qui construit le corps et, surtout, la frontière (`boundary`) du multipart.
 *
 * **La partie n'est PAS un `{ uri, name, type }`**, la forme historique de
 * React Native, et c'est le point à ne pas défaire. Depuis le SDK 52, Expo
 * remplace le `fetch` global par son implémentation WinterCG, dont le
 * convertisseur multipart n'accepte que trois formes : une chaîne, un `Blob`,
 * ou un objet exposant `bytes()`. Une partie portant `uri` tombe dans son
 * `else` final et lève « Unsupported FormDataPart implementation », sans que
 * rien ne parte sur le réseau — l'erreur ressemble alors à une panne de
 * serveur, et on cherche du mauvais côté pendant longtemps.
 *
 * On passe donc la troisième forme. Elle est préférée au `Blob` natif parce
 * qu'elle laisse choisir le nom de fichier annoncé au serveur, indépendamment
 * de celui de la copie en cache.
 */
export interface FichierAEnvoyer {
  name: string
  type: string
  /** Lecture différée : l'appelant sait d'où viennent les octets, pas cette couche. */
  bytes: () => Promise<Uint8Array>
}
export async function upload<T>(
  path: string,
  champ: string,
  fichier: FichierAEnvoyer,
  timeoutMs = 240_000
): Promise<T> {
  await authStore.updateToken(30)
  const { accessToken } = authStore.getState()

  let octets: Uint8Array
  try {
    octets = await fichier.bytes()
  } catch (e) {
    throw new ApiError(
      `Le fichier n'a pas pu être lu sur le téléphone (${(e as Error).message}).`,
      0
    )
  }

  const corps = new FormData()
  // Le cast est inévitable : le type `Blob` du DOM ne décrit pas cette forme,
  // que le convertisseur d'Expo accepte pourtant (`'bytes' in entry`).
  corps.append(champ, {
    name: fichier.name,
    type: fichier.type,
    bytes: async () => octets,
  } as unknown as Blob)

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)

  let response: Response
  try {
    response = await fetch(`${aiUrl()}${path}`, {
      method: 'POST',
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
      body: corps,
      signal: controller.signal,
    })
  } catch (e) {
    const cause = (e as Error).message || String(e)
    // La cause d'origine est conservée dans le message. Un envoi de fichier
    // échoue pour bien d'autres raisons qu'un serveur injoignable — une URI
    // illisible, par exemple — et masquer le motif derrière un texte rassurant
    // envoie chercher la panne du mauvais côté.
    console.warn('[upload] échec', { path, nom: fichier.name, cause })
    throw new ApiError(
      (e as Error).name === 'AbortError'
        ? "L'envoi a dépassé le temps imparti. Essayez avec un document plus court."
        : `L'envoi du fichier a échoué : ${cause}`,
      0
    )
  } finally {
    clearTimeout(timer)
  }

  if (!response.ok) {
    const data = (await response.json().catch(() => ({}))) as { detail?: string }
    throw new ApiError(data.detail ?? `Erreur ${response.status}`, response.status)
  }

  return (await response.json()) as T
}
