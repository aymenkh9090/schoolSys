# SchoolSys

Plateforme SaaS de gestion scolaire : backend Spring Boot multi-module, frontend
React/Vite, application mobile React Native pour les enseignants, et un
microservice Python d'assistants IA. Authentification déléguée à Keycloak.

> **Deux noms, un seul produit.** Le produit s'appelle **SchoolSys** — c'est le
> nom porté par le frontend, par l'application mobile et par les coordonnées
> Maven (`tn.schoolsys` / `schoolsys-*`). `smartschool` subsiste dans les
> **identifiants techniques** : le dépôt GitHub, le realm Keycloak, la base de
> données, les noms de conteneurs et le module `smartschool-api`. Les renommer
> reviendrait à reconstruire le realm et la base pour un gain purement cosmétique
> — ils restent donc tels quels, et toute commande de ce README les emploie
> littéralement.

## Prérequis

- Docker + Docker Compose
- Java 21 + Maven
- Node.js 20+ / npm
- Pour le mobile : un téléphone avec **Expo Go**, sur le même Wi-Fi que le poste

## 1. Lancer la pile (Docker Compose)

Depuis la racine du dépôt :

```bash
cp .env.example .env
docker compose up -d --build
```

Une seule commande, un seul fichier : **8 conteneurs** (voir `docker-compose.yml`).

| Service | Conteneur | Rôle | Accès |
|---|---|---|---|
| `keycloak-db` | `keycloak-db-ss` | PostgreSQL dédié à Keycloak | interne |
| `keycloak` | `keycloak-ss` | Serveur Keycloak (mode dev) | http://localhost:8081 |
| `app-db` | `smartschool-dbss` | PostgreSQL de l'application (`smartschool`) | localhost:5432 |
| `backend` | `smartschool-api-ss` | API Spring Boot | http://localhost:8080 |
| `frontend` | `smartschool-front-ss` | UI React servie par nginx | http://localhost:3000 |
| `prometheus` | `prometheuss` | Collecte des métriques de l'API | http://localhost:9090 |
| `grafana` | `grafana-ss` | Tableaux de bord des métriques | http://localhost:3001 |
| `ai-assistant` | `ai-assistant-ss` | Microservice Python (assistants IA) | http://localhost:8000 |

Les conteneurs se joignent entre eux par leur **nom de service** (`app-db`,
`keycloak`, `prometheus`, `backend`) : aucune adresse n'est à régler à la main.
Seules font exception les URL destinées au **navigateur** — les `VITE_*` du front
et l'`issuer` des jetons — qui doivent rester des adresses joignables depuis
l'extérieur de Docker. Voir §3 bis, c'est la source de panne n°1 du projet.

Prometheus, Grafana et `ai-assistant` ne sont **pas nécessaires** au
fonctionnement : sans eux, l'API et le front tournent, seuls la supervision et
les assistants sont indisponibles.

> **Au tout premier lancement, le backend s'arrêtera** : le realm Keycloak
> n'existe pas encore, donc `KC_CLIENT_SECRET` est vide dans le `.env` et le
> placeholder d'`application.yml` reste non résolu. Séquence d'amorçage, une
> seule fois :
>
> ```bash
> COMPOSE_PROFILES= docker compose up -d   # l'infra seule
> # → suivre le §2 (realm + clients), coller le secret dans .env
> docker compose up -d --build             # la pile complète
> ```
>
> Automatiser cette étape demande d'importer le realm au démarrage
> (`--import-realm`) : c'est le prérequis d'un déploiement continu, pas encore
> fait.

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
| Valid redirect URIs | `http://localhost:3000/*` |
| Web origins | `http://localhost:3000` |

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

`SMTP_PASSWORD` n'est **pas** le mot de passe du compte Google : Gmail le refuse
depuis mai 2022 (`535-5.7.8 Username and Password not accepted`). Générer un
*mot de passe d'application* sur https://myaccount.google.com/apppasswords — la
validation en deux étapes doit être activée au préalable — et coller les 16
caractères sans les espaces. Ce secret ne sert qu'au SMTP et se révoque sans
toucher au mot de passe du compte.

Plutôt que de réexporter ces variables à chaque session, les écrire une fois
dans `.env` (déjà ignoré par git, cf. `.env.example`) puis les charger :

```bash
set -a; source .env; set +a
```

`docker compose` lit `.env` tout seul, mais le backend tourne hors Docker
(`mvn spring-boot:run`) : sans ce `source`, les variables n'atteignent jamais
Spring et l'envoi d'email reste silencieusement désactivé.

Le reste de la configuration (URL de la base, issuer Keycloak, realm, client-id) est déjà dans `backend/smartschool-api/src/main/resources/application.yml` et pointe vers les services du `docker-compose.yml`.

## 3 bis. Variante : API et front hors conteneurs (développement)

Pour travailler dans l'IDE — débogage, rechargement à chaud — il faut sortir
`backend` et `frontend` de la pile. Ils sont derrière le profil `app` du
`docker-compose.yml` ; l'interrupteur tient en **deux lignes du `.env`**, déjà
présentes en commentaire :

```env
#COMPOSE_PROFILES=app
#API_HOST=backend
COMPOSE_PROFILES=
API_HOST=host.docker.internal
```

`docker compose up -d` ne lance alors que l'infra (6 conteneurs), et l'API se
démarre au §4, le front au §5. `API_HOST` dit à l'assistant IA où joindre
l'API : le nom du service Docker en pile complète, l'hôte quand Spring tourne
dans l'IDE. Aucune autre variable ne change entre les deux modes.

En pile complète, le front conteneurisé est publié sur **3000**, le port du dev
server Vite : les redirect URIs du client Keycloak restent valables sans
reconfiguration.

**Ce que le nom de service Docker ne peut pas faire.** Le front est une
application qui s'exécute dans le **navigateur**, pas dans le conteneur nginx :
ses variables `VITE_*` sont figées à la construction de l'image (ce sont des
`args`, pas des `environment`) et doivent désigner des adresses joignables depuis
la machine de l'utilisateur — jamais `backend` ni `keycloak`. Changer l'URL
publique de l'API imposera donc de reconstruire l'image du front.

**Les deux URL Keycloak du service `backend` sont volontairement différentes.**
`KEYCLOAK_ISSUER_URI` vaut `http://localhost:8081/...` : c'est l'identité
inscrite dans le jeton, telle que le *navigateur* a vu Keycloak, et le backend
doit y comparer le claim `iss`. Mais depuis un conteneur, `localhost` désigne le
conteneur lui-même — les clés publiques sont donc téléchargées via
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`, qui utilise le nom de
service Docker `keycloak`. Sans cette seconde variable, Spring tenterait la
découverte OIDC sur l'issuer et échouerait au démarrage.

## 4. Démarrer le backend

```bash
cd backend
mvn -pl smartschool-api -am install -DskipTests   # la première fois seulement
mvn -pl smartschool-api spring-boot:run
```

- API : http://localhost:8080
- Swagger UI : http://localhost:8080/swagger-ui.html

**Deux commandes, et non un `-am` sur `spring-boot:run`.** L'API dépend de cinq
modules frères (`security-module`, `tenant-business`, `organisation-business`,
`smartschool-planning`, `absence-business`) : sans eux dans le dépôt local,
Maven ne résout pas ses dépendances. Mais `-am` étend le
goal au POM parent, qui n'a pas de classe `main` — `spring-boot:run` s'y arrête
sur `Unable to find a suitable main class`. On construit donc les modules
d'abord, on ne lance que l'API ensuite. La première commande devient inutile
tant qu'aucun module frère n'est modifié.

### 4 bis. Le jeu de données de démonstration

```bash
cd backend
mvn -pl smartschool-api spring-boot:run -Dspring-boot.run.profiles=demo
```

Le profil `demo` alimente la base avec deux collèges complets, prêts pour une
génération d'emploi du temps :

| | Ibn Khaldoun | Carthage |
|---|---|---|
| Programme | § T.1 — collège ordinaire | § T.3 — collège pilote |
| Classes | 16 (7×7ᵉ, 5×8ᵉ, 4×9ᵉ) | 10 (4×7ᵉ, 3×8ᵉ, 3×9ᵉ) |
| Élèves | 481 | 304 |
| Enseignants | 50 | 38 |
| Salles | 40 | 28 |
| Affectations | 320 | 210 |
| Théâtre | non assuré | assuré |

Les classes sont codées `7B1`, `7B2`, … et comptent entre 25 et 35 élèves. Les
matières, leurs volumes et le découpage de leurs séances ne sont pas écrits dans
le seeder : chaque collège **hérite** du programme national (circulaire n°66 du
04/09/2024) semé par `NationalPatternSeeder`. Corriger la circulaire à un seul
endroit met donc les deux établissements à jour.

Le runner est idempotent — un second démarrage ne réécrit rien. Pour repartir
d'une base propre :

```bash
mvn -pl smartschool-api spring-boot:run -Dspring-boot.run.profiles=demo \
    -Dspring-boot.run.arguments=--reinitialiser-demo
```

La réinitialisation ne touche qu'aux données pédagogiques des deux collèges :
les établissements eux-mêmes, leurs abonnements et les comptes Keycloak
survivent.

## 5. Démarrer le frontend

```bash
cd frontend
npm install
npm run dev
```

- Application : http://localhost:3000

`frontend/.env.local` contient déjà les valeurs par défaut cohérentes avec Keycloak et le backend démarrés ci-dessus :

```env
VITE_API_URL=http://localhost:8080
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=smartschool
VITE_KEYCLOAK_CLIENT_ID=smartschool-frontend
```

## 6. Se connecter

Ouvrir http://localhost:3000 et se connecter avec l'utilisateur `superadmin` créé à l'étape 2.6 (rôle `PLATFORM_SUPER_ADMIN`).

## 7. Application mobile (facultatif)

L'espace enseignant sur téléphone : cours du jour, appel, cahier de séance,
planning, classes, et un assistant qui répond sur les cahiers passés ou sur un
cours PDF que l'on joint.

```bash
# Renseigner LAN_HOST dans le .env de la racine, puis relancer la pile :
ip -4 addr show | grep -oP '(?<=inet )192\.168\.[0-9.]+' | head -1
docker compose up -d
cd mobile && npm install && npm start
```

Puis scanner le QR code avec **Expo Go**.

> **`LAN_HOST` n'est pas optionnel dès qu'un téléphone entre en jeu.** Un
> téléphone ne peut pas joindre `localhost` : il demande son jeton à Keycloak par
> l'IP du poste, et Keycloak en `start-dev` inscrit alors cette IP comme émetteur
> — que l'API, qui attend `localhost`, rejette. La connexion réussit, puis
> **tous** les écrans reçoivent 401 sans que rien n'explique pourquoi.
> `LAN_HOST` aligne d'un seul coup `KC_HOSTNAME`, l'`issuer` attendu par l'API
> et celui de l'assistant ; si l'API tourne hors Docker, elle doit recevoir le
> même (`KEYCLOAK_ISSUER_URI`).

La procédure complète — variables d'environnement, assistant sur le port 8001,
tableau de dépannage — est dans **`mobile/README.md`**.

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
