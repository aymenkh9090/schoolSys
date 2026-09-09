# Plan — chaîne CI/CD locale et préservation du jeu de démonstration

> **Objet.** Fermer le dernier maillon de la chaîne : passer de « les images se
> construisent » à « les images sont publiées, puis déployées », **sans jamais
> perdre les deux collèges de démonstration**.
>
> **Ce plan ne crée pas la CI : elle existe et elle est verte.** Les quatre jobs
> de `.github/workflows/ci.yml` testent le backend, le front et l'assistant, et
> construisent les trois images. Ce qui manque tient en un mot : `push`.
>
> **La moitié du plan ne parle pas de CI/CD mais de données.** Publier une image
> est une affaire de dix lignes ; garantir qu'Ibn Khaldoun et Carthage sont
> toujours là après un déploiement en demande davantage, pour une raison qui
> n'est pas évidente à la lecture du code — cf. § 2.
>
> **Chantier voisin :** `docs/plan-assistant-conflits.md`, dont les cinq étapes
> sont faites. Celui-ci ne touche à aucune fonctionnalité — il ne déplace que
> des artefacts et des données.

---

## 0. Où nous en sommes

| Étape | État |
|---|---|
| 1 — Publier les trois images sur Docker Hub | **faite** — job `docker` sous garde de secret, étiquettes `latest` + `sha-<commit>`, `image:` sur les trois services du compose |
| 2 — Le profil `demo` atteint les conteneurs, et devient pilotable | à faire |
| 3 — Sauvegarder les deux bases, ensemble | à faire |
| 4 — Figer le jeu de référence | à faire |
| 5 — Le déploiement local, sur runner self-hosted | à faire |

### Ce qui existe déjà

**La CI est complète et elle passe.** `.github/workflows/ci.yml` — quatre jobs :
`backend` (`mvn verify`, JaCoCo agrégé, SonarCloud sous garde de secret),
`frontend` (`npm ci`, lint, `npm run build` qui embarque `tsc -b`),
`ai-assistant` (`pytest`), et `docker` (l. 147) qui construit les trois images
derrière `needs: [backend, frontend, ai-assistant]`, avec buildx et cache GHA.

**Les trois images se construisent déjà.** `backend/Dockerfile` est un
multi-étages JDK 21 → JRE, utilisateur non privilégié, cache Maven BuildKit.
Les contextes `./ai-assistant` et `./frontend` sont déclarés dans le job.

**La pile tourne en une commande.** `docker compose up -d` lève huit conteneurs :
Keycloak et sa base, la base applicative, Prometheus, Grafana, l'assistant, le
backend et le front (ces deux derniers derrière le profil Compose `app`).

**Les données ont déjà un logement durable.** Deux volumes nommés :
`app_pg_data` (l. 71) pour la base applicative, `keycloak_pg_data` (l. 14) pour
les comptes. Ils survivent à `docker compose down`.

**Le jeu de démonstration est écrit et testé.** `DemoDataRunner` sème deux
collèges complets — 26 classes, 785 élèves, 88 enseignants, 530 affectations —
qui **héritent** du programme national semé par `NationalPatternSeeder`.
`VerificationJeuDeDonneesIT` en contrôle le résultat.

---

## 1. Le problème

- **P1 — La chaîne s'arrête au build.** Les trois étapes du job `docker` portent
  `push: false` (l. 160, 168, 176). La CI **prouve** que les images se
  construisent ; elle n'en laisse aucune trace exploitable. Rien à tirer, rien à
  déployer, rien à remettre en place après un incident.

- **P2 — Le profil `demo` n'atteint jamais les conteneurs.** `DemoDataRunner`
  est un `@Profile("demo")` (l. 123) et `SPRING_PROFILES_ACTIVE` n'apparaît
  **nulle part** — ni dans `backend/Dockerfile`, ni dans `docker-compose.yml`.
  La ligne `ENVIRONMENT: demo` du compose (l. 237) n'y change rien : elle n'est
  lue que par le tag Micrometer d'`application.yml` (l. 110). Les deux collèges
  n'existent donc que lorsque l'API est lancée depuis Maven
  (`README.md:251`). Conséquence directe pour l'étape 1 : une image publiée,
  tirée sur n'importe quelle machine, démarre sur une **base vide**.

- **P3 — Rien ne protège les deux tenants d'une purge.** `DemoDataRunner.run()`
  appelle `purger()` quand `structureHeritee(tenantId)` est vrai — c'est-à-dire
  dès qu'il existe un `Level` dont le code n'est plus dans la constante `NIVEAUX`
  (l. 1053). Le test est faux aujourd'hui. Il deviendra vrai au premier commit
  qui touche `NIVEAUX` — et le déploiement suivant videra les 25 tables de
  `TABLES_A_PURGER` (l. 1070) pour les deux collèges, **`planning_generated_timetable`
  et `planning_timetable_session` compris**. L'emploi du temps que le jury vient
  de générer disparaît, et le seul signal est une ligne `warn` (l. 1085).

- **P4 — Un volume n'est pas une sauvegarde.** Il vit sur la même machine, dans
  le même Docker que la pile. Un `down -v` de trop, un `docker volume prune`,
  un disque qui lâche : rien ne reste. Aujourd'hui le projet n'a aucun dump.

---

## 2. Cause

Le jeu de démonstration a été conçu comme un **outil de développement** — on le
relance quand on veut, depuis l'IDE, sur une base qu'on ne regrette pas. D'où le
`@Profile`, d'où la purge automatique sur détection d'ancienne structure : deux
mécanismes qui rendent service tant que la base est jetable.

Le déploiement renverse cette hypothèse. La base cesse d'être jetable au moment
précis où quelqu'un l'utilise pour une démonstration — et c'est le même
démarrage d'application qui, jusque-là, la reconstruisait sans dommage.

Les deux garde-fous du plan (§ étapes 2 et 3) ne corrigent donc pas un défaut du
seeder : ils lui retirent le droit de s'exécuter là où il n'était pas prévu.

---

## 3. Ordre d'exécution

### Étape 1 — Publier les trois images sur Docker Hub — **faite**

Le job `docker` gagne trois choses, et n'en perd aucune :

- `docker/login-action`, sous garde de secret comme l'est déjà l'analyse Sonar :
  sans `DOCKERHUB_TOKEN`, le job construit sans publier au lieu d'échouer. Un
  dépôt cloné par un tiers doit rester vert.
- `docker/metadata-action` pour les étiquettes : `:latest` **et** `:sha-<court>`.
  Le tag par commit n'est pas décoratif — c'est la seule chose qui rende un
  retour arrière possible. Sans lui, « revenir à hier » n'a pas d'adresse.
- `push: ${{ github.ref == 'refs/heads/main' }}` : on construit sur chaque PR,
  on ne publie que depuis `main`.

Les secrets `DOCKERHUB_USERNAME` et `DOCKERHUB_TOKEN` se posent dans
Settings → Secrets → Actions. **Un jeton d'accès personnel, jamais le mot de
passe du compte.**

Le compose déclare en parallèle un `image:` à côté de chaque `build:` sur les
trois services applicatifs — `image: aymenzak21/schoolsys-backend:${TAG:-latest}`
et ses deux sœurs. Un second fichier `docker-compose.prod.yml` aurait dupliqué
huit services pour n'en changer que trois, et les deux copies auraient divergé
au premier ajout. Avec les deux clés, `docker compose build` continue de
construire en local et `docker compose pull` sait quoi tirer.

**Livrée.** Le compte est `aymenzak21`, les trois dépôts sont
`schoolsys-backend`, `schoolsys-frontend` et `schoolsys-ai-assistant`. `TAG` est
documenté dans `.env.example` : absent il suit `latest`, renseigné il fixe la
version — c'est le geste de retour arrière.

**Trois décisions prises en écrivant le job :**

- **La condition de publication est calculée une fois**, dans un step
  `publication` que les trois étapes consultent. Répétée trois fois, elle aurait
  fini par diverger — et une pile dont une image sur trois a été publiée est une
  pile que personne n'a jamais testée.
- **Le front est construit sans `build-args`.** Les `ARG` de `frontend/Dockerfile`
  valent déjà `localhost`, ce qui est exactement ce qu'une image publique doit
  porter : elle est destinée à une pile lancée *sur* la machine qui la consulte.
  Passer `LAN_HOST` ici graverait l'IP d'un runner GitHub dans le bundle.
- **Le résumé du run liste les étiquettes publiées** et la commande de
  déploiement correspondante. Les retrouver autrement demande de rouvrir les
  logs du job et de lire la sortie de buildx.

**Reste à faire côté GitHub, et personne ne peut le faire à ta place :** poser
`DOCKERHUB_USERNAME` (`aymenzak21`) et `DOCKERHUB_TOKEN` dans Settings →
Secrets and variables → Actions. Tant qu'ils manquent, le job construit sans
publier — il ne casse pas.

### Étape 2 — Le profil `demo` atteint les conteneurs, et devient pilotable

Une seule ligne sur le service `backend` du compose :

```yaml
SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-demo}
```

Elle règle P2 et P3 d'un coup, et c'est pour cela que les deux ne font qu'une
étape :

- **`demo` par défaut** — une pile levée sur une base neuve se peuple, ce qui
  est le comportement qu'on attend d'une image de démonstration ;
- **vide quand on veut** — un `SPRING_PROFILES_ACTIVE=` dans le `.env` de la
  machine et `DemoDataRunner` n'est même plus instancié. Aucune purge n'est
  alors possible, quoi qu'il advienne de `NIVEAUX`.

C'est une garantie plus forte qu'un `smartschool.demo.reinitialiser=false` :
celui-ci ne désarme que la purge *demandée*, pas celle déclenchée par
`structureHeritee`.

`.env.example` porte la consigne, en toutes lettres : **`demo` au premier
démarrage, vide dès que la base contient quelque chose qu'on regretterait.**

### Étape 3 — Sauvegarder les deux bases, ensemble

Deux scripts dans `scripts/`, `sauvegarde.sh` et `restauration.sh`, chacun
opérant sur **les deux** bases en un seul geste :

```bash
docker compose exec -T app-db      pg_dump -U postgres smartschool
docker compose exec -T keycloak-db pg_dump -U keycloak  keycloak
```

**Pourquoi les deux, toujours.** `DemoDataRunner` crée les comptes par
`KeycloakAdminService` : les `school_user` vivent dans `app_pg_data`, les
identifiants correspondants dans `keycloak_pg_data`. Restaurer l'un sans
l'autre donne des utilisateurs qui pointent vers des comptes absents. Le
symptôme ressemble à une panne d'authentification, et se cherche du mauvais
côté. **Les deux volumes forment un couple ; les scripts refusent de les
dissocier.**

Le dossier des sauvegardes est ignoré par Git — un dump horodaté n'a rien à
faire dans l'historique.

### Étape 4 — Figer le jeu de référence

Une fois la base semée et vérifiée, le couple de dumps est figé dans
`docs/sql/`, à côté de `rattrapage-volumes-t1.sql`. C'est le **jeu de
référence** : sur une machine neuve, Ibn Khaldoun et Carthage se remettent en
place en quelques secondes, sans rejouer le seeder ni recréer les comptes
Keycloak, et à l'identique.

Les données sont fictives — noms tunisiens générés par `NomsTunisiens` — donc
rien ne s'oppose à les versionner.

Bénéfice qui dépasse le déploiement : le jour de la soutenance, le jeu montré
ne dépend plus de l'exécution correcte d'un runner sur une base inconnue.

### Étape 5 — Le déploiement local, sur runner self-hosted

Un second workflow, `deploy.yml`, en `workflow_dispatch` — déclenchement manuel,
pas automatique. Sur un runner self-hosted installé sur la machine de
démonstration, dans un répertoire de travail **fixe** :

```
sauvegarde des 2 bases  →  docker compose pull  →  docker compose up -d  →  curl actuator/health
```

**Trois points qui décident si ça marche ou pas :**

- **Le répertoire de travail est fixe, pas le checkout jetable du runner.** Le
  `.env` contient `KC_CLIENT_SECRET`, `DB_PASSWORD`, `SMTP_PASSWORD` ; il est
  gitignoré (`.gitignore:23`), donc il n'existe **que** sur la machine. Un
  `docker compose` lancé ailleurs ne le trouve pas, le placeholder
  `${KC_CLIENT_SECRET}` d'`application.yml` reste non résolu, et le backend
  refuse de démarrer.
- **La machine a besoin du dépôt, pas seulement des images.** Le compose monte
  quatre fichiers de `./monitoring` dans Prometheus et Grafana (l. 90, 126, 129,
  132). Des images seules ne suffisent pas : il faut un `git pull` sur la
  machine cible avant le `docker compose pull`.
- **Le smoke test conclut le job.** Un `curl` sur l'actuator du backend (port
  9091) qui échoue le job si la pile ne remonte pas. Un déploiement qui se
  déclare réussi sans avoir rien vérifié est pire qu'un déploiement manuel : il
  déplace la découverte de la panne au moment de la démonstration.

---

## 4. La règle à ne pas casser

**Aucun `down -v` dans un script ou un workflow de ce dépôt.** C'est le seul
`docker compose` qui détruit les volumes, donc le seul geste capable d'effacer
Ibn Khaldoun et Carthage. Un `down` seul est sans danger et suffit toujours.

Corollaire : les scripts de l'étape 3 sauvegardent **avant** toute opération qui
recrée des conteneurs, jamais après.

---

## 5. Fichiers concernés

| Couche | Fichier |
|---|---|
| CI | `.github/workflows/ci.yml` (job `docker`, l. 147) |
| CD | `.github/workflows/deploy.yml` (à créer) |
| Pile | `docker-compose.yml` (services `backend`, `frontend`, `ai-assistant`) |
| Configuration | `.env.example` |
| Sauvegarde | `scripts/sauvegarde.sh`, `scripts/restauration.sh` (à créer) |
| Jeu de référence | `docs/sql/` |
| Seeder concerné | `smartschool-api/.../api/demo/DemoDataRunner.java` |
| Documentation | `README.md` § 4 bis |

---

## 6. Ce que ce plan ne fera pas

**Il ne déploie pas dans le cloud.** La cible est la machine de démonstration,
et c'est un choix, pas un pis-aller : le déploiement et la supervision doivent
rester observables pendant une soutenance. Docker Hub sert de registre parce
qu'il est public et gratuit, pas parce qu'un serveur distant est prévu.

**Il ne rend pas le front reconfigurable après build.** `VITE_API_URL` et
`VITE_KEYCLOAK_URL` sont des `args:` de build (l. 261-262) : Vite les fige à la
compilation. Une image publiée porte donc définitivement les valeurs passées par
la CI — `localhost`. C'est exact pour une démonstration au navigateur sur la
machine cible, et faux dès qu'un téléphone entre en jeu, puisque `LAN_HOST` ne
peut plus agir sur le front. Rendre ces URL lisibles à l'exécution demande un
`config.js` servi par nginx et lu au démarrage de l'application : c'est un autre
chantier, et il n'est pas ouvert ici. **Pour la démonstration mobile, on
continue de construire le front localement.**

**Il ne met en place aucune politique de secrets.** Les mots de passe restent
dans un `.env` sur la machine. C'est tenable pour un poste de démonstration ;
ça ne le serait pas pour un déploiement réel, et le dire ici évite de le
découvrir en question de jury.
