# SchoolSys Mobile — l'espace enseignant

Client React Native (Expo). Le backend est **déjà complet** : cette application
n'ajoute aucune règle métier, elle rend accessible en classe, téléphone en main,
ce qui n'a pas sa place devant un ordinateur.

## Les écrans

Quatre onglets qui sont les quatre gestes de la journée — voir où on en est,
faire l'appel, remplir le cahier, demander à l'assistant. Ce qu'on consulte une
fois par semaine (l'emploi du temps, les classes) s'ouvre depuis l'accueil et se
referme : la barre du bas ne porte que le quotidien.

| Écran | Ce qu'on y trouve | API |
|---|---|---|
| **Accueil** (onglet) | La séance en cours, le bouton « Lancer l'appel », les présents/absents du moment, les **absences non justifiées de la classe** et la carte assistant | `/teachers/me`, `/planning/timetable/generated`, `/appel/seances`, `/appel/classes/{id}/signalements` |
| **Appel** (onglet) | Choix de la séance du jour, puis feuille d'appel | `POST /api/v1/appel/seances/ouvrir` |
| **Cahier de texte** (onglet) | Choix de la séance, puis saisie | `GET/POST /api/v1/cahier/seances/{id}` |
| **Assistant IA** (onglet) | Deux usages, un seul interlocuteur : *Mes cahiers* — question libre sur ses propres séances ; *Préparer un cours* — on joint un PDF, puis on demande ce qu'on veut | `POST /api/cahier/assistant/chat`, `POST /api/cours/document`, `POST /api/cours/chat` |
| **Feuille d'appel** | Toute la classe est présente par défaut : on marque les exceptions, **puis on enregistre**. Affiche ce que les collègues ont signalé. Clôture verrouillante | `PATCH /api/v1/appel/lignes/{id}/statut`, `GET /api/v1/appel/classes/{id}/signalements`, `POST /api/v1/appel/seances/{id}/verrouiller` |
| **Cahier de séance** | Sujet, chapitre, activités, travail demandé | `POST /api/v1/cahier/seances/{id}` |
| **Mon emploi du temps**, **Mes classes** | Ouverts depuis l'accueil | `/planning/timetable/jobs/{jobId}/view/teacher/{code}`, `/eleves/classe/{id}/actifs` |

### L'appel s'enregistre en un geste

Chaque tap partait auparavant seul vers le serveur. L'enseignant ne savait jamais
s'il avait fini, une erreur de doigt était immédiatement écrite, et un réseau
capricieux transformait la feuille en série d'états à moitié envoyés. La saisie
vit désormais en local, une barre compte ce qui reste à envoyer, et le bouton
**Enregistrer** engage tout — le geste qu'on faisait déjà en refermant le cahier
papier. Quitter la feuille sans enregistrer demande confirmation.

### Une absence suit l'élève, de séance en séance

Un élève marqué absent en première heure était invisible pour le professeur de
la deuxième : chacun ouvrait sa feuille sur une classe réputée entière.
`GET /api/v1/appel/classes/{id}/signalements` renvoie les absences et exclusions
de la **classe** — et non de l'enseignant — que personne n'a justifiées, sur les
sept derniers jours. Elles s'affichent sur l'accueil et sous le nom de l'élève
dans la feuille d'appel, avec l'heure, la matière et le collègue qui les a
saisies.

La liste ne se vide que d'une façon : la vie scolaire valide un justificatif
depuis le web, ce qui marque la ligne d'appel justifiée. Un enseignant constate,
il ne fait pas taire l'alerte d'un collègue.

Le planning de la semaine n'est **pas** une grille horaire comme sur le web :
la reproduire donnerait des cases de trois millimètres. Un sélecteur de jour et
une liste disent la même chose, et se lisent debout dans un couloir.

**« Préparer un cours » n'impose aucun format de sortie.** On joint le chapitre,
puis on demande : « explique la partie sur la réciproque », « propose quatre
exercices du plus simple au plus difficile », « fais un QCM de cinq questions
avec le corrigé ». Un écran qui aurait produit d'office un résumé et des
exercices aurait interdit tout le reste — la demande de l'enseignant serait
passée après celle du développeur. La seule contrainte imposée au modèle est de
s'appuyer sur le cours fourni et de dire ce qui n'y figure pas.

Le document **n'est jamais écrit sur disque** : il est lu une fois, gardé en
mémoire le temps de la préparation (une heure, vingt documents au plus) et
**rattaché au compte qui l'a envoyé** — un identifiant deviné n'ouvre pas le
cours d'un collègue. Un PDF **scanné** est refusé en nommant la cause plutôt que
résumé au jugé : sans ce contrôle, le modèle recevrait du bruit et inventerait
un cours plausible, la pire sortie possible parce qu'elle est crédible.

**Les corrigés se relisent.** Le format est fiable — les réponses sont groupées
sous « Corrigé », jamais mêlées aux énoncés, sans quoi le QCM ne serait pas
projetable. L'exactitude, elle, ne l'est pas : sur un premier essai, le modèle a
donné `BC = 10 cm` pour un triangle de côtés 3 et 4. L'application le dit à
l'écran plutôt que de le taire.

## Trois décisions, et pourquoi

**L'adresse du serveur se saisit dans l'application.** Un téléphone qui appelle
`localhost` s'appelle lui-même : les services tournent sur le portable et ne sont
joignables que par son IP sur le réseau local — laquelle change d'un réseau à
l'autre. Elle est donc modifiable depuis l'écran de connexion (lien « Serveur »)
et retenue d'une session à l'autre : le jour de la démonstration, on corrige un
champ de texte au lieu de reconstruire le bundle.

**L'authentification réutilise le grant `password` de Keycloak**, comme le web.
OIDC/PKCE serait le choix correct pour une application publiée sur un store — un
mobile ne peut pas garder un secret. Ici l'application n'est pas publiée, elle
est démontrée sur un réseau local, et PKCE + navigateur système coûterait une
journée sans rien démontrer de plus du sujet. Le choix est assumé, pas subi.

**Le retard n'ouvre pas de formulaire.** Le web demande l'heure d'arrivée parce
qu'il régularise souvent après coup ; en classe, l'enseignant marque l'élève au
moment où il entre — l'heure du geste *est* l'heure d'arrivée. L'exclusion, elle,
demande toujours sa raison : le backend la refuse sans (`validerReglesMetier`),
et c'est une décision qui doit rester justifiée.

## Démarrer

### 1. Les services, avec un nom d'hôte fixe pour Keycloak

```bash
export LAN_HOST=$(ip -4 addr show | grep -oP '(?<=inet )192\.168\.[0-9.]+' | head -1)
docker compose up -d   # LAN_HOST renseigné dans le .env de la racine
```

Sans `LAN_HOST`, Keycloak en `start-dev` construit l'émetteur du jeton à partir
de l'en-tête `Host` : un jeton demandé depuis le téléphone porte
`http://<ip>:8081/...` là où l'API attend `http://localhost:8081/...`, et l'API
répond **401 sans rien expliquer**. C'est le blocage classique de ce montage.

### 2. L'API, avec le même émetteur

```bash
export KEYCLOAK_ISSUER_URI="http://$LAN_HOST:8081/realms/smartschool"
export KEYCLOAK_SERVER_URL="http://$LAN_HOST:8081"
export KC_CLIENT_SECRET="<secret du client smartschool-backend>"
mvn spring-boot:run -pl smartschool-api -Dspring-boot.run.profiles=demo
```

Le secret se lit dans la console Keycloak (`admin` / `admin`, client
*smartschool-backend*, onglet *Credentials*), ou par l'API d'administration :

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/master/protocol/openid-connect/token \
  -d 'client_id=admin-cli&username=admin&password=admin&grant_type=password' | jq -r .access_token)
CID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8081/admin/realms/smartschool/clients?clientId=smartschool-backend' | jq -r '.[0].id')
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/admin/realms/smartschool/clients/$CID/client-secret" | jq -r .value
```

### 3. L'assistant (facultatif, pour l'onglet question libre)

Le conteneur `ai-assistant` (port 8000) sert le tableau de bord mais **pas** le
chat : Ollama n'écoute que sur `127.0.0.1` de l'hôte. Pour le chat, lancer
l'instance de développement, en écoutant sur toutes les interfaces afin que le
téléphone puisse l'atteindre :

```bash
cd ai-assistant
export KEYCLOAK_ISSUER_URL="http://$LAN_HOST:8081/realms/smartschool"
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

`--host 0.0.0.0` parce que le défaut n'écoute que la boucle locale — invisible
depuis le téléphone. `KEYCLOAK_ISSUER_URL` pour la même raison que côté API :
le service Python valide lui aussi l'émetteur du jeton.

### 4. L'application

```bash
cd mobile && npm start
```

`npm start` fixe le port de Metro à **8082** : son défaut est 8081, déjà pris par
Keycloak dans ce projet. Sans cela, `expo start` s'arrête sur une question à
laquelle personne ne répond quand la commande tourne en arrière-plan.

Scanner le QR code avec **Expo Go** (Play Store). Le téléphone doit être sur le
**même Wi-Fi** que le portable. À la connexion, ouvrir « Serveur » et vérifier
que l'adresse affichée est bien celle du portable.

## Quand ça ne marche pas

| Symptôme | Cause |
|---|---|
| « Serveur injoignable » à la connexion | Téléphone sur un autre réseau, ou IP changée. Corriger sous « Serveur ». |
| Connexion acceptée puis **401** sur tous les écrans | Émetteur du jeton ≠ `KEYCLOAK_ISSUER_URI` de l'API. Les deux doivent désigner Keycloak par la même URL. |
| « Aucun emploi du temps publié » | Aucun emploi du temps au statut `PUBLISHED`. Les séances déjà ouvertes restent accessibles plus bas. |
| Le chat répond « serveur ne répond pas » | `uvicorn` lancé sur `127.0.0.1` : le relancer avec `--host 0.0.0.0`. |
| « Préparer un cours » refuse le PDF | Document scanné, sans couche de texte. Le message le dit ; l'OCR n'est pas au programme. |
| « Ce document n'est plus disponible » | Le cours a expiré (une heure) ou le service a redémarré. Le rejoindre avec le bouton `+`. |
| « Unsupported FormDataPart implementation » | Une partie multipart a été passée sous la forme `{ uri, name, type }`. Voir ci-dessous — ne pas revenir à cette forme. |

## Le piège du multipart, à ne pas défaire

Depuis le SDK 52, Expo **remplace le `fetch` global** par son implémentation
WinterCG. Son convertisseur multipart (`expo/src/winter/fetch/convertFormData.ts`)
n'accepte que trois formes de partie : une chaîne, un `Blob`, ou un objet
exposant `bytes()`. Il le dit lui-même en commentaire :

> `uri` is not supported for React Native's FormData.

Or `{ uri, name, type }` est **la** forme documentée partout pour React Native,
celle que tout exemple d'upload propose. Elle tombe ici dans le `else` final et
lève `Unsupported FormDataPart implementation` — **avant tout envoi réseau**. Le
symptôme ressemble alors à un serveur injoignable, et on cherche longtemps du
mauvais côté : la trace serveur reste vide, puisque rien n'est parti.

`api/client.ts` passe donc la troisième forme, `{ name, type, bytes() }`. Elle
est préférée au `Blob` natif parce qu'elle laisse choisir le nom de fichier
annoncé au serveur, indépendamment de celui de la copie en cache.

**Deuxième piège, dans la foulée : d'où viennent les octets.** Le premier
correctif lisait le fichier choisi par `expo-document-picker` avec
`expo-file-system` — et échouait sur `missing read permission`. Les deux modules
ne voient pas le même monde dans Expo Go : le sélecteur dépose sa copie dans un
cache **partagé par toutes les expériences**
(`…/host.exp.exponent/cache/DocumentPicker/`), tandis qu'`expo-file-system`
n'autorise la lecture que dans le répertoire de l'expérience en cours.

La sortie n'est pas d'élargir la permission mais de supprimer la frontière :
c'est `expo-file-system` qui ouvre désormais le sélecteur, par
`File.pickFileAsync({ mimeTypes: 'application/pdf' })`. Le module qui LIT le
fichier est celui qui l'a CHOISI — la question de la permission ne se pose plus,
au lieu d'être contournée. `expo-document-picker` n'est plus utilisé.
| `expo start` demande un autre port | Metro veut 8081, Keycloak l'occupe. Utiliser `npm start`, qui impose 8082. |
