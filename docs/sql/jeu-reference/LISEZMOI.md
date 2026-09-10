# Jeu de référence — les deux collèges, figés

> **Ce que c'est.** Un couple de dumps PostgreSQL qui remet la pile dans l'état
> exact d'une démonstration : les deux collèges Ibn Khaldoun et Carthage côté
> application, et le realm `smartschool` complet côté Keycloak. Sur une machine
> neuve, `scripts/restauration.sh docs/sql/jeu-reference` reconstruit les deux
> bases en quelques secondes — le § 2 du README (realm, clients, rôles, groupes)
> n'est plus à refaire à la main.
>
> **Pourquoi versionné, alors que `sauvegardes/` est ignoré par Git.** Un dump
> horodaté est un filet de sécurité, il n'a rien à faire dans l'historique.
> Celui-ci est autre chose : c'est le point de départ reproductible dont dépend
> le déploiement (`docs/plan-cicd.md`, étape 5) et la soutenance. Les données
> sont fictives — noms tunisiens générés par `NomsTunisiens` — donc rien ne
> s'oppose à les suivre dans le dépôt.

## Contenu

Pris le **2026-09-10**, sur le commit **99f6e3e** (branche `jeu-de-donnees-reelles`),
après un démarrage du backend en profil `demo`.

### `app.sql` — base applicative `smartschool`

| | Ibn Khaldoun | Carthage | Total |
|---|---|---|---|
| Élèves | 481 | 304 | **785** |
| Classes | 16 | 10 | **26** |
| Enseignants | 50 | 38 | **88** |
| Affectations | 320 | 210 | **530** |
| Salles | 40 | 28 | **68** |

Plus : 6 programmes nationaux (`national_patterns`), 202 créneaux horaires,
2 années scolaires. Chaque collège porte son `tenant.keycloak_group_id`, qui
pointe vers un groupe du dump Keycloak ci-dessous — **les deux fichiers forment
un couple** (cf. l'en-tête de `scripts/sauvegarde.sh`).

**Contient aussi** 6 emplois du temps déjà générés (`planning_generated_timetable`,
2928 séances) : ce sont des exécutions de test conservées volontairement. Pour
repartir d'un état « prêt à générer », les supprimer après restauration ou
relancer le backend avec `--reinitialiser-demo`.

### `keycloak.sql` — base `keycloak`

Le realm `smartschool` entier :

- clients `smartschool-backend` (confidential, service accounts) et
  `smartschool-frontend` (public)
- rôles realm : `PLATFORM_SUPER_ADMIN`, `SCHOOL_ADMIN`, `TEACHER`,
  `SURVEILLANT`, `PARENT`, `STUDENT`
- les groupes des deux collèges, dont les identifiants sont référencés par
  `tenant.keycloak_group_id` côté application
- 48 utilisateurs (comptes semés + comptes créés pendant les tests)

Le secret du client `smartschool-backend` inscrit dans ce dump est
`RQSLRdMsBUWvHio5vPREGZWZRteNlO40` — il doit être recopié dans `KC_CLIENT_SECRET`
du `.env` de la machine, sans quoi le backend ne démarre pas. Pour une
démonstration, ce n'est pas un secret (cf. `docs/plan-cicd.md` § 6).

## Restaurer

```bash
docker compose up -d          # les bases doivent tourner
scripts/restauration.sh docs/sql/jeu-reference
```

Le script **supprime puis recrée** les bases `smartschool` et `keycloak`, arrête
Keycloak et le backend le temps de l'opération, puis les redémarre. Il propose
de sauvegarder l'état courant avant de commencer — un `sauvegardes/<horodatage>/`
de plus ne coûte rien.

## Regénérer ce jeu

Quand le schéma a bougé (nouvelle migration Liquibase, changement de `NIVEAUX`)
ou quand on veut un instantané plus propre :

```bash
# pile en profil demo, base vide, backend qui a fini de semer
scripts/sauvegarde.sh
cp sauvegardes/<horodatage>/app.sql      docs/sql/jeu-reference/
cp sauvegardes/<horodatage>/keycloak.sql docs/sql/jeu-reference/
# mettre à jour contexte.txt (commit) et les chiffres de ce fichier
```
