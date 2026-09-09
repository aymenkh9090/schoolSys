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
| 1 — `occurrences` : désigner les séances en cause | **en cours, non commité** — `ScoreExplanationResponse.Occurrence` + `SessionRef`, colonne `lesson_id` (migration 017), 7 tests écrits dans `ScoreExplanationOccurrencesTest` |
| 2 — Surlignage dans la grille | à faire |
| 3 — Suggestions calculées, pas figées | à faire |
| 4 — L'assistant rend des désignations | à faire |
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

### Étape 1 — `occurrences` : désigner au lieu de décrire — **en cours**

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

**Reste à faire :** exécuter `ScoreExplanationOccurrencesTest` (7 tests) et
`TimetableSolverServicePersistResultTest`, vérifier la migration sur la base de
développement, commiter.

### Étape 2 — Le surlignage dans la grille

`ScoreExplanationPanel.tsx` n'affiche aujourd'hui que les phrases. Le champ
`occurrences` du DTO n'a **aucun consommateur** côté front — attention au
`occurrences` déjà présent dans `frontend/src/api/planning.api.ts:178`, qui est
celui des *suggestions de règles* et n'a rien à voir.

Cliquer une violation surligne **toutes** les séances de l'occurrence : n'en
montrer qu'une sur les deux d'un conflit ne montre pas le conflit.

C'est l'étape qui apporte le plus de valeur pour le moins de travail, et elle
n'a besoin d'aucun LLM.

### Étape 3 — Des suggestions calculées, pas figées

Remplacer la constante de `CONSTRAINT_SUGGESTIONS` par une suggestion qui a vu
la solution : « déplacer *Maths 7B, lundi 8 h* — *jeudi 10 h* est libre pour cet
enseignant, cette classe et une salle du bon type ».

Du calcul Java sur `TimetableSolution`, **pas du LLM** : un créneau proposé doit
être réellement libre, ce qu'un modèle ne peut pas garantir. La phrase statique
reste le repli quand aucun créneau ne convient.

### Étape 4 — L'assistant rend des désignations

**Ne pas donner `occurrences` au modèle.** Un 7B qui navigue dans une structure
imbriquée se trompe de champ et annonce un score qui n'existe pas — c'est la
raison d'être du texte pré-interprété documenté en tête de
`planning_handlers.py`.

Le handler garde donc sa sortie textuelle, et `ChatResponse`
(`ai-assistant/app/models.py:17`) gagne un champ de séances citées, **rempli par
le handler Python, pas par le modèle**. L'écran affiche la réponse en français
plus des puces cliquables qui pointent la grille.

> Le modèle raconte ; le code désigne.

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
