# SmartSchool

Plateforme SaaS de gestion scolaire (backend Spring Boot multi-module + frontend React/Vite), avec authentification déléguée à Keycloak.

## Prérequis

- Docker + Docker Compose
- Java 21 + Maven
- Node.js 20+ / npm

## 1. Lancer l'infrastructure (Docker Compose)

Depuis la racine du dépôt :

```bash
docker compose up -d
```

Cela démarre **6 conteneurs** (voir `docker-compose.yml`) :

| Service | Conteneur | Rôle | Accès |
|---|---|---|---|
| `keycloak-db` | `keycloak-db-ss` | PostgreSQL dédié à Keycloak | interne |
| `keycloak` | `keycloak-ss` | Serveur Keycloak (mode dev) | http://localhost:8081 |
| `app-db` | `smartschool-dbss` | PostgreSQL de l'application (`smartschool`) | localhost:5432 |
| `prometheus` | `prometheuss` | Collecte des métriques de l'API | http://localhost:9090 |
| `grafana` | `grafana-ss` | Tableaux de bord des métriques | http://localhost:3001 |
| `ai-assistant` | `ai-assistant-ss` | Microservice Python (assistants IA) | http://localhost:8000 |

Les trois derniers ne sont **pas nécessaires** pour lancer l'application : sans
eux, le backend et le frontend fonctionnent, seuls la supervision et les
assistants sont indisponibles.

> **Le conteneur `ai-assistant` ne sert pas le chat.** Il expose le tableau de
> bord de supervision, mais Ollama n'écoute que sur le `127.0.0.1` de l'hôte,
> hors de sa portée. Pour les assistants, lancer l'instance de développement :
> `cd ai-assistant && uvicorn app.main:app --port 8001` (voir
> `ai-assistant/README.md`).

Le port hôte de Grafana est **3001** et non 3000 : ce dernier est pris par le
serveur de développement du frontend.

Vérifier que Keycloak est démarré (~30 s) — le nom du conteneur est
`keycloak-ss`, pas `keycloak` :

```bash
docker compose ps
docker logs keycloak-ss --tail=20
```

Console d'admin Keycloak : **http://localhost:8081** → `admin` / `admin`

La base applicative `app-db` est directement utilisable par le backend (voir `spring.datasource` dans `application.yml`) : aucune migration manuelle n'est nécessaire, Liquibase s'en charge au démarrage du backend.

## 2. Configurer Keycloak (realm + clients)

### 2.1 Créer le realm

Console admin → menu déroulant en haut à gauche → **Create realm** → nom `smartschool`.

### 2.2 Créer les realm roles

**Realm roles** → **Create role**, créer exactement (les noms doivent correspondre aux constantes backend) :

- `PLATFORM_SUPER_ADMIN`
- `SCHOOL_ADMIN`
- `TEACHER`
- `SURVEILLANT`
- `PARENT`
- `STUDENT`

### 2.3 Créer le client backend (`smartschool-backend`)

**Clients** → **Create client** :

| Paramètre | Valeur |
|---|---|
| Client type | OpenID Connect |
| Client ID | `smartschool-backend` |
| Client authentication | **ON** (confidential) |
| Service accounts roles | **ON** (obligatoire) |
| Standard flow | OFF |
| Direct access grants | ON (pratique pour tester via Postman) |

Une fois créé :
- Onglet **Credentials** → copier le **Client Secret** (nécessaire à l'étape 3).
- Onglet **Service accounts roles** → **Assign role** → filtrer par client `realm-management` → assigner `manage-users`, `manage-groups`, `query-users`, `view-users`.

### 2.4 Ajouter le mapper `tenant_id`

**Clients** → `smartschool-backend` → **Client scopes** → `smartschool-backend-dedicated` → **Add mapper** → **By configuration** → **User Attribute** :

| Champ | Valeur |
|---|---|
| Name | `tenant-id-mapper` |
| User Attribute | `tenant_id` |
| Token Claim Name | `tenant_id` |
| Add to access token | ON |
| Add to ID token | ON |

### 2.5 Créer le client frontend (`smartschool-frontend`)

**Clients** → **Create client** :

| Paramètre | Valeur |
|---|---|
| Client ID | `smartschool-frontend` |
| Client authentication | OFF (public) |
| Standard flow | ON |
| Valid redirect URIs | `http://localhost:5173/*` |
| Web origins | `http://localhost:5173` |

### 2.6 Créer le premier super admin

**Users** → **Create user** : username `superadmin`, email vérifié → **Create** → onglet **Credentials** → **Set password** (Temporary : OFF) → onglet **Role mapping** → assigner `PLATFORM_SUPER_ADMIN`.

## 3. Configurer les variables d'environnement du backend

Exporter le secret du client `smartschool-backend` récupéré à l'étape 2.3 :

```bash
export KC_CLIENT_SECRET="<secret-copié-depuis-keycloak>"
```

Optionnel (envoi d'email lors de la création de compte) :

```bash
export SMTP_USERNAME="..."
export SMTP_PASSWORD="..."
```

Le reste de la configuration (URL de la base, issuer Keycloak, realm, client-id) est déjà dans `backend/smartschool-api/src/main/resources/application.yml` et pointe vers les services du `docker-compose.yml`.

## 4. Démarrer le backend

```bash
cd backend
mvn -pl smartschool-api -am spring-boot:run
```

- API : http://localhost:8080
- Swagger UI : http://localhost:8080/swagger-ui.html

Autres commandes utiles : voir `backend/README.md` (tests, profil `demo` avec données d'exemple, etc.).

## 5. Démarrer le frontend

```bash
cd frontend
npm install
npm run dev
```

- Application : http://localhost:5173

`frontend/.env.local` contient déjà les valeurs par défaut cohérentes avec Keycloak et le backend démarrés ci-dessus :

```env
VITE_API_URL=http://localhost:8080
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=smartschool
VITE_KEYCLOAK_CLIENT_ID=smartschool-frontend
```

## 6. Se connecter

Ouvrir http://localhost:5173 et se connecter avec l'utilisateur `superadmin` créé à l'étape 2.6 (rôle `PLATFORM_SUPER_ADMIN`).

## Tester l'API sans le frontend (Postman/curl)

Obtenir un token JWT :

```bash
curl -X POST http://localhost:8081/realms/smartschool/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=smartschool-backend" \
  -d "client_secret=$KC_CLIENT_SECRET" \
  -d "username=superadmin" \
  -d "password=<mot-de-passe>" \
  -d "scope=openid"
```

Utiliser le `access_token` retourné dans l'en-tête `Authorization: Bearer <token>` des appels à l'API.
