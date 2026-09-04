# AI Assistant — supervision de la plateforme SmartSchool

Microservice Python/FastAPI qui lit les métriques réelles de la plateforme
(Prometheus + Actuator) et les explique en français via un LLM local (Ollama).

Le LLM **ne compose jamais de PromQL** : il choisit un outil dans une liste
figée (`app/tools/`), et le code exécute une requête écrite à la main.

## Démarrage en développement (recommandé)

```bash
cd /home/aymen/sc-p/ai-assistant
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # puis passer AUTH_ENABLED=false pour tester sans Keycloak
uvicorn app.main:app --reload --port 8000
```

- Doc interactive : http://localhost:8000/docs
- Prérequis : `docker compose up -d prometheus` et `ollama pull qwen2.5:7b`

### Choix du modèle

Mesuré sur cette machine (RTX 3050 6 Go) : le 7b tient **entièrement** en VRAM
(4,75 Go sur 4,75 Go chargés), donc aucun basculement sur le CPU.

| | 3b | 7b |
|---|---|---|
| Génération | 57,4 tokens/s | 26,3 tokens/s |
| Réponse de l'assistant, modèle chaud | 1,4 – 2,7 s | 2,7 – 6,5 s |
| VRAM occupée | 2,16 Go | 4,75 Go |

⚠️ Les deux modèles ne tiennent **pas** ensemble en VRAM (6,9 Go pour 6 Go
disponibles). Faire tourner deux instances du service avec des modèles
différents fait recharger le modèle à chaque bascule : +5 s sur le premier
appel. Pour revenir au 3b, changer `OLLAMA_MODEL` dans `.env`.

```bash
curl -s localhost:8000/health | jq
curl -s localhost:8000/api/monitoring/health | jq
curl -s -X POST localhost:8000/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Est-ce qu'\''il y a un problème sur la plateforme ?"}' | jq
```

`tools_used` non vide = le tool calling fonctionne. S'il est vide, rien d'autre
n'a de valeur : la réponse serait inventée.

## Recherche sémantique sur les cahiers de séance (RAG)

`POST /api/cahier/assistant/chat` — une route, deux usages :

| Appelant | Ce qu'il interroge | Exemple |
|---|---|---|
| `TEACHER` | ses propres séances | « quand ai-je traité Pythagore et quel devoir j'ai donné ? » |
| `SCHOOL_ADMIN` | celles de son établissement | « où en est chaque classe dans son programme ? » |

**Ce n'est pas ce service qui arbitre.** Le backend construit le corpus à partir
du compte porté par le jeton (`GET /api/v1/cahier/corpus`, qui ne prend aucun
identifiant d'enseignant) : l'assistant indexe ce qu'il reçoit, sans pouvoir en
demander plus. Le cache d'index est donc clé PAR COMPTE — une clé par
établissement servirait à un enseignant l'index de son directeur.

### Prérequis

```bash
ollama pull nomic-embed-text     # ~300 Mo, cohabite avec qwen2.5:7b dans 6 Go
curl -s localhost:8000/health | jq '.embeddings'   # doit valoir true
```

### Deux outils, deux rôles distincts

- `search_cahier_seances` — recherche par similarité, pour les questions de
  **contenu** (« qu'ai-je fait avec la 8A ? »). Chaque extrait est rendu avec sa
  source, que le modèle doit citer.
- `get_couverture_programme` — agrégation **exacte** (séances, dernière date,
  dernier chapitre par classe et matière), pour les questions d'**état**
  (« qui a pris du retard ? »). Une date et un compteur se lisent, ils ne
  s'estiment pas : les confier à la similarité, ce serait accepter que le modèle
  se trompe sur le seul point que l'utilisateur vérifiera.

Aucun outil d'écriture : le cahier de séance fait foi et se verrouille.

### Calibration du filtrage — mesurée, pas devinée

nomic-embed-text comprime toutes ses similarités entre **0,52 et 0,69** sur ce
corpus francophone, questions hors sujet comprises. Un seuil absolu bas ne
rejette donc jamais rien. D'où deux filtres (`rag_score_floor`,
`rag_score_margin`) :

| Question | Meilleur score | Verdict |
|---|---|---|
| « quand ai-je traité Pythagore ? » | 0,67 | retenue |
| « où en est la 8A en SVT ? » | 0,68 | retenue |
| « combien coûte un abonnement ? » | 0,60 | rejetée |
| « quelle est la météo demain ? » | 0,57 | rejetée |

⚠️ Calibré sur un échantillon de 4 séances : **à revoir sur des données réelles**,
un corpus plus dense resserrant l'écart entre les deux groupes.

Testé aussi : les préfixes `search_query:` / `search_document:` recommandés pour
nomic **dégradent** le classement ici (2 bonnes réponses sur 3 contre 3 sur 3).
Ils ne sont pas utilisés.

### Pas de base vectorielle, pas de numpy

Quelques centaines de séances, un produit scalaire sur des vecteurs déjà
normalisés à l'indexation : la recherche coûte quelques dizaines de
millisecondes, contre plusieurs secondes pour la génération qui suit. Une base
vectorielle ne ferait ici qu'ajouter un service à exploiter. Le jour où le
corpus change d'ordre de grandeur, `app-db` est un PostgreSQL : `pgvector` s'y
greffe sans changer l'architecture.

## Tests

```bash
source .venv/bin/activate && pytest -q
```

## Docker

```bash
cd /home/aymen/sc-p
docker compose up -d --build ai-assistant
```

Deux points de configuration propres au conteneur :

| Variable | Valeur en conteneur | Pourquoi |
|---|---|---|
| `ACTUATOR_URL` | `http://host.docker.internal:9091` | **Port 9091**, pas 8080 : c'est `management.server.port` de `application.yml`. |
| `KEYCLOAK_URL` / `KEYCLOAK_ISSUER_URL` | `http://keycloak:8081` / `http://localhost:8081/realms/smartschool` | L'adresse **réseau** (pour lire les clés JWKS) diffère de l'**issuer** inscrit dans le token, émis pour un navigateur sur `localhost`. |

### Ollama doit écouter au-delà de 127.0.0.1

Par défaut Ollama n'écoute que sur `127.0.0.1:11434` : le conteneur ne peut pas
l'atteindre et `/health` renvoie `"ollama": false`.

```bash
sudo systemctl edit ollama
# ajouter :
#   [Service]
#   Environment="OLLAMA_HOST=0.0.0.0:11434"
sudo systemctl daemon-reload && sudo systemctl restart ollama
```

Sans cette étape, garde le mode développement (`uvicorn` sur l'hôte), où
`localhost:11434` fonctionne directement.

## Frontend

Page `/super-admin/monitoring` (`frontend/src/features/superadmin/monitoring/`).
Le client axios `src/api/aiAssistant.api.ts` réutilise le même jeton Keycloak.
⚠️ Les champs sont en **snake_case** : Pydantic sérialise tel quel.
