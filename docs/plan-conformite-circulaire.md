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
| D — Règles de la circulaire absentes | **fait** — 5 règles créées, 1 corrigée, 1 réveillée ; 37 tests ; `NON_CABLEES` tombe de 7 à 6 |
| E — Validation métier post-solve | **fait** — `TimetableBusinessValidator`, 14 contrôles, verrou sur `SOLVED`, rapport archivé ; 33 tests |
| F — Correction de la consécutivité | **fait** — mesurée sur les créneaux contigus, parité comprise ; 14 tests |
| G — Parité de semaine dans la continuité | **fait** — `noStudentIdleGaps` et `MIN_STUDENT_HOURS_PER_HALF_DAY` lisent la quinzaine ; 12 tests |
| H — Programme national conforme (§ T.1 + § T.3) | **fait** — MATH 4 h, EN `(2)+1+1`, FR `2+1+1+①` ; jeu pilote ajouté ; rattrapage des bases semées ; 12 tests |
| I — Catalogue vidé de ses mensonges | **fait** — § II.2 et § III.1 écrites, 4 codes retirés ; `NON_CABLEES` est **vide** ; 11 tests |

### Ce que l'étape D a livré

Cinq codes **neufs des deux côtés** — ils n'existaient ni au catalogue ni dans le
provider. La migration `014-seed-circulaire-constraints.yaml` les sème et
**rattrape les profils existants** : `createDefault()` ne sème un réglage qu'à la
création du profil, un établissement déjà en service n'aurait donc jamais vu ces
règles. C'est le défaut P1 à l'envers, et il fallait le traiter dans la même
migration.

| # | Règle | § | Code | Sévérité |
|---|---|---|---|---|
| 1 | Matière à 2 h/semaine : jamais deux jours consécutifs | III.2.c | `SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS` | MEDIUM |
| 2 | 24 h entre deux séances d'EPS | III.2.b | `PHYSICAL_EDUCATION_SESSION_SPACING` | MEDIUM |
| 3 | 2 h minimum par demi-journée non vide | I.2 | `MIN_STUDENT_HOURS_PER_HALF_DAY` | HARD |
| 4 | ¾ des matières fondamentales le matin | III.2.a | `MAIN_SUBJECTS_MORNING_QUOTA` | MEDIUM, en récompense |
| 5 | Stabilité de salle sur une demi-journée | I.4 | `CLASS_ROOM_STABILITY_PER_HALF_DAY` | MEDIUM |
| 6 | Alternance restreinte aux 4 premiers jours | II.4 | `BALANCED_MORNING_AFTERNOON` **corrigée** | MEDIUM |
| 7 | Deux niveaux minimum par enseignant | II.5 | `TEACHER_MIN_TWO_LEVELS` **câblée** | SOFT |

Trois décisions d'implémentation méritent d'être retenues, parce qu'elles
tranchent des ambiguïtés du texte :

- **§ III.2.c est écrit sur le volume, pas sur les noms de matières.** La
  circulaire vise l'histoire-géo et l'éducation islamique et civique ; elle les
  désigne par « les matières à deux heures ». La contrainte lit
  `officialWeeklySlots == 4` : elle suivra le programme si celui-ci change.
- **§ I.2 — l'exemption de l'EPS porte sur la demi-journée, pas sur l'heure.**
  Une heure de sport seule dans sa matinée est régulière ; une heure de sport
  suivie d'une heure de maths fait deux heures et satisfait le plancher. Retirer
  l'EPS de la somme aurait produit l'inverse : une matinée de deux heures
  déclarée trop courte.
- **§ III.2.a est une récompense plafonnée, pas une pénalité.** Le texte réserve
  explicitement un quart de l'horaire à l'après-midi : pénaliser les heures
  d'après-midi combattrait la circulaire. La récompense sature au quota, si bien
  qu'une matière entièrement matinale ne rapporte pas plus qu'un trois-quarts.

**Effet de bord corrigé au passage.** `BALANCED_MORNING_AFTERNOON` équilibrait
la semaine entière. Vendredi et samedi n'ayant pas d'après-midi dans
l'établissement 28, chaque heure du vendredi matin creusait un déséquilibre que
rien ne pouvait combler : la contrainte pénalisait un emploi du temps régulier et
le solveur dépensait son budget à courir après un équilibre impossible. Elle ne
regarde plus que lundi à jeudi, comme le § II.4 le borne.

### Ce que l'étape E a livré

`TimetableBusinessValidator` — une fonction pure de la solution, sans Timefold,
sans profil, sans configuration. Quatorze contrôles, rangés en trois familles et
en trois seulement :

| Famille | Contrôles | Pourquoi le solveur ne suffisait pas |
|---|---|---|
| **Intégrité de la génération** | séance non placée, séance dupliquée, enseignant manquant, demi-groupe désapparié | Le solveur y est **structurellement aveugle** : une séance jamais engendrée n'est dans aucun flux de contraintes, donc ne coûte rien. |
| **Impossibilité physique** | conflit enseignant / classe / salle, indisponibilité, capacité, salle spécialisée, pause méridienne, séance débordante | Ces contraintes existent au provider, mais elles sont **décochables dans le profil**. |
| **Programme officiel** | volume par classe et matière (§ T.1–T.3) | Idem — et c'était le cas concret : décocher `RESPECT_OFFICIAL_SUBJECT_HOURS` suffisait à obtenir un planning « conforme » où chaque classe perdait des heures. |

**Ce qu'il ne fait pas, délibérément.** Aucune règle pédagogique n'y figure — ni
le quota matinal du § III.2.a, ni l'espacement des séances d'EPS du § III.2.b, ni
la stabilité de salle du § I.4. Ce sont des contraintes MEDIUM : elles orientent
le solveur, elles ne justifient pas de refuser un emploi du temps à un
établissement. Les réimplémenter ici dupliquerait le provider et ferait diverger
deux définitions de la même règle — c'est-à-dire recréerait P1.

**Le verrou.** `persistResult` ne passe `SOLVED` que si `score.isFeasible()`
**et** `report.estConforme()`. Trois conséquences, toutes voulues :

1. Le motif du refus est **archivé** sur le job (`timetable_job.validation_report`,
   migration 015). `/score-explanation` ne répond qu'aussi longtemps que la
   solution est en mémoire — c'est-à-dire pas après un redémarrage ; le rapport,
   lui, survit.
2. `feasible` veut désormais dire **« remettable »**, partout : sur le job, sur le
   `GeneratedTimetable` et dans l'instantané diffusé. Publier la faisabilité
   Timefold seule affichait « faisable » à côté d'un statut INFEASIBLE.
3. Un **avertissement** s'archive sans refuser. Le seul aujourd'hui —
   `VOLUME_NON_VERIFIABLE` — dit que le volume officiel manque en base pour
   certains couples classe / matière. Un contrôle qui se tait faute de donnée
   doit dire qu'il s'est tu, sans quoi l'absence de constat se lit comme une
   conformité.

Côté interface, `ScoreExplanationPanel` gagne une section « Vérification métier »,
placée **avant** les violations Timefold : ce qu'elle reproche est vrai quelles
que soient les règles activées.

### Ce que l'étape F a livré

`MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT` comptait les séances de la matière
**dans la journée**, sans regarder où elles tombaient. Deux heures de
mathématiques à 8 h et à 16 h passaient donc pour consécutives, et trois heures
d'affilée coupées par une heure d'anglais ne l'étaient pas. Elle mesurait la
concentration — que `AVOID_SUBJECT_CONCENTRATION_SAME_DAY` mesurait déjà, avec le
même `groupBy`, en SOFT.

Elle mesure désormais la plus longue suite de séances **contiguës**, avec trois
précisions qui comptent :

- **Le groupement va jusqu'à la demi-journée**, pas seulement au jour : la
  dernière heure de la matinée et la première de l'après-midi ne s'enchaînent
  pas, la pause du § I.3 les sépare.
- **La durée réelle des séances est lue.** Une séance de deux heures occupe
  quatre créneaux ; la suivante s'y enchaîne au cinquième. Compter les séances
  sans lire leur durée rompait la suite à tort.
- **La parité de semaine est évaluée à part**, semaine impaire puis semaine
  paire, en retenant la pire. Une quinzaine ne prolonge une suite que les
  semaines où elle a lieu.

Ce dernier point mérite d'être noté : c'est le traitement que la dette
`noStudentIdleGaps` réclamait, appliqué ici pour la première fois. Il a servi de
modèle à l'étape G, qui l'a généralisé.

Le libellé du catalogue — « Éviter plus de deux séances consécutives de la même
matière » — n'a pas bougé : **il décrivait déjà le comportement attendu**. C'est
le code qui en avait dévié, pas la spécification. Aucune migration n'était donc
nécessaire.

**Les deux contraintes ne se recouvrent plus.** Une matière donnée deux fois le
matin et deux fois l'après-midi concentre sans enchaîner ; quatre heures
d'affilée enchaînent sans concentrer davantage. Elles restent toutes deux au
catalogue, et disent maintenant deux choses différentes.

### Ce que l'étape G a livré

L'étape F avait corrigé la consécutivité en évaluant la semaine impaire et la
semaine paire séparément, et signalait dans la foulée que deux autres
contraintes souffraient du même angle mort. Elles le souffrent au même endroit :
toutes trois regroupent les séances d'une demi-journée, et toutes trois lisaient
ce groupe comme si chaque séance avait lieu chaque semaine.

| Contrainte | § | Ce qu'elle disait | Ce qu'elle dit |
|---|---|---|---|
| `NO_STUDENT_IDLE_GAPS` | I.5 | une quinzaine bouchait le trou les deux semaines | le trou est cherché dans chaque semaine |
| `MIN_STUDENT_HOURS_PER_HALF_DAY` | I.2 | une quinzaine remplissait la demi-journée les deux semaines | le volume est compté dans chaque semaine, la pire décide |

Le filtre de parité, écrit trois fois, est désormais écrit une seule :
`seancesDeLaSemaine(seances, semaine)`. C'était la condition pour que les trois
contraintes ne re-divergent pas — elles avaient chacune eu sa version du même
oubli.

**La correction va dans les deux sens, et c'est ce qui la rend juste.** Elle
signale des violations qui n'étaient pas vues : une quinzaine placée entre deux
cours laisse un vrai trou la semaine où elle n'a pas lieu ; une matinée d'une
heure de cours plus une heure de quinzaine ne fait deux heures qu'une semaine sur
deux. Mais elle en retire aussi : deux quinzaines opposées de part et d'autre
d'un créneau libre étaient comptées comme un trou, alors qu'aucune semaine ne
voit les deux, et une quinzaine de deux heures seule dans sa matinée était une
demi-journée trop courte la semaine où la classe ne se déplace même pas.

**Un point de lecture a été tranché au passage** : l'exemption de l'EPS du § I.2
se réévalue semaine par semaine. Une matinée composée d'une heure de sport
hebdomadaire et d'une heure de quinzaine est, la semaine paire, une matinée
entièrement sportive — donc exemptée. Appliquer l'exemption sur la liste brute
des séances l'aurait refusée, au motif qu'une séance de maths y figure une
semaine sur deux.

La pénalité reste comptée **par demi-journée**, non par semaine : c'est la
demi-journée qui est mal construite, et la compter deux fois quand le défaut
existe les deux semaines gonflerait le score sans rien ajouter au diagnostic.

### Ce que l'étape H a livré

Le programme national semé se disait « officiel » et « § T.1 » en donnant des
volumes qui n'étaient d'aucun des deux tableaux. C'était le point bloquant
depuis l'étape E : `VOLUME_HORAIRE` compare le placé au volume en base, et le
volume en base était faux.

| Matière | Semé jusqu'ici | § T.1 — retenu | § T.3 — ajouté |
|---|---|---|---|
| Mathématiques | 6 h, `1×6` | **4 h**, `1+1+1+1` | 5 h, `1+1+1+1+1` |
| Anglais | 5 h, classe entière | **4 h**, `(2)+1+1` | 5 h, `(2)+1+1+1` |
| Français | 5 h, `2+1+1+1` | **`2+1+1+①`** en 7ᵉ et 8ᵉ, `2+1+1+1` en 9ᵉ | 5 h pleines |
| tout le reste | — | inchangé | identique au § T.1 |

Trois choses méritent d'être retenues.

**La séance de groupe de l'anglais avait purement disparu.** Le § T.1 note
`(2)+1+1` : la première séance est en système de groupes. Le seed donnait quatre
séances en classe entière. Ce n'est pas une heure de moins seulement, c'est une
séance d'une autre nature — l'enseignant la donne deux fois, l'élève la reçoit
une.

**Le collège pilote a désormais son propre programme.** `COLLEGE_*_PILOTE`
sème le § T.3, dont les écarts avec le § T.1 sont exactement les trois lignes
ci-dessus. Reste à décider lequel s'applique quand une demande ne dit que le
niveau : `findActiveWithDetailsByCountryAndLevel` trie par version décroissante
et le service prend le premier. Le § T.1 étant le cas général, la version du
programme ordinaire est définie comme *celle du pilote plus un* — une règle, pas
un nombre, qui restera vraie après le prochain changement de programme. Un
collège pilote applique le § T.3 en désignant le programme par son identifiant.

**Le garde-fou du seeder était le défaut de l'étape D, encore une fois.**
« Des programmes TN existent, ne rien faire » : une correction du programme
officiel n'atteignait jamais une base en service, c'est-à-dire précisément
celles où elle compte. Chaque programme est maintenant comparé à sa version et
ses lignes remplacées si elle est dépassée. Les suppressions sont vidées en base
avant les insertions — `national_pattern_details` porte un index unique
(programme, matière), et l'ordre inverse violerait la contrainte sur toutes les
matières conservées.

**Ce que le seed ne rattrape pas, et qui reste à faire à la main.** Les patterns
déjà copiés chez un établissement ne bougent pas : « Appliquer le programme
national » saute tout pattern dont le nom existe déjà, pour ne pas écraser ce
qu'un établissement a réglé lui-même. `docs/sql/rattrapage-volumes-t1.sql`
supprime les trois patterns divergents et réaligne `heures_semaine`, après quoi
« Appliquer le programme national » les recrée depuis le seed corrigé — plutôt
que de réécrire les séances en SQL, chemin que rien ne couvre. **Ramener les
mathématiques de 6 h à 4 h retire deux heures à chaque classe : les affectations
d'enseignants bâties sur six heures deviennent excédentaires et doivent être
revues.** Le script ne les touche pas.

**Convention de total, à noter parce qu'elle diverge de la circulaire en
apparence.** Le texte écrit « 4 h + quinzaine » et laisse la séance `①` hors du
total. Ici le total est la somme des durées de séances, quinzaine comprise :
français 7ᵉ vaut 5 h. Ce n'est pas une autre lecture, c'est la seule utilisable
en aval — une séance de quinzaine occupe un créneau entier de la grille, et
`RESPECT_OFFICIAL_SUBJECT_HOURS` compare des créneaux placés à ce total. La
compter pour une demi-heure ferait échouer la contrainte sur un emploi du temps
correct.

**Un erratum du corpus repéré au passage**, à confirmer sur le PDF : le § T.3
porte en note « Anglais : 4 h au lieu de 3 », alors que son propre tableau note
`(2)+1+1+1 · 5 h` et que le § T.1 en donne 4. La note ne s'accorde ni avec sa
table ni avec l'autre. Les tables étant la transcription et les notes le
commentaire, c'est la table qui a été suivie.

### Ce que l'étape I a livré

Six codes du catalogue n'ouvraient aucun flux dans le provider, pour deux
raisons opposées et également trompeuses : trois SOFT n'avaient jamais été
écrites — l'établissement pouvait les activer, elles ne faisaient rien — et
trois règles dures étaient appliquées **en dur**, si bien que les décocher ne
les désactivait pas. La liste est traitée code par code, selon ce qui était
faux.

| Code | § | Décision |
|---|---|---|
| `BALANCED_TEACHER_WORKLOAD` | II.2 | **écrite** — service réparti sur les jours de travail |
| `MAIN_SUBJECT_BALANCED_DISTRIBUTION` | III.1 | **écrite** — matière répartie matin / après-midi |
| `BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS` | — | **retirée** : absente de la circulaire |
| `NO_STUDENT_IDLE_GAPS` | I.5 | **retirée du catalogue**, toujours appliquée |
| `SPECIAL_ROOM_REQUIRED` | III.4 | idem |
| `SPECIAL_ROOM_NO_OVERLAP` | — | **retirée** : ne disait rien de plus que `roomConflict` |

**Pourquoi retirer plutôt que rendre pilotable.** Le § I.5 interdit les heures
creuses et le § III.4 impose la salle spécialisée : ce ne sont pas des options.
Les rendre décochables aurait rouvert exactement le piège que l'étape E a
fermé — un emploi du temps déclaré conforme parce qu'on avait décoché la règle
qu'il viole. Les deux règles restent donc appliquées ; c'est la case à cocher
qui disparaît, parce qu'elle ne commandait rien.

**§ II.2 — les deux nombres viennent de la même phrase.** « L'horaire
hebdomadaire dû par l'enseignant est réparti de manière équilibrée sur les
jours de travail. » L'équilibre n'est pas chiffré (§ 1.6, point 4) et il ne faut
pas lui prêter un chiffre. Ce qui est mesuré est donc la seule chose que la
phrase interdit sans ambiguïté : concentrer le service sur quelques jours. Le
plafond d'une journée est la part équitable — le service divisé par les jours
travaillés — **et jamais moins de deux heures**, plancher que le même article
énonce dans la phrase suivante. Sans ce plancher, la contrainte pousserait un
enseignant à mi-temps vers une heure par jour six jours par semaine, c'est-à-dire
vers ce que le même article interdit. Le jour de formation du § II.1 sort du
diviseur par les indisponibilités de l'enseignant — c'est là qu'il devrait être
inscrit, et rien ne peuple encore cette source.

**§ III.1 — une règle binaire, une pénalité binaire.** « Les heures
hebdomadaires prévues pour une même matière sont réparties sur les périodes du
matin et de l'après-midi, quelle que soit cette matière. » Le texte ne demande
pas une moitié de chaque côté ; il interdit qu'une matière soit *entièrement*
massée d'un seul côté. Une matière est massée ou elle ne l'est pas — un degré de
massement n'aurait pas de sens. Deux exclusions, parce qu'on ne reproche pas
l'impossible : une matière d'une seule séance ne se partage pas, et le seuil de
volume est réglable (deux heures par défaut). La parité de semaine n'est **pas**
séparée ici, contrairement aux contraintes de continuité : l'article parle des
heures « prévues pour la matière », c'est-à-dire de la semaine type et non de la
semaine vécue.

**Un défaut voisin corrigé au passage.** Aucune des règles câblées aux étapes C
et D n'avait de libellé dans `TimetableSolverService` : le panneau d'explication
affichait leur code brut — `MIN_STUDENT_HOURS_PER_HALF_DAY` — à un directeur
d'établissement. Les dix libellés et suggestions manquants sont écrits, avec le
§ d'origine.

### Où reprendre

**Les neuf étapes du plan sont faites, la dette qu'elles avaient identifiée est
soldée et `NON_CABLEES` est vide.** Ce qui reste tient en un rattrapage de
données à exécuter et deux hypothèses à confirmer auprès de l'établissement.

**Points ouverts, par ordre d'urgence :**

1. **Rattrapage des données de l'établissement 28** — la décision est prise
   (§ T.1, avec un jeu pilote séparé) et le seed est corrigé, mais les patterns
   déjà copiés chez l'établissement doivent être supprimés pour être recréés :
   `docs/sql/rattrapage-volumes-t1.sql`, à exécuter base démarrée, puis
   « Appliquer le programme national ». Les affectations d'enseignants bâties
   sur 6 h de mathématiques sont à revoir dans la foulée.
2. **Mesure des 24 h du § III.2.b** — de début à début, hypothèse retenue et
   implémentée ; c'est la lecture stricte. À confirmer auprès de l'établissement.
3. **Alternance des quinzaines** — `LessonGenerator.mapWeekParity()`, hypothèse
   retenue (§ 1.6, point 6).

**Dette identifiée, traitée à l'étape G :** `noStudentIdleGaps` et
`MIN_STUDENT_HOURS_PER_HALF_DAY` regroupaient les séances sans regarder la parité
de semaine. Les deux lisent désormais la quinzaine, par le même filtre que la
consécutivité.

**`NON_CABLEES` est vide depuis l'étape I.** Le catalogue ne montre plus que des
règles que le solveur évalue, et toute règle qu'il évalue y figure. Le verrou
`CatalogueProviderCoverageTest` ne tolère donc plus aucun écart.

**Articles de la circulaire encore sans implémentation** — il en reste trois, et
aucun ne se règle par une contrainte : § I.3 (la séparation de midi n'est
qu'implicite dans `isBreakSlot`), § II.1 (aucune source ne peuple les jours de
formation — c'est une donnée qui manque, pas une règle), § II.3 (écrit comme un
plafond journalier et non comme la dérogation qu'il est).

**État des tests :** `smartschool-planning` 367, `smartschool-api` 12,
`organisation-business` 300, `absence-business` 10, `tenant-business` 19 —
**0 échec**. Le front compile.

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
| `SPECIAL_ROOM_NO_OVERLAP` | HARD | ~~aucun flux~~ — **retirée du catalogue, étape I** (doublon de `roomConflict`) |
| `NO_STUDENT_IDLE_GAPS` | HARD | ~~codée en dur, non pilotable~~ — **retirée du catalogue, étape I** ; toujours appliquée |
| `SPECIAL_ROOM_REQUIRED` | HARD | idem |
| `BALANCED_TEACHER_WORKLOAD` | SOFT | ~~constante déclarée, aucun flux~~ — **écrite à l'étape I** (§ II.2) |
| `TEACHER_MIN_TWO_LEVELS` | SOFT | ~~idem~~ — **câblée à l'étape D** |
| `MAIN_SUBJECT_BALANCED_DISTRIBUTION` | SOFT | ~~idem~~ — **écrite à l'étape I** (§ III.1) |
| `BALANCED_CLASS_DIFFICULTY_FOR_TEACHERS` | SOFT | ~~idem~~ — **retirée du catalogue, étape I** : absente de la circulaire |

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
| I.2 | 2 h minimum par demi-journée non vide | ~~absente~~ — **`MIN_STUDENT_HOURS_PER_HALF_DAY`, étape D** |
| I.3 | 2 h de séparation midi | implicite (`isBreakSlot`), non vérifiée |
| I.4 | Stabilité de salle sur une demi-journée | ~~absente~~ — **`CLASS_ROOM_STABILITY_PER_HALF_DAY`, étape D** |
| II.1 | Journée de formation | `teacherAvailability` existe, rien ne la peuple |
| II.2 | Équilibre hebdomadaire du service | ~~absente~~ — **`BALANCED_TEACHER_WORKLOAD`, étape I** |
| II.3 | Dérogation 5 h consécutives vendredi/samedi | implémentée comme plafond journalier, pas comme dérogation |
| II.4 | Alternance sur les 4 premiers jours | ~~toute la semaine~~ — **bornée à lundi–jeudi, étape D** |
| II.5 | Deux niveaux minimum | ~~constante déclarée, flux absent~~ — **câblée à l'étape D** |
| III.1 | Répartition matin/après-midi d'une matière | ~~absente~~ — **`MAIN_SUBJECT_BALANCED_DISTRIBUTION`, étape I** |
| III.2.a | ¾ des fondamentales le matin | ~~absente~~ — **`MAIN_SUBJECTS_MORNING_QUOTA`, étape D** ; `mainSubject` enfin utilisé |
| III.2.b | 24 h entre deux EPS | ~~absente~~ — **`PHYSICAL_EDUCATION_SESSION_SPACING`, étape D** |
| III.2.c | 2 h/semaine, jamais deux jours consécutifs | ~~absente~~ — **`SUBJECT_TWO_HOURS_NOT_CONSECUTIVE_DAYS`, étape D** |

Restent absentes : le § I.3 (séparation de midi, seulement implicite), le § II.1
(aucune source ne peuple les jours de formation) et le § II.3 (écrit comme un
plafond et non comme une dérogation).

### P6 — `MAX_TWO_CONSECUTIVE_SESSIONS` ne mesure pas la consécutivité — **corrigé**

> **Corrigé à l'étape F.** Elle comptait les séances **du même jour**, pas les
> séances **contiguës** : deux séances à 8 h et 16 h comptaient comme
> consécutives, trois d'affilée séparées par une autre matière ne comptaient pas.
> Doublon de fait avec `AVOID_SUBJECT_CONCENTRATION_SAME_DAY`, qui avait le même
> `groupBy`. Elle mesure désormais la plus longue suite de créneaux contigus, par
> demi-journée, durées réelles lues et parité de semaine évaluée à part. Les deux
> contraintes disent maintenant deux choses différentes.

### P7 — Aucune validation métier du planning produit — **corrigé**

> **Corrigé à l'étape E.** `TimetableSolverService` ne connaissait qu'un critère :
> `score.isFeasible()`. Or ce score ne dit qu'une chose — aucune des contraintes
> *activées par cet établissement* n'est violée. Il ne dit rien des autres, et
> décocher `RESPECT_OFFICIAL_SUBJECT_HOURS` suffisait à obtenir un emploi du temps
> déclaré conforme où chaque classe perdait des heures : tout le reste du plan
> était révocable d'un clic. `TimetableBusinessValidator` juge indépendamment du
> profil et du solveur, et le job ne passe `SOLVED` que si les deux verdicts
> concordent.

### P8 — Données hors circulaire (établissement 28)

- ~~**MATH 6 h** en 7ᵉ~~ — **corrigé à l'étape H** : le seed dit 4 h (§ T.1).
  Les données de l'établissement se rattrapent par
  `docs/sql/rattrapage-volumes-t1.sql`.
- ~~**EN 5 h en `2+1+1+1`**~~ — **corrigé à l'étape H** : `(2)+1+1`, séance de
  groupe rétablie.
- **ISL / CIV comptés 2 h** — T.1 dit « 1 h + quinzaine ». Écart assumé : le
  total est la somme des durées de séances, la quinzaine occupant un créneau
  entier de la grille (voir l'étape H).
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
| `solver/validation/` | **créé à l'étape E** — `TimetableBusinessValidator`, `ValidationReport`, `ValidationFinding`, `ValidationSeverity` |
| migration Liquibase | alignement catalogue ↔ provider ; `014-seed-circulaire-constraints.yaml` sème les 5 codes de l'étape D et rattrape les profils existants ; `016-vider-catalogue-non-cable.yaml` retire les 4 codes de l'étape I et redit ce que font les 2 écrites |
| `api/NationalPatternSeeder.java` | programmes § T.1 et § T.3, rattrapage par version |
| `docs/sql/rattrapage-volumes-t1.sql` | remise à niveau des patterns déjà copiés chez un établissement |
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

### Étape D — Les règles absentes, par valeur décroissante — **faite**
1. § III.2.c — 2 h/semaine, jamais deux jours consécutifs (MEDIUM)
2. § III.2.b — 24 h entre deux EPS (MEDIUM)
3. § I.2 — 2 h minimum par demi-journée non vide (HARD)
4. § III.2.a — ¾ des fondamentales le matin (MEDIUM, récompense)
5. § I.4 — stabilité de salle (MEDIUM)
6. § II.4 — alternance restreinte aux 4 premiers jours (correction)
7. § II.5 — deux niveaux minimum (SOFT)

Les cinq premières sont de nouveaux codes, semés par la migration 014 avec le
rattrapage des profils déjà en base ; les deux dernières corrigent l'existant.
Couverture : `ConformiteCirculaireEtapeDTest`, 37 tests.

### Étape E — Validation métier post-solve — **faite**
Indépendante de Timefold : heures par matière et par classe, nombre de séances,
conflits enseignant / classe / salle, disponibilités, lessons manquantes,
dupliquées ou en trop, conformité T.1–T.3. Le job ne passe `SOLVED` que si
`isFeasible()` **et** validation métier OK.

Livré dans `solver/validation/` : 14 contrôles répartis en intégrité de la
génération, impossibilité physique et programme officiel. Le motif du refus est
archivé sur le job (migration 015) et affiché dans le panneau d'explication.
Couverture : `TimetableBusinessValidatorTest`, 28 tests, plus 5 sur le verrou
lui-même dans `TimetableSolverServicePersistResultTest`.

### Étape F — Consécutivité — **faite**
Réécrire `MAX_TWO_CONSECUTIVE_SESSIONS` sur les `orderIndex` contigus, ou la
supprimer si `AVOID_SUBJECT_CONCENTRATION_SAME_DAY` suffit.

Réécrite, et gardée : une fois la consécutivité réellement mesurée, les deux
contraintes ne disent plus la même chose. Groupement à la demi-journée, durées
réelles lues, parité de semaine évaluée séparément.
Couverture : `ConsecutiviteConstraintTest`, 14 tests.

### Étape G — La parité de semaine dans la continuité — **faite**
Appliquer à `NO_STUDENT_IDLE_GAPS` (§ I.5) et `MIN_STUDENT_HOURS_PER_HALF_DAY`
(§ I.2) le traitement de parité écrit à l'étape F : évaluer la semaine impaire
et la semaine paire séparément, retenir la pire. Le filtre est mis en commun —
`seancesDeLaSemaine()` — pour que les trois contraintes cessent d'avoir chacune
sa lecture de la quinzaine. L'exemption sportive du § I.2 se réévalue elle aussi
semaine par semaine.
Couverture : `PariteSemaineContinuiteTest`, 12 tests.

### Étape H — Le programme national, § T.1 et § T.3 — **faite**
Corriger `COLLEGE_*_OFFICIEL` sur le § T.1 — mathématiques 4 h, anglais
`(2)+1+1` avec sa séance de groupe, français `2+1+1+①` en 7ᵉ et 8ᵉ — et ajouter
`COLLEGE_*_PILOTE` pour le § T.3. Le seeder compare les versions au lieu de se
taire quand des programmes existent, sans quoi la correction n'atteindrait
aucune base en service. Les données déjà copiées chez un établissement se
rattrapent par `docs/sql/rattrapage-volumes-t1.sql`.
Couverture : `NationalPatternSeederTest`, 12 tests.

### Étape I — Vider le catalogue de ce qu'il ne commande pas — **faite**
Écrire les deux SOFT qui correspondent à un article du texte —
`BALANCED_TEACHER_WORKLOAD` (§ II.2) et `MAIN_SUBJECT_BALANCED_DISTRIBUTION`
(§ III.1) — et retirer du catalogue les quatre codes restants : trois règles
dures appliquées en dur, donc jamais désactivables, et une préférence que la
circulaire ne demande nulle part. `NON_CABLEES` est vide. Les dix libellés
manquants du panneau d'explication sont écrits au passage.
Couverture : `RepartitionHebdomadaireTest`, 11 tests ; migration 016.

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
| G | Déplace des violations dans les deux sens : quelques-unes apparaissent (trous et demi-journées courtes que la quinzaine masquait), quelques-unes disparaissent (trous qu'aucune semaine ne voit). Sensible seulement là où il y a des quinzaines. |
| H | Retire 2 h de mathématiques et 1 h d'anglais à chaque classe, et rend à l'anglais sa séance de groupe. C'est ce qui débloque `VOLUME_HORAIRE`. Les affectations d'enseignants bâties sur les anciens volumes deviennent excédentaires. |
| I | Deux préférences SOFT de plus à satisfaire, donc un score souple plus bas à emploi du temps constant. Aucune règle dure ne change : les codes retirés du catalogue étaient appliqués en dur et le restent. |

---

## 7. Ce que ce plan ne fera pas

Conformément à la règle finale : **aucune lesson ne sera supprimée, aucun volume
horaire diminué, aucune contrainte affaiblie** dans le seul but d'obtenir un
score positif.

L'étape H ramène les mathématiques de 6 h à 4 h : c'est une diminution, et il
faut dire pourquoi elle ne contredit pas cette règle. Elle n'est pas faite pour
alléger le problème mais parce que le § T.1 dit 4 h. Le sens de la règle est
qu'on ne rabote pas le programme pour faire passer le solveur ; ici c'est
l'inverse — les données s'écartaient du programme, et le solveur, mieux
contraint, l'a rendu visible. Le fait que ce soit plus facile à placer est une
conséquence, pas un motif.

Avec 3 professeurs d'EPS pour 23 classes à 3 séances hebdomadaires, et vendredi
et samedi sans après-midi, l'établissement 28 **restera infaisable** après ces
corrections. Ce n'est pas une régression : les violations existent déjà dans le
monde réel, le système les ignorait. Le résultat attendu est que le système
dise que c'est infaisable, **et pourquoi**.
