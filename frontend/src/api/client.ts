import axios from 'axios'
import { toast } from 'sonner'
import * as authStore from '@/auth/authStore'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
})

apiClient.interceptors.request.use(async (config) => {
  const { accessToken } = authStore.getState()
  if (accessToken) {
    try {
      await authStore.updateToken(30)
    } catch {
      window.location.href = '/login'
      return config
    }
    config.headers.Authorization = `Bearer ${authStore.getState().accessToken}`
  }

  const tenantId = authStore.getState().tokenParsed?.tenant_id
  if (tenantId) {
    config.headers['X-Tenant-ID'] = tenantId
  }

  return config
})

/**
 * Un refus d'authentification ne doit jamais ressembler à une absence de
 * données.
 *
 * Sans cet intercepteur, un 401 ne produisait rien : la requête échouait, le
 * composant rendait sa liste vide, et l'écran disait « aucun résultat » alors
 * que la base était pleine. C'est la panne la plus coûteuse rencontrée sur ce
 * projet — deux jours passés à chercher dans le jeu de démo un défaut qui était
 * dans la configuration de l'émetteur du jeton.
 *
 * Le cas est trompeur parce qu'un jeton peut être parfaitement valide et
 * pourtant refusé : Keycloak en `start-dev` inscrit dans le jeton l'adresse par
 * laquelle on l'a joint, et l'API compare cette adresse à celle qu'elle attend.
 * Si le navigateur passe par l'IP du poste et l'API par « localhost », les deux
 * chaînes diffèrent et tout est rejeté. D'où le message : il nomme les deux
 * côtés à rapprocher plutôt que de dire « non autorisé ».
 */
let refusSignale = false

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const statut = error.response?.status

    if (statut === 401) {
      // Une seule alerte, même si dix requêtes échouent ensemble au chargement
      // d'un écran ; sans cette garde, la page se couvrirait de doublons.
      if (!refusSignale) {
        refusSignale = true
        toast.error("Session refusée par l'API (401)", {
          description:
            "Le jeton est rejeté, les écrans resteront vides. Vérifiez que le front "
            + "et l'API désignent Keycloak par la même adresse "
            + '(VITE_KEYCLOAK_URL et LAN_HOST) — voir .env.example.',
          duration: 12000,
          onDismiss: () => {
            refusSignale = false
          },
          onAutoClose: () => {
            refusSignale = false
          },
        })
      }
    } else if (statut === 403) {
      toast.error('Accès refusé (403)', {
        description: "Votre rôle ne permet pas cette action.",
      })
    }

    return Promise.reject(error)
  },
)

export default apiClient
