# Corpus « Consigne ministérielle » — circulaire n°66 du 04/09/2024

> Ministère de l'Éducation (Tunisie) — Direction de la pédagogie et des
> référentiels du cycle préparatoire et de l'enseignement secondaire.
> *Recommandations pédagogiques et organisationnelles pour l'élaboration des
> emplois du temps dans les collèges, les collèges techniques et les collèges
> pilotes* + *Guide de répartition des séances d'enseignement*.
> Année scolaire 2024-2025.

## Comment ce fichier est fait, et pourquoi

Le PDF source (`docs/consigne.pdf`) est un **scan** : sept images, aucune
couche texte, texte en arabe. Une extraction automatique (`pdftotext` rend zéro
caractère) est impossible, et un OCR arabe sur un scan légèrement penché
détruirait les trois tableaux — qui sont précisément la partie chiffrée.

Le corpus est donc **transcrit et relu à la main**, article par article, et
versionné. Ce n'est pas un contournement : un corpus réglementaire de référence
se vérifie, il ne s'OCRise pas à l'aveugle. La contrepartie est assumée et doit
être dite : l'extraction automatique de documents scannés arabes est **hors
périmètre** de ce travail ; en échange, la traçabilité page / article est
garantie et chaque chunk porte le texte arabe original à côté de sa traduction.

**Un chunk = un article**, jamais un paragraphe coupé arbitrairement. Seul
l'article III.2 est éclaté en trois (`III.2.a`, `III.2.b`, `III.2.c`) : ses
trois alinéas énoncent trois règles indépendantes qui se traduisent en trois
contraintes différentes ; les garder ensemble aurait rendu la recherche floue
et la proposition de règle ambiguë.

**Texte normatif et commentaire sont séparés, et c'est mesuré.** Dans les
chunks d'articles, le premier paragraphe est la traduction de l'article ; tout
ce qui suit `**Lecture pour le solveur** —` est notre analyse, pas la
circulaire. Seul le texte normatif est vectorisé. La raison n'est pas
esthétique : en indexant aussi le commentaire, les vingt-deux chunks se
ressemblaient tous — « donne-moi une recette de couscous » atteignait 0,639 de
similarité, au-dessus de la pire question légitime (0,625), et aucun seuil ne
pouvait plus séparer les deux. En n'indexant que l'article, le hors-sujet
retombe à 0,585 et le pire cas légitime monte à 0,637 : un plancher existe à
nouveau. Les tableaux (§ T.1 à T.3) et la légende (§ N.1) échappent à la
règle — ils sont de la transcription de bout en bout, il n'y a rien à en
retirer.

**Format de parsing.** Le fichier est une suite de blocs. Chaque bloc s'ouvre
par une ligne `---`, contient ses métadonnées, se referme par une ligne `---`,
puis vient le corps en markdown jusqu'au bloc suivant. Le corps ne contient
donc **jamais** de ligne `---` isolée. Tout ce qui précède le premier `---` est
cet en-tête, ignoré à l'indexation.

Métadonnées de chaque chunk :

| Champ | Rôle |
|---|---|
| `id` | Numéro d'article de la circulaire — sert à citer (« § II.2 »). |
| `page` | Page du PDF, pour remonter au scan. |
| `section` | Intitulé de la partie dont l'article relève. |
| `portee` | `DslScope` pré-mappé, ou `null` si l'article ne se traduit pas en contrainte de solveur. |
| `severite_suggeree` | `DslSeverity` proposée par défaut, ou `null`. |

`portee` et `severite_suggeree` sont une **proposition**, pas une décision : la
circulaire ne dit pas « HARD » ou « SOFT ». Le mapping est notre lecture, il
pré-remplit l'écran de confirmation, et c'est un humain qui tranche.

---
id: I.1
page: 2
section: Recommandations concernant l'élève et la classe
portee: null
severite_suggeree: null
---
Une même matière, dans une même classe, est confiée à un seul enseignant.

**Lecture pour le solveur** — Cet article encadre l'**affectation** des enseignants aux classes, en amont de
la construction de l'emploi du temps. Dans la plateforme il relève des
attributions d'enseignement (`TeachingAssignment`), pas du solveur : il est
déjà structurellement vrai, une affectation liant un triplet
classe / matière / enseignant. Aucune contrainte DSL à en dériver.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § I.1
**Texte original** — يُسند القسم الواحد في كلّ مادّة إلى مدرّس واحد.
---
id: I.2
page: 2
section: Recommandations concernant l'élève et la classe
portee: CLASS_DAY
severite_suggeree: HARD
---
Les emplois du temps des élèves sont établis sur la base de six heures par jour
au maximum et de deux heures au minimum par demi-journée, matin ou après-midi.
Remarque : cette règle ne s'applique pas à la matière éducation physique.

**Lecture pour le solveur** — Deux seuils dans un seul article : un plafond journalier par classe, et un
plancher par demi-journée. Le plafond se traduit directement (`CLASS_DAY`,
`TOTAL_HOURS > 6`, `PENALIZE`, `HARD`). Le plancher est un minimum, donc une
demi-journée non vide de moins de deux heures : il se pénalise sur la
demi-journée concernée, pas sur la journée entière.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § I.2
**Texte original** — تعدّ جداول أوقات التّلاميذ على أساس ستّ ساعات في اليوم كحدّ أقصى وساعتين كحدّ أدنى صباحا أو مساء (ملاحظة: لا ينطبق هذا الأمر على مادّة التّربية البدنيّة).
---
id: I.3
page: 2
section: Recommandations concernant l'élève et la classe
portee: CLASS_DAY
severite_suggeree: HARD
---
La séparation entre les séances du matin et les séances de l'après-midi est de
deux heures, quel que soit le temps scolaire retenu par l'établissement.

**Lecture pour le solveur** — C'est la coupure méridienne. Elle vaut quelle que soit la grille horaire de
l'établissement (séance de 55 min ou d'une heure, journée continue ou non) : ce
n'est pas une préférence locale, c'est un plancher national.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § I.3
**Texte original** — يتمّ الفصل بين الحصص الصّباحيّة والمسائيّة للتعلم بساعتين مهما كان الزّمن المدرسي المعتمد.
---
id: I.4
page: 2
section: Recommandations concernant l'élève et la classe
portee: LESSON
severite_suggeree: MEDIUM
---
À l'exception des matières enseignées en salles spécialisées, il est interdit
de changer la salle de cours d'une même classe, afin de limiter au maximum les
déplacements des élèves à l'intérieur d'une même demi-journée.

**Lecture pour le solveur** — Contrainte de **stabilité de salle** : sur une demi-journée, une classe reste
dans sa salle, sauf pour les matières qui exigent une salle spécialisée
(laboratoire, atelier, salle d'informatique, salle de sport). L'exception n'est
pas un adoucissement de la règle, c'est sa condition de faisabilité : les
travaux pratiques du § III.4 imposent justement le déplacement.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § I.4
**Texte original** — باستثناء الموادّ الّتي تدرّس في قاعات الاختصاص، فإنّه يُحجّر تغيير قاعة الدّراسة بالنّسبة إلى القسم الواحد للحدّ من تنقّل التّلاميذ خلال الفترة الواحدة.
---
id: I.5
page: 2
section: Recommandations concernant l'élève et la classe
portee: CLASS_DAY
severite_suggeree: HARD
---
Les heures creuses sont interdites dans les emplois du temps des élèves.

**Lecture pour le solveur** — Une heure creuse est un trou entre deux séances d'une même demi-journée, pour
une même classe. La règle impose donc la **compacité** de la journée de
l'élève : les séances d'une demi-journée doivent être consécutives.

C'est la règle la plus coûteuse à satisfaire pour un solveur, et celle dont la
violation se voit immédiatement à l'œil sur un emploi du temps imprimé — d'où
son statut de contrainte dure.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § I.5
**Texte original** — تحجّر السّاعات الجوفاء في جداول أوقات التّلاميذ.
---
id: I.6
page: 2
section: Recommandations concernant l'élève et la classe
portee: null
severite_suggeree: null
---
Le principe d'équilibre et d'homogénéité est retenu pour la répartition des
élèves entre les classes, et entre les deux groupes d'une même classe.

**Lecture pour le solveur** — Cet article encadre la **constitution des classes et des groupes**, en amont de
l'emploi du temps : il porte sur les effectifs, pas sur le placement des
séances. Dans la plateforme il relève de la gestion des classes
(`ClassGroup`) ; le solveur reçoit les classes déjà constituées. Aucune
contrainte DSL à en dériver.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § I.6
**Texte original** — اعتماد مبدإ التّوازن والتّجانس في توزيع التّلاميذ بين الأقسام وبين فوجي القسم الواحد.
---
id: II.1
page: 2
section: Recommandations concernant l'enseignant
portee: TEACHER_WEEK
severite_suggeree: HARD
---
Les journées consacrées à la formation pédagogique doivent être respectées.

**Lecture pour le solveur** — L'enseignant a une ou plusieurs journées réservées à sa formation : aucune
séance ne peut y être placée. C'est une indisponibilité individuelle, exprimée
en pratique par une contrainte de non-disponibilité sur le couple
enseignant / jour.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § II.1
**Texte original** — احترام الأيّام المخصّصة للتكوين البيداغوجي.
---
id: II.2
page: 2
section: Recommandations concernant l'enseignant
portee: TEACHER_DAY
severite_suggeree: HARD
---
L'horaire hebdomadaire dû par l'enseignant est réparti de manière équilibrée
sur les jours de travail, sans compter la journée consacrée à la formation. Les
emplois du temps sont établis sur la base de six heures d'enseignement par jour
au maximum et de deux heures au minimum, matin ou après-midi. Les heures
d'enseignement effectivement réalisées sont celles qui sont comptabilisées dans
l'équilibre de l'emploi du temps.

**Lecture pour le solveur** — C'est le symétrique exact du § I.2, côté enseignant : mêmes seuils (6 h par
jour, 2 h par demi-journée), mais regroupés par enseignant et non par classe.
L'article ajoute une exigence de **répartition équilibrée sur la semaine** : ce
n'est pas seulement un plafond journalier, c'est une interdiction de concentrer
tout le service sur deux jours.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § II.2
**Texte original** — يوزّع التّوقيت الأسبوعي المستوجب للمدرّس بصفة متوازنة على أيّام العمل، دون اعتبار اليوم المخصّص للتكوين. وتعدّ جداول الأوقات على أساس ستّ ساعات تدريس في اليوم كحدّ أقصى، وساعتين كحدّ أدنى صباحا أو مساء. هذا وتحتسب ساعات التّدريس المنجزة فعليّا في موازنة الجدول.
---
id: II.3
page: 2
section: Recommandations concernant l'enseignant
portee: TEACHER_DAY
severite_suggeree: MEDIUM
---
L'enseignant peut se voir confier cinq heures d'enseignement consécutives les
vendredi et samedi.

**Lecture pour le solveur** — Exception explicite au plafond du § II.2, limitée à deux jours de la semaine.
Elle existe parce que le samedi n'a qu'une demi-journée : sans elle, un
enseignant ne pourrait pas y faire un service utile.

Elle se traduit non pas comme une règle à ajouter mais comme une **dérogation à
appliquer aux règles de consécutivité** : la limite d'heures consécutives passe
à cinq le vendredi et le samedi.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § II.3
**Texte original** — يمكن تكليف المدرّس بخمس ساعات تدريس مسترسلة يومي الجمعة والسّبت.
---
id: II.4
page: 2
section: Recommandations concernant l'enseignant
portee: TEACHER_WEEK
severite_suggeree: MEDIUM
---
L'alternance est retenue, lors de l'élaboration de l'emploi du temps de chaque
enseignant, entre les séances du matin et les séances de l'après-midi, dans
toutes les matières quelle que soit la spécialité, au cours des quatre premiers
jours de la semaine.

**Lecture pour le solveur** — L'enseignant ne doit pas être cantonné aux matinées ni aux après-midis : sur
lundi, mardi, mercredi et jeudi, son service alterne entre les deux
demi-journées. Les vendredi et samedi sont hors du champ de la règle — c'est
cohérent avec le § II.3.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § II.4
**Texte original** — تعتمد المراوحة، عند إعداد جدول كلّ مدرّس، بين الحصص الصّباحيّة والحصص المسائيّة في كلّ الموادّ مهما كان الاختصاص خلال الأيّام الأربعة الأولى من الأسبوع.
---
id: II.5
page: 2
section: Recommandations concernant l'enseignant
portee: TEACHER_WEEK
severite_suggeree: SOFT
---
Deux niveaux d'études différents au moins sont confiés à l'enseignant.

**Lecture pour le solveur** — Article d'affectation davantage que d'emploi du temps : il porte sur la
répartition des classes entre enseignants. Il se vérifie néanmoins sur le
planning produit, en comptant les niveaux distincts touchés par un enseignant
sur la semaine — d'où la portée `TEACHER_WEEK` et une sévérité faible : il ne
peut être satisfait que si l'établissement dispose de plusieurs niveaux à
confier.

**Source** — Circulaire n°66 du 04/09/2024, p. 2, § II.5
**Texte original** — يسند إلى المدرّس مستويان دراسيّان مختلفان على الأقلّ.
---
id: II.6
page: 3
section: Recommandations concernant l'enseignant
portee: null
severite_suggeree: null
---
Il est impératif d'assurer le nombre d'heures dû par l'enseignant, avec la
possibilité de lui attribuer des heures supplémentaires en cas de nécessité.

**Lecture pour le solveur** — Article de gestion du service : il porte sur le volume horaire dû et les heures
supplémentaires, c'est-à-dire sur le contrat de l'enseignant. Il fixe une
donnée d'**entrée** du solveur (le service à couvrir), pas une contrainte à
faire respecter par lui.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § II.6
**Texte original** — ضرورة الالتزام بتأمين العدد اللاّزم من السّاعات المستوجبة من قبل المدرس مع إمكانيّة إسناد ساعات إضافيّة عند الاقتضاء.
---
id: III.1
page: 3
section: Recommandations concernant les apprentissages
portee: LESSON
severite_suggeree: SOFT
---
Les heures hebdomadaires prévues pour une même matière sont réparties sur les
périodes du matin et de l'après-midi, quelle que soit cette matière.

**Lecture pour le solveur** — Règle de non-concentration : une matière ne doit pas être intégralement massée
sur une seule demi-journée type. Elle est le cadre général dont le § III.2.a
est le cas particulier chiffré pour les matières fondamentales.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § III.1
**Texte original** — توزّع السّاعات الأسبوعيّة المقرّرة للمادّة الواحدة، على الفترات الصّباحيّة والمسائيّة مهما كانت هذه المادّة.
---
id: III.2.a
page: 3
section: Recommandations concernant les apprentissages
portee: LESSON
severite_suggeree: MEDIUM
---
Les cours sont programmés, à l'intérieur d'une même période — matinée ou
après-midi — selon la règle de diversification des apprentissages. À ce titre :
trois quarts de l'horaire de chaque matière fondamentale dans la formation de
l'élève (langue arabe, langue française, mathématiques) sont programmés sur la
période du matin, de manière équilibrée sur l'étendue de la semaine, et le
quart restant est programmé sur la période de l'après-midi.

**Lecture pour le solveur** — C'est l'article le plus quantitatif de la circulaire, et le seul qui se traduise
naturellement par une **récompense** plutôt que par une interdiction : on ne
peut pas interdire l'après-midi aux mathématiques, puisqu'un quart de leur
horaire doit précisément y aller. On valorise donc les séances de matière
fondamentale placées le matin, et l'équilibre sur la semaine reste porté par le
§ III.1.

Matières fondamentales visées, avec leur horaire hebdomadaire (voir § T.1) :
arabe (5 h), français (5 h en 7ᵉ et 8ᵉ, 5 h en 9ᵉ), mathématiques (4 h en
collège, 5 h en collège pilote). Trois quarts de 4 h font 3 h le matin et 1 h
l'après-midi.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § III.2 (1ᵉʳ alinéa)
**Texte original** — تبرمج الدّروس، في الفترة الواحدة، صباحيّة كانت أم مسائيّة، على قاعدة تنويع التّعلّمات كما يلي: يبرمج ثلاثة أرباع توقيت كلّ مادّة أساسيّة في تكوين التّلميذ (اللّغة العربيّة واللّغة الفرنسيّة والرّياضيات) في الفترة الصّباحيّة بصفة متوازنة وعلى امتداد الأسبوع، ويبرمج الرّبع الآخر في الفترة المسائيّة.
---
id: III.2.b
page: 3
section: Recommandations concernant les apprentissages
portee: LESSON
severite_suggeree: MEDIUM
---
En ce qui concerne la matière éducation physique, l'horaire hebdomadaire est
programmé soit en trois séances espacées, soit en deux séances dont l'une de
deux heures et l'autre d'une heure. Remarque : il faut toujours respecter la
règle de séparation de vingt-quatre heures entre deux séances d'éducation
physique.

**Lecture pour le solveur** — Deux découpages autorisés pour un même volume de 3 h (voir § T.1, ligne
éducation physique : `2+1`), et une contrainte d'**espacement** de 24 heures
entre deux séances. La séparation de 24 h n'est pas une préférence de confort :
elle a une justification physiologique — un délai de récupération — et c'est ce
qui la distingue du § III.2.c, qui relève lui de la pédagogie.

Deux séances le même jour, ou sur deux jours consécutifs à des heures proches,
violent la règle.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § III.2 (2ᵉ alinéa)
**Texte original** — فيما يتعلّق بمادّة التّربية البدنيّة، يبرمج التّوقيت الأسبوعي إمّا في ثلاث ساعات متباعدة أو في حصّتين إحداهما بساعتين والأخرى بساعة (ملاحظة: ينبغي دائما احترام قاعدة الفصل بين حصّتي التّربية البدنيّة بـ 24 ساعة).
---
id: III.2.c
page: 3
section: Recommandations concernant les apprentissages
portee: LESSON
severite_suggeree: MEDIUM
---
Les matières enseignées à raison de deux heures par semaine ne sont pas
programmées sur deux jours consécutifs.

**Lecture pour le solveur** — Règle d'espacement pédagogique : entre deux séances d'une même matière à faible
volume, il faut laisser au moins un jour, pour que le travail personnel de
l'élève ait le temps de s'intercaler.

Elle vise, d'après les tableaux, l'histoire-géographie (`1+1`), l'éducation
islamique et l'éducation civique (`①+1`), et toutes les matières à deux séances
hebdomadaires d'une heure.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § III.2 (3ᵉ alinéa)
**Texte original** — لا تبرمج الموادّ الّتي تدرّس بحساب ساعتين في الأسبوع على يومين متتاليين.
---
id: III.3
page: 3
section: Recommandations concernant les apprentissages
portee: LESSON
severite_suggeree: SOFT
---
Le lien est établi entre les matières qui adoptent le système des groupes et
les séances de quinzaine.

**Lecture pour le solveur** — Les matières en système de groupes (notées `(2)` ou `(3)` dans les tableaux) et
les matières à séance quinzaine (notées `①`) doivent être **coordonnées** :
quand une classe est scindée en deux groupes pour l'anglais, le second groupe
doit avoir cours en même temps, typiquement la matière en quinzaine. C'est ce
qui évite qu'une moitié de classe se retrouve sans cours.

C'est une contrainte de **simultanéité entre séances**, la plus difficile des
quinze à exprimer dans le DSL actuel : celui-ci évalue chaque séance ou chaque
cumul, mais ne met pas deux séances en relation. À signaler comme limite
identifiée plutôt qu'à mal traduire.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § III.3
**Texte original** — يتمّ الرّبط بين الموادّ الّتي تعتمد نظام الأفواج والحصص نصف الشّهريّة.
---
id: III.4
page: 3
section: Recommandations concernant les apprentissages
portee: LESSON
severite_suggeree: HARD
---
Les séances de travaux pratiques se déroulent dans les salles spécialisées et
non ailleurs, sur la base d'une exploitation complète de ces salles. Il n'est
recouru aux salles ordinaires qu'en cas de nécessité absolue (nombre
insuffisant de salles spécialisées). De même, on évite d'affecter les salles
spécialisées à l'enseignement des matières ordinaires.

**Lecture pour le solveur** — Deux règles en une, et elles vont dans des directions opposées : les travaux
pratiques exigent une salle spécialisée, et les salles spécialisées ne doivent
pas être occupées par des matières qui n'en ont pas besoin. La seconde est la
condition de faisabilité de la première — un laboratoire monopolisé par des
cours d'histoire n'est plus disponible pour les TP.

La clause « en cas de nécessité absolue » explique pourquoi la contrainte, bien
que dure dans son intention, doit rester **levable** par l'établissement : un
collège qui n'a qu'un laboratoire ne peut pas la satisfaire, et un emploi du
temps infaisable ne rend service à personne.

**Source** — Circulaire n°66 du 04/09/2024, p. 3, § III.4
**Texte original** — تنجز حصص الأشغال التّطبيقيّة في قاعات الاختصاص دون سواها على أساس تأمين الاستغلال التّامّ لها ولا يلجأ إلى استخدام القاعات العاديّة إلاّ عند الضّرورة القصوى (عدم توفّر العدد الكافي من قاعات الاختصاص) كما يتجنّب تخصيصها لتدريس الموادّ العاديّة.
---
id: N.1
page: 5
section: Guide de répartition des séances — notation des tableaux
portee: null
severite_suggeree: null
---
Notation employée dans les trois tableaux de volumes horaires (§ T.1, T.2, T.3).
Elle n'est pas décorative : elle décrit le **découpage** des séances, pas
seulement leur total.

- `2+1+1+1` — l'horaire hebdomadaire est découpé en séances de ces durées :
  ici 5 heures en quatre séances, une de deux heures et trois d'une heure. La
  somme donne le volume, mais c'est le découpage qui contraint le solveur.
- `(2)` et `(3)` — séance hebdomadaire **en système de groupes** : la classe est
  scindée, la séance est donnée deux ou trois fois. Le chiffre est la durée en
  heures. Conséquence directe : l'enseignant y fait plus d'heures que l'élève
  n'en reçoit.
- `①` — séance **de quinzaine** (une semaine sur deux) pour la classe entière.
  C'est la notion de parité de semaine du solveur (`WeekParity`).
- `(*)` dans le tableau des collèges — ligne applicable aux seuls collèges qui
  assurent l'éducation théâtrale.

**Source** — Circulaire n°66 du 04/09/2024, p. 5 à 7, légendes des tableaux
**Texte original** — (2)، (3): حصّة أسبوعيّة بنظام الأفواج. ① : حصّة نصف شهريّة لكامل الفصل. (*): بالنّسبة إلى المدارس الإعداديّة الّتي تؤمّن مادّة التربية المسرحيّة.

---
id: T.1
page: 5
section: Guide de répartition des séances — collèges
portee: null
severite_suggeree: null
---
Combien d'heures de cours par semaine, matière par matière, en 7ᵉ, 8ᵉ et 9ᵉ
année de **collège** (المدارس الإعداديّة) : volumes horaires hebdomadaires et
découpage des séances. Notation expliquée au § N.1.

| Matière | 7ᵉ année | 8ᵉ année | 9ᵉ année |
|---|---|---|---|
| Arabe | 2+1+1+1 | 2+1+1+1 | 2+1+1+1 |
| Français | 2+1+1+① | 2+1+1+① | 2+1+1+1 |
| Anglais | (2)+1+1 | (2)+1+1 | (2)+1+1 |
| Histoire-géographie | 1+1 | 1+1 | 1+1 |
| Éducation islamique | ①+1 | ①+1 | ①+1 |
| Éducation civique | ①+1 | ①+1 | ①+1 |
| Mathématiques | 1+1+1+1 | 1+1+1+1 | 1+1+1+1 |
| Sciences physiques | ①+(2) | ①+(2) | ①+(2) |
| Sciences de la vie et de la Terre | ①+(2) | ①+(2) | ①+(2) |
| Informatique | (2) | (2) | (2) |
| Éducation technologique | (3) | (2) | (2) |
| Éducation musicale | 1 | 1 | 1 |
| Arts plastiques | 1 | 1 | 1 |
| Éducation théâtrale (*) | (2) | (2) | (2) |
| Éducation physique | 2+1 | 2+1 | 2+1 |

Trois lectures utiles pour la construction de l'emploi du temps :

- **Matières fondamentales du § III.2.a** — arabe 5 h, français 5 h,
  mathématiques 4 h. Trois quarts le matin : 3 h 45, 3 h 45 et 3 h.
- **Matières visées par le § III.2.c** (2 h par semaine, jamais deux jours
  consécutifs) — histoire-géographie, et de fait éducation islamique et
  éducation civique dont la séance `①` est bimensuelle.
- **Éducation physique** — `2+1` correspond exactement au second découpage
  autorisé par le § III.2.b, avec les 24 h de séparation.

**Source** — Circulaire n°66 du 04/09/2024, p. 5 (guide, page 4/2)

---
id: T.2
page: 6
section: Guide de répartition des séances — collèges techniques
portee: null
severite_suggeree: null
---
Combien d'heures de cours par semaine, matière par matière, en 7ᵉ année de
qualification technique, 8ᵉ et 9ᵉ année de **collège technique**
(المدارس الإعداديّة التّقنيّة). Ce tableau est le seul des trois à distinguer
**l'horaire de l'élève** de **l'horaire de l'enseignant** : dès qu'une matière
passe en système de groupes, l'enseignant répète la séance et fait donc plus
d'heures que l'élève n'en reçoit. Toutes les valeurs sont en heures par semaine.

| Matière | 7ᵉ (élève / prof) | 8ᵉ (élève / prof) | 9ᵉ (élève / prof) |
|---|---|---|---|
| Arabe | 2 / 2 | 2 / 2 | 2 / 2 |
| Français | 3 / 3 | 3 / 3 | 2 / 2 |
| Anglais (*) | 3 / 4 | 3 / 4 | 2 / 3 |
| Histoire-géographie | 2 / 2 | 2 / 2 | 2 / 2 |
| Éducation islamique | 1 / 1 | 1 / 1 | 1 / 1 |
| Éducation civique | 1 / 1 | 1 / 1 | 1 / 1 |
| Mathématiques | 3 / 3 | 3 / 3 | 2 / 2 |
| Éducation physique | 2 / 2 | 2 / 2 | 2 / 2 |
| Sciences physiques | 2 / 4 | 2 / 4 | 1 / 2 |
| Sciences de la vie et de la Terre | 1 / 2 | 1 / 2 | — |
| Informatique | 2 / 4 | 2 / 4 | 2 / 4 |
| Formation technique 7ᵉ année | 4 / 8 | — | — |
| Formation technique 8ᵉ année | — | 4 / 8 | — |
| Formation technique 9ᵉ année (**) | — | — | 6 / 18 |
| Activités spécifiques (***) | 6 / 12 | 6 / 18 | 10 / 30 |

Les effectifs sont fixés par bloc de matières, et ils dictent le nombre de
groupes donc le nombre de séances à placer :

- **Arabe → éducation physique** — enseignées en classe entière. L'anglais (*)
  fait exception : le système de groupes (deux groupes) s'applique à l'une des
  heures de l'élève. Effectif d'une classe : 30 élèves au plus.
- **Sciences physiques → formation technique 8ᵉ** — enseignées en système de
  groupes. Effectif d'un groupe : 15 élèves au plus.
- **Formation technique 9ᵉ et activités spécifiques** — système de groupes avec
  la classe divisée en **trois** groupes. Effectif d'un groupe : 10 élèves au
  plus. C'est ce qui explique le rapport 6 / 18 et 10 / 30.

(**) L'enseignement de la formation technique est assuré par le même enseignant
que celui chargé des activités spécifiques rattachées au sous-domaine, selon la
règle de rotation périodique en vigueur pour les activités spécifiques.
(***) Les élèves tournent sur les sous-domaines rattachés au domaine
professionnel.
La langue italienne est enseignée dans le cadre de clubs, à raison de deux
heures par semaine, en 8ᵉ et 9ᵉ année de l'enseignement préparatoire technique.

**Source** — Circulaire n°66 du 04/09/2024, p. 6 (guide, page 4/3)

---
id: T.3
page: 7
section: Guide de répartition des séances — collèges pilotes
portee: null
severite_suggeree: null
---
Combien d'heures de cours par semaine, matière par matière, en 7ᵉ, 8ᵉ et 9ᵉ
année de **collège pilote** (المدارس الإعداديّة النّموذجيّة) : volumes horaires
hebdomadaires et découpage des séances. Notation au § N.1.

| Matière | 7ᵉ année | 8ᵉ année | 9ᵉ année |
|---|---|---|---|
| Arabe | 2+1+1+1 | 2+1+1+1 | 2+1+1+1 |
| Français | 2+1+1+1 | 2+1+1+1 | 2+1+1+1 |
| Anglais | (2)+1+1+1 | (2)+1+1+1 | (2)+1+1+1 |
| Histoire-géographie | 1+1 | 1+1 | 1+1 |
| Éducation islamique | ①+1 | ①+1 | ①+1 |
| Éducation civique | ①+1 | ①+1 | ①+1 |
| Mathématiques | 1+1+1+1+1 | 1+1+1+1+1 | 1+1+1+1+1 |
| Sciences physiques | ①+(2) | ①+(2) | ①+(2) |
| Sciences de la vie et de la Terre | ①+(2) | ①+(2) | ①+(2) |
| Informatique | (2) | (2) | (2) |
| Éducation technologique | (3) | (2) | (2) |
| Éducation physique | 2+1 | 2+1 | 2+1 |
| Éducation musicale | 1 | 1 | 1 |
| Arts plastiques | 1 | 1 | 1 |
| Éducation théâtrale | (2) | (2) | (2) |

Écarts avec le collège ordinaire (§ T.1), qui sont exactement ce qui distingue
un collège pilote :

- **Mathématiques : 5 h au lieu de 4** (`1+1+1+1+1`), soit cinq séances d'une
  heure — donc au moins une par jour de la semaine.
- **Français : 5 h pleines** en 7ᵉ et 8ᵉ, sans la séance de quinzaine `①` du
  collège ordinaire.
- **Anglais : 4 h au lieu de 3.**
- **Éducation théâtrale sans la restriction `(*)`** : elle est assurée dans tous
  les collèges pilotes.

**Source** — Circulaire n°66 du 04/09/2024, p. 7 (guide, page 4/4)
