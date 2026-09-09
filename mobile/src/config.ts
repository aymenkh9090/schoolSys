/**
 * Adresse du serveur, modifiable depuis l'écran de connexion.
 *
 * Sur un téléphone, `localhost` désigne le téléphone lui-même : les trois
 * services tournent sur le portable et ne sont joignables que par son IP sur
 * le réseau local. Cette IP change d'un réseau à l'autre — celle de la salle
 * de soutenance n'est pas celle de la maison — donc elle est **saisissable
 * dans l'application** plutôt que compilée dedans : le jour J, on corrige un
 * champ de texte au lieu de reconstruire le bundle.
 */

import AsyncStorage from '@react-native-async-storage/async-storage'

const HOST_KEY = 'smartschool.host'

/** Valeur de départ : `EXPO_PUBLIC_HOST` du `.env`, sinon l'IP de développement. */
export const DEFAULT_HOST = process.env.EXPO_PUBLIC_HOST ?? '192.168.0.235'

// Ports fixés par docker-compose et application.yml. Le backend tourne sur
// l'hôte (8080), Keycloak dans Docker (8081), l'assistant dans Docker (8000).
//
// **8000 et non 8001**, et c'est le correctif d'une panne qui est revenue deux
// fois. Le 8001 désignait l'instance uvicorn lancée à la main sur le poste : la
// seule à atteindre Ollama, mais liée à `127.0.0.1` par défaut. Elle répondait
// donc au poste et à rien d'autre — le téléphone affichait « Serveur
// injoignable » chaque fois qu'on oubliait `--host 0.0.0.0`, c'est-à-dire
// chaque fois. Le conteneur, lui, publie `0.0.0.0:8000` sans qu'on ait à y
// penser, et atteint Ollama depuis que celui-ci écoute sur toutes les
// interfaces (`OLLAMA_HOST`, cf. ai-assistant/README.md). Plus aucune commande
// à ne pas oublier : `docker compose up -d` suffit.
const PORT_API = 8080
const PORT_KEYCLOAK = 8081
const PORT_AI = 8000

let host = DEFAULT_HOST

export function getHost(): string {
  return host
}

/** Restaure l'hôte saisi lors d'une session précédente. */
export async function loadHost(): Promise<string> {
  const stored = await AsyncStorage.getItem(HOST_KEY)
  if (stored) host = stored
  return host
}

export async function setHost(value: string): Promise<void> {
  host = value.trim() || DEFAULT_HOST
  await AsyncStorage.setItem(HOST_KEY, host)
}

export const apiUrl = () => `http://${host}:${PORT_API}`
export const keycloakUrl = () => `http://${host}:${PORT_KEYCLOAK}`
export const aiUrl = () => `http://${host}:${PORT_AI}`

export const KEYCLOAK_REALM = 'smartschool'
export const KEYCLOAK_CLIENT_ID = 'smartschool-frontend'
