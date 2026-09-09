# Récupération du module `pointage` — dossier de suivi

> **Statut : non décidé, rien n'est engagé.** Ce document existe pour qu'à la
> reprise, la question ne soit pas *« est-ce encore récupérable ? »* — la
> réponse est oui, et l'inventaire est ci-dessous — mais *« est-ce qu'on le
> remet, et à quel prix ? »*
>
> Constats datés du **8 septembre 2026**. Chaque affirmation a été vérifiée sur
> le disque à cette date ; les chemins et les noms de méthodes sont à
> re-vérifier avant d'agir, le projet ayant continué d'évoluer.

---

## 1. La question préalable : faut-il le remettre ?

Le pointage n'a pas été perdu. Il a été **supprimé volontairement**, et
l'argument est écrit noir sur blanc dans `plan-recentrage-pfe.md` §1.2 :

> `pointage-business` | 34 | Pointage du **personnel**. Redondant avec l'absence
> **élève**, qui est celle que la consigne encadre.

Le remettre défait une partie du recentrage, et surtout **fragilise la
formulation de soutenance** préparée au même endroit : *« j'ai volontairement
resserré le périmètre sur la chaîne réglementation → planification →
exécution »*. Un jury qui voit revenir le pointage du personnel a le droit de
demander pourquoi la justification ne tient plus.

Deux raisons peuvent malgré tout la rendre légitime — à trancher explicitement,
pas par défaut :

| Raison | Verdict |
|---|---|
| « Le périmètre restant paraît maigre pour la soutenance » | Arbitrage recevable. Mais mesurer d'abord : 8 modules, 562 tests, un solveur conforme à la circulaire. Le volume n'est probablement pas le problème. |
| « Le module existe, autant le remettre » | Non. Le plan a déjà tranché contre, et §3.3 rappelle le coût réel : côté front, la suppression avait demandé le double du temps prévu. La réinsertion aussi. |

**Si la réponse est non, ce document n'a plus d'objet — le supprimer ou le
laisser comme trace de la décision.**

---

## 2. Où se trouve la source

### 2.1 Ce que git ne peut pas faire

Le dépôt de `~/pfe/schoolSys` a été initialisé le **4 septembre 2026**, donc
**après** la suppression. Aucun fichier `pointage` n'apparaît dans son
historique :

```bash
git log --all --name-status -- '*pointage*'   # → vide
```

L'archive `backup-modules-supprimes.tgz` annoncée en fin de §3.4 du plan de
recentrage **est introuvable** sur la machine. Ne pas compter dessus.

### 2.2 Les copies intactes sur le disque

| Chemin | Date | `.java` | changelogs | Retenir ? |
|---|---|---|---|---|
| **`~/schoolSys`** | **23 août 2026** | **34** | **5** | ✅ **source de référence** |
| `~/sc-p` | 9 août 2026 | 34 | 5 | secours |
| `~/copie/c1/sc-p` | 5 juillet 2026 | 32 | 4 | trop ancienne |
| `~/copie/sc-p` | 26 juin 2026 | 32 | 4 | trop ancienne |
| `~/full app` | 25 juin 2026 | 32 | 4 | trop ancienne |

⚠️ `~/schoolSys` et `~/pfe/schoolSys` sont **deux répertoires différents**. Le
premier est la copie d'avant recentrage, le second est le projet vivant. Ne pas
les confondre dans une commande `rm`.

En dernier recours, le jar compilé porte les YAML Liquibase en clair et les
`.class` décompilables :

```
~/.m2/repository/tn/wtm/school/pointage/pointage-business/0.0.1-SNAPSHOT/pointage-business-0.0.1-SNAPSHOT.jar
```

### 2.3 Inventaire — 39 fichiers sur 3 couches

**`backend/pointage-business/`** — 34 `.java` + `pom.xml` + 5 YAML

| Paquet | Contenu |
|---|---|
| `entity` | `PresencePersonnel`, `JustificatifPointage`, `SuiviHeuresEnseignant` |
| `enums` | `Periode`, `TypePersonnel`, `StatutPresencePersonnel`, `TypeJustificatifPointage`, `StatutJustificatifPointage` |
| `dto/requete` · `dto/reponse` | 5 requêtes · 6 réponses |
| `mapper` · `repository` | 3 mappers MapStruct · 3 repositories |
| `service` (+ `impl`) | `ServicePointage`, `ServiceJustificatifPointage`, `ServiceStatistiquesPointage`, `ServiceSuiviHeures` |
| `port` | `PortMembrePersonnel` — l'interface qui évite de coupler pointage à organisation |
| `resources/db/changelog.pointage/` | `001` présence · `002` justificatif · `003` suivi-heures · `004` période · `db.changelog-master.yaml` |

**`backend/smartschool-api/.../api/pointage/`** — 5 fichiers

| Fichier | Route | Rôles |
|---|---|---|
| `PointageController` | `/api/v1/pointage` | `SCHOOL_ADMIN`, `TEACHER`, `SURVEILLANT` |
| `JustificatifPointageController` | `/api/v1/pointage/justificatifs` | `SCHOOL_ADMIN`, `TEACHER`, `SURVEILLANT` |
| `SuiviHeuresController` | `/api/v1/pointage/suivi-heures` | `SCHOOL_ADMIN`, `TEACHER` |
| `StatistiquesPointageController` | `/api/v1/statistiques/pointage` | `SCHOOL_ADMIN`, `TEACHER` |
| `adapter/PortMembrePersonnelAdapter` | — | implémente le port sur `TeacherRepository` + `SchoolUserRepository` |

**`frontend/src/`** — 5 fichiers

`features/pointage/` : `PointageJournalier`, `HistoriquePointage`,
`JustificatifsPointage`, `SuiviHeuresEnseignants` · plus `api/pointage.api.ts`.

---

## 3. Ce qui a changé depuis — le vrai travail

Ce n'est **pas** un copier-coller. Quatre écarts vérifiés entre la copie du
23 août et le projet d'aujourd'hui.

### 3.1 Les coordonnées Maven ont été renommées ⚠️ bloquant

Le projet est passé de `tn.wtm` à `tn.schoolsys`, avec préfixe `schoolsys-` sur
tous les artifactId :

| | copie du 23 août | `~/pfe/schoolSys` aujourd'hui |
|---|---|---|
| parent | `tn.wtm:school-platform` | `tn.schoolsys:schoolsys-platform` |
| module | `tn.wtm.school.pointage:pointage-business` | à créer : `tn.schoolsys.pointage:schoolsys-pointage-business` |

**Les paquets Java, eux, n'ont pas bougé** — `tn.wtm.school.pointage.*` reste
valide, `tn.wtm.school.org.entity.Teacher` existe toujours. Seul le `pom.xml`
du module est à réécrire ; **aucun `import` Java n'est à toucher.** Prendre
`absence-business/pom.xml` comme gabarit.

### 3.2 Le changelog Liquibase n'a jamais été exécuté ⚠️ à corriger, pas à reproduire

Constat : **rien n'inclut `db/changelog.pointage/db.changelog-master.yaml`.**
Il ne s'inclut que lui-même, et le master applicatif ne le référence pas — ni
avant, ni après le recentrage. §3.1 du plan le dit d'ailleurs sans en tirer la
conséquence : *« budget et pointage ont leur propre master, non inclus »*.

Les tables `pointage_*` existaient donc uniquement parce que
`application.yml:14` porte `ddl-auto: update` : **c'est Hibernate qui les a
créées, pas Liquibase.** Les 4 migrations sont du code mort.

Conséquences :

- En `prod`, `ddl-auto: validate` (`application-prod.yml:4`) : le module
  **n'aurait jamais démarré**. C'est un défaut latent, pas une régression du
  recentrage.
- Réinsérer le module *tel quel*, c'est réimporter le défaut. La correction
  tient en un `include` dans
  `smartschool-api/src/main/resources/db.changelog/db.changelog-master.yaml`,
  exactement sur le modèle de la délégation au master planning.
- `ChangelogMasterTest` documente précisément ce défaut (« une liste affichée,
  une autre appliquée », défaut P1 du plan de conformité). Y **ajouter un cas**
  pour le master pointage plutôt que de le contourner.

### 3.3 Le master applicatif a été restructuré

Il ne recopie plus les migrations planning une par une : il délègue au master du
module. Ne pas recoller l'ancien bloc — suivre la convention actuelle.

### 3.4 Le front a été refondu en cartes

Plusieurs commits post-suppression (`feat(ui): les écrans d'absences passent en
cartes`, `feat(ui): la section organisation passe en cartes`) ont changé le
langage visuel. Les 4 écrans de la copie sont dans **l'ancien style** : ils
compileront, mais jureront. Prévoir une passe de mise au niveau, ou assumer
l'écart.

### 3.5 Bonne nouvelle : l'adaptateur est intact

Tout ce dont `PortMembrePersonnelAdapter` a besoin existe encore, à
l'identique :

| Dépendance | État |
|---|---|
| `TeacherRepository.findByTenantIdAndIdEnseignantIn` | ✅ `TeacherRepository.java:25` — encore utilisée par `PortContexteScolaireAdapter` (absence) |
| `SchoolUserRepository.findByTenantIdAndIdIn` | ✅ `SchoolUserRepository.java:18` — **plus aucun appelant** depuis la suppression |
| `Teacher.idEnseignant`, `SchoolUser.nomComplet` | ✅ inchangés |
| `PersonnelDisponibleService` (planning) | ✅ toujours là, sert `/enseignants-disponibles` |

Autrement dit : **la couche backend se rebranche presque mécaniquement.** Le
commentaire de `TeacherController.java:32` (« pointage du personnel, planning
enseignant ») et celui de `ci.yml:114` sont les cicatrices laissées exprès.

---

## 4. Quand : **avant le CD**

Le CD n'est pas fait — `ci.yml` construit les 3 images avec `push: false`, et le
plan §7 l'annonce comme *« la suite naturelle »*. Si la réinsertion a lieu, elle
doit la précéder :

1. **Liquibase.** Le CD suppose de jouer les migrations sur un environnement qui
   persiste. Ajouter 4 changelogs — jamais exécutés à ce jour — après coup, c'est
   se fabriquer une migration en production sans nécessité.
2. **Réacteur Maven.** Le CD se construit sur la composition du build. La changer
   ensuite oblige à revalider la chaîne entière.
3. **SonarCloud.** Le gate porte sur le *nouveau code*. 39 fichiers d'un coup sur
   un pipeline fraîchement déclaré vert, c'est un gate rouge au pire moment.
4. **Front.** `tsc -b` est le filet qui avait rattrapé les imports morts à la
   suppression (`ci.yml:114`) ; il doit servir avant que le build ne serve à
   déployer.

---

## 5. Mode opératoire

Backend d'abord — le compilateur signale les oublis. Front ensuite.

### 5.1 Backend

```bash
cd ~/pfe/schoolSys/backend

# 1. Le module (attention : ~/schoolSys, pas ~/pfe/schoolSys)
cp -r ~/schoolSys/backend/pointage-business .

# 2. Réécrire pointage-business/pom.xml aux coordonnées tn.schoolsys
#    (gabarit : absence-business/pom.xml) — §3.1

# 3. pom.xml parent : <module>pointage-business</module> après absence-business

# 4. smartschool-api/pom.xml : la <dependency> aux NOUVELLES coordonnées

# 5. coverage-report/pom.xml : ajouter la dépendance, sinon le module
#    n'apparaît pas dans l'agrégat JaCoCo et Sonar le voit à 0 %

# 6. Contrôleurs + adaptateur
cp -r ~/schoolSys/backend/smartschool-api/src/main/java/tn/wtm/school/api/pointage \
      smartschool-api/src/main/java/tn/wtm/school/api/

# 7. db.changelog-master.yaml : ajouter la délégation — §3.2
#      - include:
#          file: db/changelog.pointage/db.changelog-master.yaml

mvn -o clean install -DskipTests
```

### 5.2 Base de données

Les tables `pointage_*` traînent encore dans la base de développement, créées
jadis par `ddl-auto`. Elles ne correspondent pas forcément à ce que les
changelogs décrivent. **Ne pas bricoler à la main** :

```bash
docker compose down -v && docker compose up -d
mvn spring-boot:run -pl smartschool-api -Dspring-boot.run.profiles=demo
```

Vérifier dans les logs que les 4 changesets `changelog.pointage` s'exécutent
réellement. S'ils n'apparaissent pas, l'`include` de l'étape 7 est manquant ou
mal placé.

### 5.3 Frontend

```bash
cd ~/pfe/schoolSys/frontend
cp -r ~/schoolSys/frontend/src/features/pointage src/features/
cp    ~/schoolSys/frontend/src/api/pointage.api.ts src/api/
```

Puis les branchements — c'est ici que le temps passe (§3.3 du plan de
recentrage : cinq tableaux de bord avaient dû être retouchés à la main) :

| Fichier | À remettre |
|---|---|
| `router/index.tsx` | 4 imports + 4 routes : `pointage`, `/justificatifs`, `/historique`, `/enseignants` |
| `components/layout/Sidebar.tsx` | groupe « Pointage » (icône `Clock`) dans **`buildAdminNav`** (4 entrées) **et `buildSurveillantNav`** (3 entrées, sans `attendanceTeacherHours`) |
| `public/locales/{fr,ar,en}/translation.json` | 5 clés × 3 langues : `nav.attendance`, `attendanceDaily`, `staffJustifications`, `attendanceHistory`, `attendanceTeacherHours` — vérifiées absentes du projet actuel |
| `surveillance/DashboardSurveillant.tsx` | *facultatif* : 2 requêtes pointage, 1 KPI, encart justificatifs personnel — remplacés par le KPI « Appels encore ouverts », qu'il faudra décider de garder ou non |
| `rapports/RapportsAnalyses.tsx` | *facultatif* : le graphe de présence personnel |

`npm run build` (`tsc -b` inclus) est le filet : il signalera tout import mort.

---

## 6. Critère de fin

- [ ] `mvn -o clean install` vert — **9 modules** (8 + pointage)
- [x] `mvn -o verify` vert sur les 9 modules — **934 tests**, 0 échec
- [ ] Les 4 changesets `changelog.pointage` **apparaissent dans les logs
      Liquibase** au démarrage sur base neuve (§5.2)
- [x] `ChangelogMasterTest` étendu au master pointage — §3.2
- [ ] `npm run build` vert · `oxlint` sans erreur nouvelle
- [ ] Les 4 écrans s'ouvrent et affichent des données du jeu de démo
- [x] Couverture du module : **100 %** (lignes, branches, méthodes, classes), 76 tests
- [ ] Sonar : quality gate vert sur le nouveau code — à confirmer au prochain *push*
- [x] `plan-recentrage-pfe.md` §1.2 **corrigé** — la ligne « pointage = supprimé »
      devient fausse, et l'argument de soutenance avec elle
- [ ] Seulement ensuite : attaquer le CD

---

## 7. Journal

| Date | Décision / action | Par |
|---|---|---|
| 2026-09-08 | Inventaire et faisabilité établis. Aucune action engagée. | — |
| 2026-09-08 | **Réinsertion faite.** Module copié depuis `~/schoolSys`, coordonnées Maven réécrites en `tn.schoolsys.pointage:schoolsys-pointage-business`, 9 modules au réacteur. Include Liquibase ajouté au master applicatif — le défaut §3.2 est corrigé, pas réimporté — et `ChangelogMasterTest` étendu (6 cas). Front : 4 écrans, 4 routes, nav admin (4 entrées) et surveillant (3), 5 clés × 3 langues. `mvn install` et `mvn test` verts, `npm run build` et `oxlint` verts. **Vérification Liquibase faite** le 9 septembre sur base applicative neuve : les 7 changesets pointage s'exécutent, l'application démarre. Réinitialisation ciblée du seul volume `sc-p_app_pg_data` — le `down -v` du §5.2 aurait aussi détruit le realm Keycloak, ce que ce plan ne signalait pas. Dump préalable : `~/pfe/dump-smartschool-20260909-0005.sql`. **Restent :** ouverture des 4 écrans sur le jeu de démo, passage Sonar, écrans en ancien style visuel (§3.4). | — |
| 2026-09-09 | **Tests écrits : 76, module à 100 %** (lignes, branches, méthodes, classes). Quatre services et les trois mappers, aucun n'était couvert. Trois règles n'étaient garanties par rien : un membre n'est pointé qu'une fois par créneau, un retard sans durée n'est pas un retard, et l'auteur de la saisie vient du compte connecté. Le test le plus utile porte sur le mapper : `toEntity` ignore `saisiPar` et `saisiA`, sans quoi un client pourrait attribuer sa saisie à un collègue et l'antidater. Un test échouera volontairement le jour de l'intégration planning — `synchroniserDepuisPlanning` doit lever, pas se taire. `mvn -o verify` vert, 934 tests, agrégat à 45,4 %. | — |
