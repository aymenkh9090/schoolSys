# Plan de mise en conformité — circulaire n°66 du 04/09/2024

> **Objet.** Rendre les emplois du temps produits par Timefold conformes à la
> circulaire ministérielle, et pas seulement faisables au sens du solveur.
>
> **Règle de priorité, dans cet ordre :**
> circulaire officielle > règles métier du code actuel > hypothèses.
> Quand le code diverge du texte, c'est le texte qui gagne.
>
> **Source unique de vérité.** `ai-assistant/data/corpus-consigne-2024.md`,
> transcrit à la main depuis `docs/consigne.pdf` (scan arabe sans couche texte).
> 22 articles. Toute règle citée ci-dessous porte son § d'origine.

---

## 0. Où nous en sommes

| Étape | État |
|---|---|
| A — Verrou catalogue ↔ solveur | **fait** — `CatalogueProviderCoverageTest`, 9 codes non câblés recensés |
| B2 — Quinzaine (`weekParity`) | **fait** — champ, parité des conflits, 9 tests |
| B1 — Durées `(N)` des séances de groupe | **fait** — seeder corrigé + 5 tests, 45 lignes de données rétablies |
| B3 — Alignement `total_hours` / `heures_semaine` | **fait** — 0 pattern incohérent, tous établissements |
| C — Les deux contraintes dures manquantes | **fait** — `RESPECT_OFFICIAL_SUBJECT_HOURS` et `PHYSICAL_EDUCATION_THREE_SESSIONS` câblées, 13 tests ; `NON_CABLEES` tombe de 9 à 7 |
| D — Règles de la circulaire absentes | à faire |
| E — Validation métier post-solve | à faire |
| F — Correction de la consécutivité | à faire |

### Où reprendre

**Prochaine action : étape D**, dans cet ordre de valeur.

| # | Règle | § | Sévérité | Note d'implémentation |
|---|---|---|---|---|
| 1 | Matière à 2 h/semaine : jamais deux jours consécutifs | III.2.c | MEDIUM | Vise histoire-géo, éducation islamique et civique. Regrouper par classe + matière, comparer les jours deux à deux. |
| 2 | 24 h entre deux séances d'EPS | III.2.b | MEDIUM | **Hypothèse à confirmer** : mesure de début à début (§ 1.6, point 5). Complète `PHYSICAL_EDUCATION_THREE_SESSIONS`, qui ne regarde que le découpage. |
| 3 | 2 h minimum par demi-journée non vide | I.2 | HARD | Le plafond de 6 h existe déjà (`MAX_STUDENT_HOURS_PER_DAY`) ; c'est le plancher qui manque. Ne s'applique **pas** à l'EPS — la circulaire l'exclut nommément. |
| 4 | ¾ des matières fondamentales le matin | III.2.a | MEDIUM, en récompense | Arabe, français, mathématiques. `Lesson.mainSubject` existe déjà et n'est utilisé nulle part. On ne peut pas interdire l'après-midi : un quart de l'horaire doit y aller. |
| 5 | Stabilité de salle sur une demi-journée | I.4 | MEDIUM | Sauf matières en salle spécialisée — l'exception est la condition de faisabilité du § III.4. |
| 6 | Alternance restreinte aux 4 premiers jours | II.4 | MEDIUM | **Correction** de `BALANCED_MORNING_AFTERNOON`, qui équilibre aujourd'hui sur toute la semaine, vendredi et samedi compris. |
| 7 | Deux niveaux minimum par enseignant | II.5 | SOFT | Retire `TEACHER_MIN_TWO_LEVELS` de `NON_CABLEES`. |

**Points ouverts à trancher avant ou pendant :**

1. **`COLLEGE_*_OFFICIEL` : T.1 ou T.3 ?** Le seed donne MATH 6 h et EN 5 h en
   `2+1+1+1`. Le § T.1 (collège) dit MATH 4 h et EN `(2)+1+1` 4 h ; le § T.3
   (pilote) dit MATH 5 h et EN `(2)+1+1+1` 5 h. Six heures de mathématiques ne
   correspondent à aucun des deux, et la séance de groupe de l'anglais a
   disparu dans les deux cas. Ramener MATH à 4 h retire des heures à toutes les
   classes : **décision non prise**.
2. **Mesure des 24 h du § III.2.b** — de début à début, hypothèse retenue.
3. **Alternance des quinzaines** — `LessonGenerator.mapWeekParity()`, hypothèse
   retenue (§ 1.6, point 6).

**Dette identifiée, non traitée :** `noStudentIdleGaps` (§ I.5) regroupe les
séances sans regarder la parité de semaine. Une séance de quinzaine y crée donc
un trou la semaine où elle n'a pas lieu, ou en masque un. La compacité devrait
s'évaluer semaine impaire et semaine paire séparément. À reprendre avec le
point 3 de l'étape D.

**Restant dans `NON_CABLEES`** (7 codes) : les 4 SOFT jamais implémentées
(`BALANCED_TEACHER_WORKLOAD`, `TEACHER_MIN_TWO_LEVELS`,
`MAIN_SUBJECT_BALANCED_DISTRIBUTION`, `BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS`)
et les 3 appliquées en dur mais non pilotables depuis l'interface
(`NO_STUDENT_IDLE_GAPS`, `SPECIAL_ROOM_REQUIRED`, `SPECIAL_ROOM_NO_OVERLAP`).

**État des tests au moment de l'interruption :** `smartschool-planning` 260,
`smartschool-api` 5, `organisation-business` 300, `absence-business` 10 —
**0 échec**.

**Pour relancer une génération et mesurer l'effet** : l'API exige un compte
porteur du rôle `SCHOOL_ADMIN` et du claim `tenant_id` ; le compte de service
Keycloak ne les a pas. Passer par le bouton « Générer » de l'écran Emploi du
temps.

---

## 1. Les règles de la circulaire

### 1.1 Élève et classe (p. 2)

| § | Règle | Nature retenue |
|---|---|---|
| I.1 | Une matière, dans une classe, est confiée à **un seul** enseignant | Affectation — hors solveur, déjà structurel (`TeachingAssignment`) |
| I.2 | **6 h/jour au maximum** par classe, **2 h au minimum** par demi-journée. *Ne s'applique pas à l'EPS* | HARD |
| I.3 | **2 h de séparation** entre matin et après-midi, quel que soit le temps scolaire | HARD |
| I.4 | Pas de changement de salle pour une classe dans une demi-journée, **sauf** matières en salle spécialisée | MEDIUM |
| I.5 | **Heures creuses interdites** chez l'élève | HARD |
| I.6 | Équilibre des effectifs entre classes et entre les deux groupes | Hors solveur (`ClassGroup`) |

### 1.2 Enseignant (p. 2–3)

| § | Règle | Nature retenue |
|---|---|---|
| II.1 | Journées de **formation pédagogique** respectées | HARD (indisponibilité) |
| II.2 | **6 h/jour max, 2 h min** par demi-journée, **et répartition équilibrée sur la semaine** | HARD (plafond) + MEDIUM (équilibre) |
| II.3 | **5 h consécutives** autorisées les vendredi et samedi | Dérogation à la consécutivité |
| II.4 | **Alternance** matin/après-midi sur les **4 premiers jours** de la semaine | MEDIUM |
| II.5 | Au moins **deux niveaux** différents par enseignant | SOFT |
| II.6 | Assurer le volume horaire dû ; heures supplémentaires possibles | Donnée d'entrée, pas une contrainte |

### 1.3 Apprentissages (p. 3)

| § | Règle | Nature retenue |
|---|---|---|
| III.1 | Les heures d'une matière se répartissent entre matin et après-midi | SOFT |
| III.2.a | **¾ du volume** des matières fondamentales (**arabe, français, mathématiques**) le **matin**, ¼ l'après-midi | MEDIUM, en récompense |
| III.2.b | EPS : **3 séances espacées** *ou* **2 séances (2 h + 1 h)** ; **24 h de séparation** entre deux séances | MEDIUM |
| III.2.c | Matières à **2 h/semaine** : jamais sur **deux jours consécutifs** | MEDIUM |
| III.3 | **Coordination** entre matières en système de groupes et séances de quinzaine | SOFT — *limite connue du DSL* |
| III.4 | TP **en salles spécialisées** ; salles spécialisées non affectées aux matières ordinaires ; salle ordinaire « en cas de nécessité absolue » | HARD, mais **levable** par l'établissement |

### 1.4 Notation des tableaux (§ N.1, p. 5–7) — **la clé de lecture**

- `2+1+1+1` — découpage des séances : 5 h en 4 séances (une de 2 h, trois d'1 h).
  C'est le découpage qui contraint le solveur, pas seulement le total.
- **`(2)` et `(3)` — séance en système de groupes. « Le chiffre est la durée en
  heures. »** La classe est scindée, la séance est donnée deux ou trois fois.
  L'enseignant y fait plus d'heures que l'élève n'en reçoit.
- `①` — séance **de quinzaine**, une semaine sur deux, pour la classe entière
  (`WeekParity`).
- `(*)` — collèges assurant l'éducation théâtrale.

### 1.5 Volumes horaires (§ T.1, T.2, T.3)

- **T.1 — collèges** : arabe 5 h, français 4 h + quinzaine (5 h en 9ᵉ), anglais
  `(2)+1+1` 4 h, histoire-géo 2 h, éducation islamique et civique `①+1`,
  mathématiques 4 h, sciences physiques et SVT `①+(2)`, informatique `(2)` 2 h,
  techno `(3)` 3 h en 7ᵉ puis `(2)`, musique 1 h, arts plastiques 1 h,
  théâtre `(2)` 2 h, EPS `2+1` 3 h.
- **T.2 — collèges techniques** : seul tableau distinguant **horaire élève** et
  **horaire enseignant**. Effectifs : classe ≤ 30, groupe ≤ 15, **tri-groupe ≤ 10**
  (d'où les rapports 6/18 et 10/30).
- **T.3 — collèges pilotes** : écarts avec T.1 — mathématiques **5 h**, français
  **5 h pleines**, anglais **4 h**, théâtre sans restriction.

### 1.6 Ce que la circulaire ne dit pas — à ne pas inventer

1. Elle n'emploie **jamais** les mots « HARD » / « SOFT ». Les sévérités de ce
   plan sont notre lecture, pas la sienne.
2. Les totaux « · N h » des tableaux **ne figurent pas** dans la circulaire :
   ils sont calculés par le corpus à partir de la notation.
3. § III.4 — « nécessité absolue » n'est pas quantifié.
4. § II.2 — « répartition équilibrée » n'est pas chiffrée.
5. § III.2.b — la séparation de 24 h ne précise pas si elle se mesure de début
   à début ou de fin à début. **Hypothèse retenue : de début à début**, à
   confirmer.
6. § N.1 — la circulaire dit qu'une séance `①` a lieu « une semaine sur deux »,
   **sans dire laquelle**. Le solveur, lui, a besoin d'une semaine précise pour
   savoir si deux séances se heurtent. **Hypothèse retenue : alternance
   déterministe au sein de la classe** — la première quinzaine de la classe en
   semaine impaire, la deuxième en semaine paire, et ainsi de suite. Ce choix
   sert le § III.3 : en répartissant les quinzaines d'une classe sur les deux
   semaines, deux d'entre elles peuvent partager un créneau au lieu de
   s'exclure, ce qui est la coordination recherchée. Tout mettre en semaine
   impaire laisserait la semaine paire vide et interdirait ce partage.
   Implémenté dans `LessonGenerator.mapWeekParity()`, **à confirmer**.

---

## 2. Les problèmes constatés

### P1 — Des contraintes affichées comme actives ne sont jamais évaluées

Croisement entre les 18 codes de `constraint_definition` et les flux réellement
déclarés dans `TimetableConstraintProvider` :

| Code | Type | État réel |
|---|---|---|
| `RESPECT_OFFICIAL_SUBJECT_HOURS` | HARD | ~~aucun flux~~ — **câblée à l'étape C** |
| `PHYSICAL_EDUCATION_THREE_SESSIONS` | HARD | ~~aucun flux~~ — **câblée à l'étape C** |
| `SPECIAL_ROOM_NO_OVERLAP` | HARD | aucun flux (couvert de fait par `roomConflict`) |
| `NO_STUDENT_IDLE_GAPS` | HARD | codée en dur, **non pilotable** depuis le profil |
| `SPECIAL_ROOM_REQUIRED` | HARD | idem |
| `BALANCED_TEACHER_WORKLOAD` | SOFT | constante déclarée, aucun flux |
| `TEACHER_MIN_TWO_LEVELS` | SOFT | idem |
| `MAIN_SUBJECT_BALANCED_DISTRIBUTION` | SOFT | idem |
| `BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS` | SOFT | idem |

Le profil 11842 active `RESPECT_OFFICIAL_SUBJECT_HOURS` en **CRITICAL, poids
1000**. Le solveur ne la voit pas : **la garantie des volumes du § T.1 n'existe
pas**. Idem pour l'EPS du § III.2.b — d'où, sur le job 11864, 18 classes à 2
séances d'EPS et 3 classes à 0 sans qu'aucune violation dure ne soit comptée.

Défaut symétrique pour les deux contraintes codées en dur : les décocher dans
l'interface ne les désactive pas.

### P2 — La notation `(N)` est lue à l'envers dans les données — **corrigé**

> **Cause trouvée et corrigée.** L'erreur ne venait pas de la saisie d'un
> établissement mais du seed lui-même, `NationalPatternSeeder`, où elle était
> écrite en toutes lettres : `« Technique 7ème : (3) → par groupe, 1h30/groupe,
> volume total 3h »`. Corriger les seules données de l'établissement 28 aurait
> été inutile : le prochain « Appliquer le programme national » les aurait
> réécrites. Le seeder est corrigé, `NationalPatternSeederTest` verrouille
> l'invariant « somme des séances = volume annoncé », et les 45 lignes déjà en
> base ont été rétablies.


§ N.1 dit « le chiffre est la durée en heures ». Les données ont interprété un
nombre de groupes, puis divisé par deux :

| Matière (7ᵉ) | Circulaire | `pattern_details` | Écart |
|---|---|---|---|
| INFO | `(2)` → séance de **2 h** | `duration=1`, `is_split=t` | moitié |
| TECH | `(3)` → séance de **3 h** | `duration=1.5` | moitié |
| THEATRE | `(2)` → **2 h** | `duration=1` | moitié |
| PHY / SCI | `①+(2)` | `1 h biweekly + 1 h split` | moitié sur le `(2)` |

`niveaux_matieres.heures_semaine` porte pourtant la bonne valeur. Les deux
sources se contredisent, et `LessonGenerator` utilise la mauvaise.

### P3 — La quinzaine `①` est mal traitée

Dans `LessonGenerator.findRelevantDetails()` :

```java
.filter(d -> d.getWeekParity() == null
        || d.getWeekParity() == WeekParity.ALL
        || d.getWeekParity() == WeekParity.BIWEEKLY)
```

- `ODD` / `EVEN` → la séance **disparaît**.
- `BIWEEKLY` → génère une séance **hebdomadaire pleine**, donc **double** le
  volume réel.

`Lesson` ne porte aucun champ de parité : le solveur ne peut ni la représenter
ni la contraindre, et le § III.3 reste inexprimable.

### P4 — Le système de groupes ne connaît que le demi-groupe

`generateDemiGroup()` produit toujours exactement 2 lessons. Le § T.2 impose
**trois** groupes pour la formation technique 9ᵉ et les activités spécifiques.
Le modèle ne sait pas non plus que l'enseignant répète la séance (horaire
enseignant ≠ horaire élève).

### P5 — Règles de la circulaire sans implémentation

| § | Règle | État |
|---|---|---|
| I.2 | 2 h minimum par demi-journée non vide | absente |
| I.3 | 2 h de séparation midi | implicite (`isBreakSlot`), non vérifiée |
| I.4 | Stabilité de salle sur une demi-journée | absente |
| II.1 | Journée de formation | `teacherAvailability` existe, rien ne la peuple |
| II.2 | Équilibre hebdomadaire du service | absente |
| II.3 | Dérogation 5 h consécutives vendredi/samedi | implémentée comme plafond journalier, pas comme dérogation |
| II.4 | Alternance sur les 4 premiers jours | équilibre calculé sur toute la semaine |
| II.5 | Deux niveaux minimum | constante déclarée, flux absent |
| III.1 | Répartition matin/après-midi d'une matière | absente |
| III.2.a | ¾ des fondamentales le matin | absente (`Lesson.mainSubject` inutilisé) |
| III.2.b | 24 h entre deux EPS | absente |
| III.2.c | 2 h/semaine, jamais deux jours consécutifs | absente |

### P6 — `MAX_TWO_CONSECUTIVE_SESSIONS` ne mesure pas la consécutivité

Elle compte les séances **du même jour**, pas les séances **contiguës**. Deux
séances à 8 h et 16 h comptent comme consécutives ; trois d'affilée séparées par
une autre matière ne comptent pas. Doublon de fait avec
`AVOID_SUBJECT_CONCENTRATION_SAME_DAY`, qui a le même `groupBy`.

### P7 — Aucune validation métier du planning produit

`TimetableSolverService` ne connaît qu'un critère : `score.isFeasible()`. Aucune
vérification des heures, des séances, ou de la conformité au texte.

### P8 — Données hors circulaire (établissement 28)

- **MATH 6 h** en 7ᵉ — la circulaire dit 4 h (T.1) ou 5 h (T.3).
- **EN 5 h en `2+1+1+1`** — la séance de groupe `(2)` a disparu.
- **ISL / CIV comptés 2 h** — T.1 dit « 1 h + quinzaine ».
- Vendredi et samedi **sans après-midi** : choix local licite, mais c'est ce qui
  produit les 10 doubles réservations du job 11864.

---

## 3. Causes

1. **Le catalogue a été conçu avant le provider** et deux règles dures n'ont
   jamais été câblées ; rien ne relie les deux listes. → P1.
2. **§ N.1 lu trop vite** : `(2)` compris « 2 groupes » au lieu de « 2 heures ».
   La division par deux est cohérente avec cette lecture. → P2.
3. **`WeekParity` ajouté au modèle organisation sans être propagé au solveur.**
   Le filtre de P3 est un contournement, pas une implémentation.
4. **Le développement a suivi la faisabilité technique plutôt que le texte** :
   les contraintes présentes sont celles qu'un solveur exige, celles qui
   manquent sont celles que seule la circulaire impose.

---

## 4. Fichiers concernés

| Fichier | Rôle |
|---|---|
| `solver/constraint/TimetableConstraintProvider.java` | flux manquants, P6 |
| `solver/constraint/ConstraintCodes.java` | constantes non câblées |
| `solver/builder/LessonGenerator.java` | P2, P3, P4 |
| `solver/domain/Lesson.java` | `weekParity`, `groupCount`, volume officiel |
| `solver/builder/TimetableProblemBuilder.java` | faits injectés |
| `solver/ref/TeacherRef.java` | volume hebdomadaire, jours de formation |
| `solver/service/TimetableSolverService.java` | branchement de la validation |
| **nouveau** `solver/validation/TimetableBusinessValidator.java` | validation métier |
| migration Liquibase | alignement catalogue ↔ provider |
| données `patterns` / `pattern_details` | durées `(N)` |

---

## 5. Ordre d'exécution

### Étape A — Verrou catalogue ↔ solveur
Un test qui échoue si un code de `constraint_definition` n'a pas de flux
correspondant. **Ne change aucun comportement** ; empêche P1 de se reproduire.

### Étape B — Les données avant les contraintes
> Timefold ne doit jamais servir à masquer une erreur de données.

1. `pattern_details.duration` pour les séances `(N)` : `(2)` → 2 h, `(3)` → 3 h.
2. Quinzaine : `Lesson.weekParity` + contrainte de parité.
3. Aligner `patterns.total_hours` et `niveaux_matieres.heures_semaine`.

### Étape C — Les deux contraintes dures manquantes
- `RESPECT_OFFICIAL_SUBJECT_HOURS` — somme des heures placées par
  (classe, matière) comparée au volume officiel. **HARD**, § T.1/T.3.
- `PHYSICAL_EDUCATION_THREE_SESSIONS` — § III.2.b, avec ses deux découpages.

### Étape D — Les règles absentes, par valeur décroissante
1. § III.2.c — 2 h/semaine, jamais deux jours consécutifs (MEDIUM)
2. § III.2.b — 24 h entre deux EPS (MEDIUM)
3. § I.2 — 2 h minimum par demi-journée non vide (HARD)
4. § III.2.a — ¾ des fondamentales le matin (MEDIUM, récompense)
5. § I.4 — stabilité de salle (MEDIUM)
6. § II.4 — alternance restreinte aux 4 premiers jours (correction)
7. § II.5 — deux niveaux minimum (SOFT)

### Étape E — Validation métier post-solve
Indépendante de Timefold : heures par matière et par classe, nombre de séances,
conflits enseignant / classe / salle, disponibilités, lessons manquantes,
dupliquées ou en trop, conformité T.1–T.3. Le job ne passe `SOLVED` que si
`isFeasible()` **et** validation métier OK.

### Étape F — Consécutivité
Réécrire `MAX_TWO_CONSECUTIVE_SESSIONS` sur les `orderIndex` contigus, ou la
supprimer si `AVOID_SUBJECT_CONCENTRATION_SAME_DAY` suffit.

---

## 6. Impact attendu

| Étape | Impact |
|---|---|
| A | Aucun sur le runtime. Fera échouer le build tant que les 4 SOFT non câblées existent. |
| B1 | **Augmente** le volume réel d'INFO, TECH, THEATRE, PHY, SCI. Correction d'une sous-évaluation. |
| B2 | Modifie le nombre de lessons ; à revalider écran par écran. |
| C | Le score dur **augmentera fortement** : les violations existaient déjà, elles étaient invisibles. |
| D | Contraint davantage ; peut rendre le problème infaisable avec les ressources actuelles. |
| E | Des jobs aujourd'hui `SOLVED` passeront `INFEASIBLE`. C'est l'objectif. |
| F | Corrige un doublon ; effet marginal. |

---

## 7. Ce que ce plan ne fera pas

Conformément à la règle finale : **aucune lesson ne sera supprimée, aucun volume
horaire diminué, aucune contrainte affaiblie** dans le seul but d'obtenir un
score positif.

Avec 3 professeurs d'EPS pour 23 classes à 3 séances hebdomadaires, et vendredi
et samedi sans après-midi, l'établissement 28 **restera infaisable** après ces
corrections. Ce n'est pas une régression : les violations existent déjà dans le
monde réel, le système les ignorait. Le résultat attendu est que le système
dise que c'est infaisable, **et pourquoi**.
