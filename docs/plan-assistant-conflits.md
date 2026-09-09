# Plan — assistant de résolution des conflits d'emploi du temps

> **Objet.** Faire passer l'explication d'un planning infaisable du *récit* à la
> *désignation* : que le système ne dise plus seulement « l'enseignant Ahmed a
> deux cours en même temps », mais montre **lesquels**, et propose un
> déplacement que le directeur n'a plus qu'à accepter.
>
> **Ce plan ne crée pas l'assistant : il le relie.** Les quatre couches
> existent déjà et fonctionnent séparément. Ce qui manque est le fil entre
> « le solveur incrimine la leçon 412 » et « l'admin clique sur la bonne case ».
>
> **Chantier voisin :** `docs/plan-conformite-circulaire.md`, dont les onze
> étapes sont faites. Celui-ci n'ajoute aucune règle de la circulaire — il
> travaille sur ce que le solveur *reproche*, pas sur ce qu'il *vérifie*.

---

## 0. Où nous en sommes

| Étape | État |
|---|---|
| 1 — `occurrences` : désigner les séances en cause | **faite** — `ScoreExplanationResponse.Occurrence` + `SessionRef`, colonne `lesson_id` (migration 017). `ScoreExplanationOccurrencesTest` 7/7 et `TimetableSolverServicePersistResultTest` 46/46 au vert |
| 2 — Surlignage dans la grille | à faire |
| 3 — Suggestions calculées, pas figées | **faite** — `AlternativeSlotFinder` + `Relocation` sur chaque occurrence. 18 tests de contraintes rejouées, 5 tests de bout en bout, module à 425/425 |
| 4 — L'assistant rend des désignations | **faite** — `PlanningChatResponse.conflicts`, rempli par le handler Python. 10 tests. Puces non cliquables tant que l'étape 2 n'existe pas |
| 5 — Proposer / confirmer un déplacement | à faire, optionnel |

### Ce qui existe déjà

**Le solveur produit les faits.** `TimetableSolverService.explainLevel()`
(`smartschool-planning/.../solver/service/TimetableSolverService.java:1258`) lit
les `ConstraintMatch` de Timefold et les rend en `ConstraintViolation` :
libellé, `count`, `score`, `examples` — des *phrases* — et une `suggestion`
piochée dans la table statique `CONSTRAINT_SUGGESTIONS` (même fichier, l. 195).

**Une seule route les expose.** `GET /api/planning/timetable/jobs/{id}/score-explanation`
(`solver/web/TimetableController.java:144`), avec deux consommateurs déjà écrits :

- l'écran `frontend/src/features/planning/ScoreExplanationPanel.tsx` ;
- l'outil `explain_violations` de l'assistant
  (`ai-assistant/app/tools/planning_handlers.py:76`), qui aplatit tout en texte
  court avant de le rendre au modèle.

**L'assistant guide déjà.** `PlanningAssistantService.ask()` expose quatre
outils **en lecture seule** (`ai-assistant/app/tools/planning_definitions.py`) :
état du planning, violations, contraintes actives, suggestions. Et la boucle
complète « proposer puis confirmer » existe côté règles : `suggest_constraint`
→ `propose_constraint` (traduction DSL validée par le backend) → route
`confirm` déclenchée par un clic de l'utilisateur.

**Le déplacement manuel existe.** `PATCH /jobs/{jobId}/sessions/{sessionId}`
(`TimetableController.java:213`).

---

## 1. Le problème

Tout ce que le système sait d'un conflit s'arrête à une phrase. Une phrase ne se
clique pas, ne se surligne pas, et ne permet pas de viser un `PATCH`. Trois
conséquences, dans l'ordre de ce qu'elles coûtent au directeur :

- **P1 — L'explication ne mène nulle part.** « Déplacez l'une des deux séances
  vers un autre créneau » est vrai pour toutes les occurrences de
  `TEACHER_CONFLICT` et n'aide sur aucune : il reste à retrouver les deux
  séances à la main dans une grille de 23 classes.
- **P2 — La suggestion ne connaît pas la solution.** `CONSTRAINT_SUGGESTIONS`
  est un `Map` de constantes : une seule phrase par code de contrainte, écrite
  avant de savoir ce que le solveur trouverait. Elle ne peut pas dire quel
  créneau est libre, parce qu'elle n'a jamais vu le planning.
- **P3 — L'assistant hérite de la même limite.** Il raconte correctement, avec
  des chiffres vrais, et se termine invariablement par « corrigez depuis la
  Consultation du planning ».

---

## 2. Cause

La ligne persistée (`timetable_session`) ne portait que des libellés, alors que
Timefold explique un score en désignant des objets `Lesson`. Le seul
rapprochement possible était approximatif — sur
`(teaching_assignment_id, group_index, jour, heure)` — et approximatif
précisément sur les coordonnées qu'un déplacement de séance modifie. Sans
identifiant commun, aucune couche en aval ne pouvait pointer une case.

---

## 3. Ordre d'exécution

### Étape 1 — `occurrences` : désigner au lieu de décrire — **faite**

Le seul endroit du plan où entrent des **faits nouveaux**. Tout le reste en
dépend : rien en aval ne peut pointer une case tant que ce n'est pas livré.

- `ScoreExplanationResponse.Occurrence` — un `ConstraintMatch`, son score
  propre, et les séances qu'il met en cause ;
- `SessionRef` — `lessonId`, `sessionId` (cible du `PATCH`), matière, classe,
  enseignant, salle, jour, heure, `groupIndex` ;
- colonne `lesson_id` sur `timetable_session`, migration **017**, nullable et
  sans reprise des lignes existantes ;
- `examples` **n'est pas remplacé** : une contrainte à seuil (« pas plus de 6 h
  par jour ») n'incrimine aucune séance en particulier — son tuple porte une
  clé de groupe et un cumul. La phrase y reste le seul rendu honnête.

**Livrée.** Les deux suites annoncées sont passées — `ScoreExplanationOccurrencesTest`
7/7, `TimetableSolverServicePersistResultTest` 46/46 — et `LiquibaseChangelogTest`
valide le changelog, migration 017 comprise. Rien n'est en attente sur cette étape :
les couches en aval peuvent désormais pointer une case.

### Étape 2 — Le surlignage dans la grille

`ScoreExplanationPanel.tsx` n'affiche aujourd'hui que les phrases. Le champ
`occurrences` du DTO n'a **aucun consommateur** côté front — attention au
`occurrences` déjà présent dans `frontend/src/api/planning.api.ts:178`, qui est
celui des *suggestions de règles* et n'a rien à voir.

Cliquer une violation surligne **toutes** les séances de l'occurrence : n'en
montrer qu'une sur les deux d'un conflit ne montre pas le conflit.

C'est l'étape qui apporte le plus de valeur pour le moins de travail, et elle
n'a besoin d'aucun LLM.

### Étape 3 — Des suggestions calculées, pas figées — **faite**

`AlternativeSlotFinder` lit la solution en mémoire et rejoue, une à une, les
règles dures qu'un déplacement dans le temps peut enfreindre : `TEACHER_CONFLICT`,
`CLASS_CONFLICT`, `ROOM_CONFLICT`, `ROOM_CAPACITY`, `TEACHER_AVAILABILITY`,
`LESSON_EXCEEDS_WORKING_BLOCK`, et le couple `SPECIAL_ROOM_REQUIRED` /
`NORMAL_COURSE_NOT_IN_SPECIAL_ROOM` pour la salle. Chaque occurrence gagne une
`Relocation` — créneau d'arrivée, salle, `sessionId` cible du `PATCH`, et la
phrase prête à afficher. `CONSTRAINT_SUGGESTIONS` redevient ce qu'il aurait
toujours dû être : le repli quand rien ne convient.

Du calcul Java, **pas du LLM** : un créneau proposé doit être réellement libre,
ce qu'un modèle ne peut pas garantir.

**Trois décisions qui tiennent la suite :**

- **Le créneau d'origine est écarté.** Réattribuer une salle sans bouger
  l'horaire soigne un conflit de salle, pas un conflit d'enseignant, et le
  chercheur ignore quelle contrainte l'a fait appeler. Un remède qui ne corrige
  rien serait pire que le silence.
- **Un demi-groupe apparié ne reçoit rien.** Déplacer le groupe A sans le
  groupe B enfreint `PAIRED_DEMI_GROUP_SAME_SLOT` ; déplacer les deux demande
  une proposition à deux séances, que le contrat de sortie ne porte pas.
- **La phrase énonce ce qui a été vérifié, jamais que le planning ira mieux.**
  Le déplacement peut dégrader une contrainte souple — quota du matin, heure
  creuse. L'arbitrage revient au directeur, qui voit la grille.

Les exceptions des contraintes sont recopiées à l'identique, et testées comme
telles : deux demi-groupes distincts cohabitent, deux quinzaines opposées ne se
heurtent pas. Les oublier n'aurait rien cassé de visible — le chercheur se
serait tu sur des créneaux valides, en silence.

`Lesson.wouldOverlapAt` porte désormais la règle de chevauchement, dont
`overlapsInTime` est le cas particulier. Une recherche de remplacement qui
recopierait ce calcul finirait par proposer un créneau que les contraintes
refusent.

### Étape 4 — L'assistant rend des désignations — **faite**

`occurrences` n'entre pas dans le prompt, et un test le garantit : le texte
rendu au modèle ne contient ni `sessionId`, ni `lessonId`, ni accolade. Un 7B
qui navigue dans une structure imbriquée se trompe de champ et annonce un score
qui n'existe pas.

Le handler garde sa sortie textuelle et remplit **en parallèle**
`cited_conflicts`, relu par `PlanningAssistantService.ask` après la boucle
d'outils. Chaque conflit porte la règle enfreinte, sa sévérité, les séances
désignées et — quand un créneau convient — le déplacement calculé à l'étape 3.

> Le modèle raconte ; le code désigne.

**Deux écarts au plan, assumés :**

- **`PlanningChatResponse` plutôt qu'un champ de plus sur `ChatResponse`.**
  L'assistant de supervision partage ce modèle et n'a aucun conflit d'emploi du
  temps à porter ; un champ toujours vide chez l'un des deux consommateurs finit
  par être lu comme un oubli. La route déclare le modèle enrichi.
- **Les puces ne sont pas cliquables.** Le surlignage dans la grille est
  l'étape 2, qui n'est pas faite : rendre ces lignes cliquables offrirait un
  geste sans destination. Le bloc affiche les séances et la proposition ; le
  clic viendra avec la cible.

### Étape 5 — Proposer, puis confirmer un déplacement — optionnel

Décalquer le schéma des contraintes : **proposer** dans la boucle d'outils,
**appliquer** par une route HTTP distincte, déclenchée par un clic sur ce qui a
été affiché mot pour mot.

---

## 4. La règle à ne pas casser

Aucun outil d'écriture dans la table de dispatch de l'assistant. Ajouter un
`move_session` à `planning_definitions.py` réintroduirait exactement le risque
que ce fichier documente en tête : un modèle qui peut à la fois discuter et
écrire finit par écrire pendant qu'il discute. Sur un emploi du temps
d'établissement, seul un assistant qui *propose* est acceptable.

---

## 5. Fichiers concernés

| Couche | Fichier |
|---|---|
| Faits | `smartschool-planning/.../solver/service/TimetableSolverService.java` |
| DTO | `smartschool-planning/.../solver/dto/response/ScoreExplanationResponse.java` |
| Entité | `smartschool-planning/.../solver/domain/TimetableSession.java` |
| Migration | `.../db/changelog.planing/017-timetable-session-lesson-id.yaml` |
| Route | `smartschool-planning/.../solver/web/TimetableController.java` |
| Écran | `frontend/src/features/planning/ScoreExplanationPanel.tsx` |
| Outil IA | `ai-assistant/app/tools/planning_handlers.py`, `planning_definitions.py` |
| Contrat IA | `ai-assistant/app/models.py` |

---

## 6. Ce que ce plan ne fera pas

Il ne rend aucun planning faisable. L'établissement 28 restera infaisable pour
les raisons dites au § 7 du plan de conformité — 3 professeurs d'EPS pour 23
classes. Ce plan ne change pas ce constat : il le rend **actionnable**, en
montrant où sont les conflits et ce qu'un déplacement corrigerait.

Il ne donne pas non plus au modèle le droit de corriger le planning. Un
déplacement reste une décision du directeur, prise sur une proposition qu'il a
lue en entier.
