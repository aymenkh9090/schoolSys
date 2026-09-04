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

Cela démarre 3 conteneurs (voir `docker-compose.yml`) :

| Service | Rôle | Port |
|---|---|---|
| `keycloak-db` | PostgreSQL dédié à Keycloak | interne |
| `keycloak` | Serveur Keycloak (mode dev) | http://localhost:8081 |
| `app-db` | PostgreSQL de l'application (`smartschool`) | localhost:5432 |

Vérifier que Keycloak est démarré (~30s) :

```bash
docker logs keycloak --tail=20
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
