# Plan — prédiction de la consommation des ressources

> **Objet.** Faire passer la supervision du *constat* à l'*anticipation* : que
> l'écran ne dise plus seulement « la mémoire est à 62 % », mais « à ce rythme,
> l'alerte sera atteinte dans 2 h 10 » — ou, tout aussi utile, « aucune tendance
> significative sur l'heure écoulée ».
>
> **La méthode : une régression linéaire** sur les métriques que Prometheus
> collecte déjà. Pas d'apprentissage préalable, pas de modèle sauvegardé : la
> droite est recalculée à chaque demande sur l'historique le plus récent.
>
> **Chantier voisin :** la supervision technique (tuiles KPI, courbes de
> tendance — commits `69b2e81`, `bdd8ccf`, `97af3db`). Celui-ci n'ajoute aucune
> métrique collectée : il exploite l'historique que Prometheus garde déjà.

---

## 0. Où nous en sommes

| Étape | État |
|---|---|
| 1 — Le calcul : régression, R², temps avant seuil | **fait** — route `/api/monitoring/forecast` |
| 2 — L'écran : la projection sur les tuiles | **fait** — pointillé et ligne d'échéance |
| 3 — L'assistant : « quand la mémoire va-t-elle saturer ? » | **fait** — outil `get_resource_forecast` |
| 4 — Le panneau : la prévision en grand, à côté de l'assistant | **fait** — `PrevisionPanel` |

### Ce qui existe déjà

**La chaîne de collecte fonctionne.** Le backend expose ses métriques via
Actuator + Micrometer sur `/actuator/prometheus` (port 9091) ; Prometheus les
collecte toutes les 15 s (`monitoring/prometheus.yml`) et les garde **15 jours**
(`--storage.tsdb.retention.time=15d`, `docker-compose.yml`), dans un volume
persistant. L'historique nécessaire à une régression est donc déjà là.

**Le service Python sait lire une plage.** `PrometheusClient.query_range`
(`ai-assistant/app/clients/prometheus.py:75`) interroge `/api/v1/query_range`,
et `MetricsService.get_trends` (`app/services/metrics.py:170`) s'en sert pour
les courbes des tuiles.

**Les expressions et les seuils sont centralisés.**
`HISTORY_METRICS` (`metrics.py:69`) porte les expressions PromQL déjà
normalisées dans l'unité d'affichage (%, ms) ; `THRESHOLDS` (`metrics.py:154`)
porte les seuils d'alerte et d'incident partagés avec l'écran.

**L'écran a réservé le pointillé à la projection.** `KpiCard.tsx:228` :
« Traits pleins, jamais pointillés : un pointillé se lit comme une projection,
pas comme une limite. » La convention visuelle de l'étape 2 est déjà posée.

---

## 1. Le problème

- **P1 — L'écran ne dit que le présent.** La courbe montre d'où l'on vient, la
  jauge où l'on en est ; rien ne dit où l'on va. Le super admin doit extrapoler
  à l'œil, et un œil extrapole mal une dent de scie.
- **P2 — L'alerte arrive quand il est trop tard.** Un seuil franchi déclenche
  l'alerte au moment où le problème est déjà là. Une fuite mémoire se voit des
  heures avant, dans la pente — encore faut-il la calculer.
- **P3 — L'assistant ne sait pas répondre « quand ? ».** `get_metric_history`
  rend min / moyenne / max et un mot (`rising`, `stable`, `falling`), déduit
  d'un écart de ±15 % à la moyenne (`metrics.py:375`). Il sait dire « ça
  monte », jamais « ça atteindra le seuil dans combien de temps ».

---

## 2. L'architecture retenue

```
Backend Spring ──(/actuator/prometheus)──► Prometheus ──(query_range)──► Service Python ──► Écran / Assistant
  expose les métriques     collecte toutes les 15 s,      lit l'historique,          affiche la prévision
                           stocke la série (sa TSDB)      calcule la régression
```

Chaque composant garde un seul rôle : **collecter, stocker, prédire,
afficher**. Prometheus ne prédit rien ; le service Python ne stocke rien.
Prometheus reste la seule source des données — aucune copie, aucune seconde base.

### Pourquoi le calcul en Python, et non `predict_linear()` de PromQL

Prometheus propose nativement `predict_linear(v[1h], t)`, qui fait la même
régression par moindres carrés. Il est écarté pour trois raisons :

1. **Pas de mesure de confiance.** `predict_linear` rend une valeur projetée,
   jamais le R². On ne peut pas distinguer une vraie tendance d'une droite
   tirée à travers du bruit — or c'est précisément le cas le plus fréquent
   (§ 3, mesures réelles).
2. **Pas de temps avant seuil.** Il faudrait le déduire en inversant la
   projection, ce qui revient à refaire le calcul à côté.
3. **Testable hors ligne.** En Python, la régression est une fonction pure,
   testée sur des séries construites à la main, sans Prometheus.

`deriv()` (la pente seule, calculée par Prometheus) sert en revanche de
**contre-vérification** : sur une vraie série, la pente calculée en Python doit
coïncider avec la sienne.

### Les formules

Sur les `n` points `(tᵢ, yᵢ)` de la fenêtre, avec `t̄` et `ȳ` les moyennes :

- **pente** `a = Σ(tᵢ − t̄)(yᵢ − ȳ) / Σ(tᵢ − t̄)²` — vitesse de variation (%/h) ;
- **ordonnée** `b = ȳ − a·t̄` ;
- **R²** `= 1 − Σ(yᵢ − ŷᵢ)² / Σ(yᵢ − ȳ)²` — part de la variation expliquée par
  la droite, de 0 (bruit pur) à 1 (droite parfaite) ;
- **temps avant seuil** `= (seuil − ŷ(maintenant)) / a`, calculé seulement si
  `a > 0` et si la valeur actuelle est encore sous le seuil.

Pas de dépendance à ajouter : quelques lignes de Python standard suffisent
(`statistics.linear_regression` existe depuis Python 3.10, mais écrire les
formules en clair les rend citables dans le rapport).

---

## 3. Ce que montrent les mesures réelles

Régression testée le 10/09/2026 sur les séries stockées par Prometheus :
8 sessions de l'API, dernière heure de chacune, un point par minute.

| Série régressée | R² observés | Lecture |
|---|---|---|
| Heap brute (`HISTORY_METRICS["memory"]`) | 0,00 à 0,34 | aucune tendance exploitable |
| Plancher de la heap (min sur 5 min) | 0,00 à 0,50 | le bon signal, mais pas de fuite sur la démo |

Trois conséquences qui fixent la conception :

1. **La heap est une dent de scie.** Le ramasse-miettes la fait monter puis
   chuter en permanence ; une droite tirée à travers mesure le rythme du GC, pas
   la consommation. **On régresse sur le plancher après GC** — le minimum sur
   une fenêtre glissante de 5 min, `min_over_time((expr)[5m:])` — car c'est ce
   plancher qui monte lors d'une fuite mémoire. C'est une série *différente* de
   celle de la tuile, et le code doit le dire.
2. **Sur le jeu de démo, la réponse honnête est « stable ».** La heap tourne
   entre 2 et 8 % du maximum, face à des seuils à 75 et 90 %. Pour montrer une
   projection en soutenance, il faudra un **scénario de charge** (test de charge
   pendant la démonstration), pas des données retouchées.
3. **Le R² doit conditionner le verdict.** Sans lui, le système annoncerait
   « alerte dans 40 h » sur une droite qui n'explique rien de la série.

---

## 4. Ordre d'exécution

### Étape 1 — Le calcul : régression, R², temps avant seuil

**Client.** `query_range` jette les horodatages et retire les trous
(`prometheus.py:117-123`). Pour la régression, l'axe du temps doit être réel :
un redémarrage retire vingt points, et sans horodatage les points suivants
seraient décalés d'autant. Ajouter une variante qui rend les couples
`(horodatage, valeur)` ; `query_range` en devient une simple projection, sans
changer son contrat (ses 7 tests existants doivent rester verts).

**Calcul pur.** Un module `app/services/prevision.py` :

- `ajuster(points) -> Ajustement(pente, ordonnee, r2, n)` — les formules du § 2 ;
- `prevoir(points, seuils, horizon) -> Prevision` — applique les garde-fous et
  rend un verdict.

**Garde-fous** (chacun a son test) :

| Situation | Verdict |
|---|---|
| Moins de 10 points, ou fenêtre couverte à moins de moitié (redémarrage) | `insuffisant` |
| R² sous 0,5, et le seuil d'alerte **à portée** de la pente haute | `incertain` — « pas de tendance significative » |
| R² sous 0,5, mais seuil **hors d'atteinte** même avec la pente haute | `stable` à l'horizon |
| Pente positive, seuil atteint avant l'horizon | `hausse` + minutes avant alerte / incident |
| Pente positive mais seuil au-delà de l'horizon | `stable` à l'horizon |
| Pente négative | `baisse` |
| Série constante (dénominateur nul) | `stable`, jamais une division par zéro |

**Écart au plan initial : la pente haute.** Le tableau d'origine envoyait tout
R² sous 0,5 vers `incertain`. Or le § 3 mesure des R² de 0,00 à 0,50 sur la
démo : la démo aurait affiché « tendance incertaine » sur toutes les tuiles,
alors que la réponse honnête y est « stable ». Un R² faible dit « la droite
n'explique pas la série », pas « le seuil est peut-être proche ». D'où une
seconde question, posée seulement quand le R² est faible : *même avec la pente
la plus défavorable que le bruit autorise*, le seuil d'alerte est-il atteignable
avant l'horizon ?

- **pente haute** `= a + 2·σₐ`, avec l'erreur-type de la pente
  `σₐ = √( Σ(yᵢ − ŷᵢ)² / (n − 2) / Σ(tᵢ − t̄)² )` — environ 95 % de confiance ;
- si `ŷ(maintenant) + max(0, pente haute) × horizon < seuil d'alerte` : `stable` ;
- sinon : `incertain`, et aucune durée.

Une heap qui oscille entre 2 et 8 % face à 75 % est donc `stable` ; la même
oscillation entre 65 et 80 % reste `incertain`. La règle du § 5 tient : aucune
échéance n'est jamais tirée d'une droite à R² faible.

**Réalisé.** `query_range_points` (`prometheus.py`) rend les couples
`(horodatage, valeur)` et `query_range` en est la projection.
`app/services/prevision.py` porte `ajuster` et `prevoir`, sans dépendance ni
horloge. `FORECAST_METRICS` (`metrics.py`) nomme les séries régressées ;
`get_forecast` lit 60 points par fenêtre (un par minute sur 1 h, jamais sous
15 s). Durées : `0` = seuil déjà franchi, `null` = pas avant l'horizon.
24 tests (23 dans `tests/test_prevision.py`, un dans `test_metrics.py`) ;
les 7 tests de `query_range` restent verts.

**Vérification réelle (10/09/2026).** Sur 16 sessions d'une heure stockées par
Prometheus 2.54, pente Python et `deriv((expr)[1h:1m])` coïncident à 2·10⁻¹²
près (écart relatif), mémoire comme CPU. Attention : sous Prometheus 2.x, la
sous-requête `[1h:1m]` est fermée des deux côtés — 61 points, pas 60 ; la
comparaison n'est exacte qu'avec la même borne. Verdicts obtenus : mémoire
`stable` 14 fois et `baisse` 2 fois (R² 0,50 et 0,56) ; CPU `stable` 16 fois
(R² ≤ 0,09). Sans la pente haute, 30 de ces 32 verdicts auraient été
`incertain`.

**Horizon : jamais plus loin que la durée observée.** Une heure d'historique
ne permet pas de prédire la journée. Au-delà, le verdict est « stable à
l'horizon d'1 h », pas une durée extrapolée.

**Métriques couvertes :** mémoire (plancher de la heap, %) et CPU (%). Le pool
de connexions (`hikaricp_connections_active / hikaricp_connections_max`) est une
ressource légitime mais **n'a pas de seuil** dans `THRESHOLDS` — voir § 7.

**Route.** `GET /api/monitoring/forecast?window=1h`, protégée par
`require_super_admin` comme ses voisines. Séparée de `/trends` pour la même
raison que `/trends` l'est de `/health` : rythme de rafraîchissement différent.

**Vérification réelle :** comparer la pente Python à `deriv()` sur la même
fenêtre, depuis le conteneur.

### Étape 2 — L'écran : la projection sur les tuiles

- Prolonger la courbe de tendance par un **segment en pointillé** jusqu'à
  l'horizon — convention déjà réservée par `KpiCard.tsx:228`.
- Une ligne sous la valeur : « Alerte dans ~2 h 10 », « Stable sur 1 h » ou
  « Tendance incertaine ». Jamais une durée quand le verdict est `incertain` ou
  `insuffisant`.
- Requête distincte, rafraîchie au rythme de `/trends` (2 min) : une pente sur
  une heure ne change pas d'un quart de minute.
- **Deux pièges relevés à l'étape 1.** (1) Pour la mémoire, la droite décrit le
  *plancher* de la heap, pas la heap brute que la courbe trace : le pointillé
  partira sous le dernier point de la courbe. Le dire (`series` dans la
  réponse), ou le faire partir de la valeur de la droite. (2) `current` et
  `at_horizon` sont des valeurs *de la droite* : sur un CPU à 0,1 % qui
  décroît, elles descendent sous 0 (−0,30 % mesuré). Borner le tracé à 0.

**Réalisé.** `frontend/.../monitoring/prevision.ts` met des mots sur le verdict
— aucun calcul de tendance côté écran. `Sparkline` prolonge la courbe en
pointillé, `KpiCard` pose la ligne d'échéance sous la valeur, `MonitoringPage`
interroge `/forecast` toutes les 2 min, sur la même fenêtre que `/trends`.
Décisions prises :

- **Même échelle de temps pour le passé et la projection.** Une heure
  d'historique et une heure de projection prennent chacune la moitié de la
  vignette : comprimer la projection dans un coin la rendrait plus raide
  qu'elle n'est, et c'est sa pente qu'on lit. L'échelle verticale inclut
  l'arrivée du pointillé, pour qu'une hausse ne sorte pas du cadre.
- **Le pointillé reporte une variation, pas une valeur.** Il part du dernier
  point tracé et monte de `at_horizon − current` : pas de saut au raccord entre
  heap brute et plancher, et la pente — ce qu'on prédit — reste exacte. Arrivée
  bornée à 0 (piège 2).
- **Pas de pointillé sous R² 0,5, même quand le verdict est `stable`.** Changé
  côté Python : `at_horizon` n'est rendu que si la droite explique la série, et
  sa présence est ce qui autorise le tracé. Sans cela, le `stable` obtenu par la
  pente haute aurait dessiné la pente d'une droite refusée. Un test le fixe.
- **La couleur seulement quand un seuil est annoncé.** Ligne et pointillé en
  ambre pour « Alerte dans ~45 min », en rouge pour « Incident dans ~40 min » ;
  « Stable », « En baisse », « Tendance incertaine » restent en gris, comme
  tout ce qui va bien sur cet écran. Une icône de sens accompagne toujours le
  mot.
- **Durées arrondies à 5 min au-delà d'une heure**, avec un tilde, et des
  espaces insécables pour qu'une tuile étroite ne coupe pas « 47 | min ».
- **L'infobulle de la ligne dit ce qui a été régressé** (« le plancher de la
  heap… », nombre de points, R²), pour qui compare la prévision au chiffre
  au-dessus.

Libellés selon le verdict : `hausse` → « Alerte dans ~X [· incident ~Y] »,
« Incident dans ~Y » si l'alerte est déjà franchie ; `stable` → « Stable sur
1 h » ; `baisse` → « En baisse sur 1 h » ; `incertain` → « Tendance
incertaine » ; `insuffisant` → « Historique insuffisant pour prévoir ».

Vérifié sur huit scénarios rendus en clair et en sombre (Chromium headless, à
1280 et 1024 px) ; `tsc -b` sans erreur. Pas de test automatisé côté frontend :
le projet n'a pas de lanceur de tests.

### Étape 3 — L'assistant : « quand la mémoire va-t-elle saturer ? »

- Outil `get_resource_forecast` dans `app/tools/definitions.py`, handler dans
  `handlers.py`, entrée dans `as_registry()`.
- Même règle que partout dans cet assistant : **le verdict est calculé en
  Python**, le modèle le raconte. Un modèle 3B ne sait ni ajuster une droite ni
  juger un R² ; il sait reformuler « memory floor rising, warning in ~130 min,
  confidence R² 0.82 ».
- Une ligne dans `SYSTEM_PROMPT` (`app/services/assistant.py`) pour orienter
  les questions « quand », « à ce rythme », « va-t-on saturer » vers cet outil
  plutôt que vers `get_metric_history`.

**Réalisé.** `get_resource_forecast` ne prend **aucun paramètre** : mémoire et
CPU tiennent en deux lignes, la fenêtre est fixée à 1 h, et un petit modèle
choisit d'autant plus mal qu'il a d'options. Chaque verdict devient une phrase
anglaise déjà interprétée (`_describe_forecast`, `handlers.py`), verdict en
majuscules en tête. Les descriptions de `get_memory_usage` et
`get_metric_history` renvoient les prévisions vers le nouvel outil. Un test
vérifie désormais que chaque outil déclaré a son handler, et inversement.

**Essai réel avec `qwen2.5:7b` (10/09/2026).** Métriques simulées par
scénario (hausse, démo, incertain, insuffisant), vrai modèle via Ollama,
7 questions dont 2 témoins, rejouées six fois au fil des corrections.
**Routage : 42/42** — les
questions « quand » vont à la prévision, « comment a évolué » à
`get_metric_history`, « combien en ce moment » à `get_memory_usage`. La
narration, elle, a exigé quatre corrections, chacune fixée par un test :

| Défaut observé | Correctif |
|---|---|
| « il faudrait au moins 30 mesures » — nombre inventé (`insuffisant`) | la sortie dit ce qui manque : « at least 30 minutes of history » (tiré de `COUVERTURE_MIN`) |
| « pas de risque immédiat » sans aucun historique | « This does NOT mean there is no risk » + « ne rassure pas » dans le prompt |
| « pourrait être atteint à tout moment » (`incertain`) | « not possible to say whether or when » au lieu de « within reach » |
| « la mémoire va saturer dans 45 min » pour le seuil d'**alerte** | « (an alert, not yet saturation) » |
| « stable **dans les prochaines heures** » sur une prévision d'une heure | voir ci-dessous |

Le dernier a résisté à tout : interdit dans la sortie de l'outil, puis dans le
prompt système en français avec l'expression exacte — encore 5 réponses sur 14.
**Le code tranche** : quand `get_resource_forecast` a été consulté,
`borner_horizon` (`assistant.py`) ramène « dans / pour / au cours des
prochaines heures », « les heures à venir » à « dans l'heure qui vient ». Le
modèle raconte ; il n'élargit pas l'horizon. Les autres réponses du modèle ne
sont pas touchées.

**À savoir pour la soutenance.** L'échéance mémoire porte sur le *plancher* de
la heap, pas sur la heap brute de la tuile. Sur une fuite, la heap brute
touche 75 % à chaque pic *avant* que le plancher n'y arrive : la tuile peut
passer en alerte avant l'heure annoncée. Ce n'est pas une contradiction — le
plancher à 75 % signifie que le ramasse-miettes ne libère plus assez, ce qu'un
pic seul ne dit pas — mais la question viendra. Le libellé vu par le modèle le
précise (« heap remaining in use after garbage collection »).

### Étape 4 — Le panneau : la prévision en grand, à côté de l'assistant

Ajoutée après coup, d'après une maquette (`docs/images/`) : un panneau
« Prévision de la saturation des ressources » posé à côté de l'assistant
technique — jauge, tendance, échéance, graphique, détails, recommandation.

**Réalisé.** `PrevisionPanel.tsx` lit la même réponse `/forecast` que les
tuiles ; aucun calcul de tendance côté écran. Côté Python, `ResourceForecast`
porte désormais `history` : les couples `(horodatage, valeur)` mêmes qui ont
servi à la régression (2 tests). Décisions prises :

- **Un vrai axe du temps, sur toute la fenêtre.** L'axe va toujours de
  « maintenant − 1 h » à « maintenant + horizon », même quand l'historique est
  plus court : après un redémarrage, la gauche reste vide, et le trou d'une
  série interrompue est coupé, pas relié.
- **La droite part de sa propre valeur, pas du dernier point.** À l'inverse du
  pointillé des tuiles (§ étape 2), qui reporte une variation : ici les seuils
  sont tracés, et c'est la droite elle-même qui doit les croiser à l'heure que
  le texte annonce. Un point marque ce croisement.
- **Seuils en traits pleins**, alors que la maquette les dessinait en
  pointillé : sur tout l'écran, le pointillé est réservé à la projection.
- **Pas de ligne « Réseau »** dans les détails, que la maquette montrait :
  aucune métrique réseau n'est collectée. Mémoire et CPU seulement — les deux
  ressources prévues.
- **La pente ne se cite que sous R² ≥ 0,5** (présence de `at_horizon`) ; sinon
  le verdict en mots. Sans projection, la légende dit pourquoi.
- **La recommandation est déduite des verdicts** (`recommandation`,
  `prevision.ts`), jamais rédigée par le modèle : la ressource la plus pressante
  décide, et `incertain` / `insuffisant` ne rassurent pas.
- **La ressource tracée** est la plus pressante tant qu'on n'a rien choisi ; un
  clic sur une ligne des détails la fixe.

Vérifié sur quatre scénarios (hausse, démo, incident, historique insuffisant),
en clair et en sombre, à 610 et 1300 px (Chromium headless) ; `tsc -b` sans
erreur, 264 tests Python verts.

---

## 5. La règle à ne pas casser

**Ne jamais annoncer une échéance que les données ne soutiennent pas.** Une
prévision fausse est pire que pas de prévision : elle fait intervenir pour rien,
puis ignorer la suivante. D'où le R² obligatoire, l'horizon borné par la durée
observée, et un verdict `incertain` affiché comme tel — jamais une ligne plate
ni une durée par défaut.

---

## 6. Fichiers concernés

| Fichier | Étape | Changement |
|---|---|---|
| `ai-assistant/app/clients/prometheus.py` | 1 | variante horodatée de `query_range` |
| `ai-assistant/app/services/prevision.py` | 1 | **nouveau** — régression et verdict |
| `ai-assistant/app/services/metrics.py` | 1 | expressions régressées (plancher heap), `get_forecast` |
| `ai-assistant/app/models.py` | 1 | modèle de réponse de la prévision |
| `ai-assistant/app/main.py` | 1 | route `/api/monitoring/forecast` |
| `ai-assistant/tests/test_prevision.py` | 1 | **nouveau** |
| `frontend/src/api/aiAssistant.api.ts` | 2 | `getForecast` et son type |
| `frontend/src/features/superadmin/monitoring/Sparkline.tsx` | 2 | segment projeté en pointillé |
| `frontend/src/features/superadmin/monitoring/KpiCard.tsx` | 2 | ligne d'échéance |
| `frontend/src/features/superadmin/monitoring/MonitoringPage.tsx` | 2 | requête de prévision |
| `ai-assistant/app/tools/definitions.py`, `handlers.py` | 3 | outil `get_resource_forecast` |
| `ai-assistant/app/services/assistant.py` | 3 | orientation dans le prompt système |
| `ai-assistant/app/models.py`, `metrics.py` | 4 | `history` : la série régressée, horodatée |
| `frontend/src/features/superadmin/monitoring/PrevisionPanel.tsx` | 4 | **nouveau** — le panneau |
| `frontend/src/features/superadmin/monitoring/prevision.ts` | 4 | `prochainSeuil`, `recommandation` |

---

## 7. Ce que ce plan ne fera pas

- **Pas de modèle plus riche** (saisonnalité, ARIMA, Prophet). La charge d'un
  établissement a un cycle journalier, qu'une droite ne capte pas ; sur une
  fenêtre d'une heure, la droite reste la bonne approximation, et elle
  s'explique en une formule.
- **Pas de prévision des métriques non-ressources** : latence et taux d'erreur
  ne se consomment pas, et ne varient pas linéairement.
- **Pas d'alerte envoyée** (mail, notification). La prévision s'affiche ; la
  brancher sur Alertmanager serait un chantier à part.
- **Décision à prendre avant d'inclure le pool de connexions :** son seuil
  d'occupation (par exemple alerte à 80 %, incident à 95 % de
  `hikaricp_connections_max`). C'est un choix d'exploitation, pas de code.
