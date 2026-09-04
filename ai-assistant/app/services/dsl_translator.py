"""
Traduction d'une demande en langage naturel vers une règle DSL validée.

Le flux, dans l'ordre, et pourquoi il est dans cet ordre :

    phrase utilisateur
      → recherche dans la CIRCULAIRE MINISTÉRIELLE (top-3 articles)
      → prompt = CATALOGUE RÉEL du backend + articles retrouvés
      → génération JSON contrainte (Ollama, format=json, température 0)
      → validation + analyse d'impact PAR LE BACKEND
      → [si refus] une tentative de correction, l'erreur du backend en entrée
      → proposition + résumé + conflits + ARTICLES CITÉS
      → *** aucune écriture *** — la confirmation est une action séparée

Le modèle n'a ici qu'un seul rôle : passer d'une phrase à une structure. Il ne
décide pas si la règle est valide (le validateur Java le fait), ni si elle est
réalisable (l'analyse de conflit le fait), ni si elle doit être enregistrée
(l'utilisateur le fait). Ce découpage n'est pas de la prudence de principe :
c'est ce qui rend le résultat vérifiable. Une règle produite par un modèle de
7 milliards de paramètres tournant sur CPU sera parfois fausse ; ce qui compte,
c'est qu'une règle fausse soit refusée plutôt qu'appliquée.

**L'ancrage sur la circulaire ajoute la dernière pièce : la vérifiabilité de
la SOURCE.** Le validateur dit qu'une règle est bien formée, l'analyse d'impact
dit ce qu'elle change — ni l'un ni l'autre ne dit d'où elle sort. Un directeur
qui active « six heures par jour au maximum » a le droit de savoir que c'est
l'article II.2 de la circulaire n°66/2024, page 2, qui l'écrit, et de le lire.

Le risque de cet ancrage est réel, il a été mesuré, et il s'est produit :
injecter des articles dans le prompt pousse un modèle de 7B à traduire
*l'article* au lieu de *la phrase de l'utilisateur*.

**La mesure.** Cinq phrases traduites avec et sans ancrage, trois passages
chacune (le modèle est reproductible à température 0, à une variante près sur
quinze). Deux régressions stables sont apparues :

  - « de préférence des maths le matin » perdait sa condition
    `period = MORNING`. L'article § III.2.a nuance en « trois quarts le matin,
    un quart l'après-midi » — le modèle en concluait qu'il ne fallait pas
    fixer la demi-journée, et récompensait donc TOUTES les séances de maths ;
  - « les travaux pratiques en laboratoire » passait de
    `room.type EQUALS LABPHYSIQUE` à `room.type NOT_EQUALS SALLE_COURS`, en
    mélangeant les deux moitiés du § III.4.

Le premier garde-fou interdisait d'AJOUTER une condition absente de la phrase.
Il ne disait rien du cas inverse — en RETIRER une — qui est celui qui s'est
produit. Le prompt interdit désormais les deux et porte le contre-exemple
travaillé du cas « maths le matin ».

**Vérification sur des phrases tenues à l'écart**, pour ne pas mesurer le
correctif sur son propre exemple : « de l'arabe le matin » et « éviter
l'éducation physique le samedi matin ». Les deux régressions ont disparu, et
sur ces deux phrases l'ancrage fait désormais MIEUX que l'absence d'ancrage —
sans lui, le modèle ne gardait que la matière et laissait tomber le jour et la
demi-journée. Échantillon de quatre phrases tenues à l'écart : deux améliorées,
deux inchangées, aucune dégradée.

Trois garde-fous, donc :

  1. le prompt dit que les articles servent à choisir la portée, la sévérité et
     les seuils — jamais à changer la règle demandée — et interdit aussi bien
     d'ajouter que d'omettre une condition, contre-exemple à l'appui ;
  2. seuls les articles qui se traduisent réellement en contrainte de solveur
     (ceux qui portent une `portee`) sont injectés — les tableaux de volumes
     horaires et les articles d'affectation sont écartés ;
  3. la citation rendue à l'utilisateur distingue l'article dont la portée
     CONCORDE avec la règle produite de ceux qui ne font que voisiner. Dire
     « cette règle correspond au § II.2 » quand la recherche n'a fait que
     remonter un article proche serait exactement le genre de citation
     décorative qui décrédibilise un outil de traçabilité.

**Ce que la citation vaut, et ce qu'elle ne vaut pas.** Sur « les profs ne
doivent pas dépasser 6 heures par jour », le § II.2 (le bon : côté enseignant)
sort désormais dans le top-3, la table de synonymes du canal lexical ayant
rapproché « profs » de « enseignant ». Il n'en sort pas toujours PREMIER : le
§ I.2 porte la même règle côté élève, avec les mêmes chiffres, et un seul mot
les distingue. C'est exactement pour ce cas que `concordance` existe — I.2 est
marqué voisin, II.2 concordant, et l'interface cite le bon.

Une reformulation suffit d'ailleurs à faire basculer le classement : « un
enseignant ne doit pas faire plus de 6 heures par jour » place le § II.2 en
tête. La recherche reste sensible à la formulation, et il faut le dire plutôt
que de laisser croire à une correspondance article ↔ règle qui serait exacte.
Ce qui est garanti n'est pas que le bon article sorte premier, c'est qu'aucun
article ne soit cité comme correspondant s'il ne l'est pas.
"""

import json
import logging
import time
from dataclasses import dataclass, field
from typing import Any

from app.clients.backend import BackendClient, BackendError
from app.clients.ollama import OllamaClient
from app.services.consigne_retrieval import ArticleConsigne, ConsigneRetriever

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Prompt
# ─────────────────────────────────────────────────────────────────────────────

SYSTEM_PROMPT = """Tu convertis une règle d'emploi du temps écrite en français en un objet JSON.

Tu réponds UNIQUEMENT par l'objet JSON, sans texte autour, sans balise de code.

Structure attendue :
{
  "scope": "<portée>",
  "logic": "AND" | "OR",
  "conditions": [ {"field": "<champ>", "operator": "<opérateur>", "value": "<valeur>"} ],
  "aggregate": {"metric": "TOTAL_HOURS", "operator": "GREATER_THAN", "value": <nombre>},
  "action": "PENALIZE" | "REWARD",
  "severity": "HARD" | "MEDIUM" | "SOFT",
  "weight": <entier entre 1 et 1000>
}

RÈGLES ABSOLUES :
- N'utilise QUE les champs, opérateurs et portées listés ci-dessous. N'en invente aucun.
- Pour une règle qui vise des séances précises ("pas de maths le vendredi après 15h") :
  scope = "LESSON", pas de bloc "aggregate".
- Pour une règle qui plafonne un cumul ("pas plus de 3 heures par jour") :
  scope = "TEACHER_DAY" (ou CLASS_DAY / ROOM_DAY / TEACHER_WEEK / CLASS_WEEK),
  "conditions" peut être vide, et le bloc "aggregate" est OBLIGATOIRE.
- "action" et "severity" sont deux décisions INDÉPENDANTES : "action" dit le SENS
  de la règle, "severity" dit sa FORCE. Décide-les séparément.
- "action" — la phrase décrit-elle une situation à ÉVITER ou à FAVORISER ?
  · à éviter ("pas de", "ne doit pas", "interdit", "jamais", "éviter") → PENALIZE
  · à favoriser ("privilégier", "de préférence en début de journée", "regrouper") → REWARD
  Une phrase NÉGATIVE reste PENALIZE même quand elle est formulée en préférence :
  "de préférence pas de maths en dernière heure" → PENALIZE, jamais REWARD.
  Décris toujours la situation que la phrase cite ; ne l'inverse jamais pour
  transformer une interdiction en récompense.
- "severity" — la règle est-elle impérative ou seulement souhaitable ?
  · "ne doit pas", "interdit", "jamais", "obligatoire" → HARD, weight 100
  · "de préférence", "si possible", "idéalement", "éviter" → SOFT, weight entre 1 et 50
- REWARD n'est jamais accepté en HARD : une récompense se pose en MEDIUM ou SOFT.
- Combinaisons attendues :
  "pas de maths le vendredi après 15h"           → PENALIZE + HARD
  "de préférence pas de maths en dernière heure" → PENALIZE + SOFT
  "de préférence des maths le matin"             → REWARD + SOFT
- Les heures s'écrivent "HH:mm". Les jours en anglais majuscule : MONDAY … SATURDAY.
- Bornes horaires. "startTime" est l'heure de DÉBUT de la séance, "endTime" son
  heure de FIN. Procède en deux temps, sans jamais sauter le second :
  1. repère la borne citée ("après 16h" → 16:00) ;
  2. écris la condition qui SÉLECTIONNE les séances fautives — celles qui tombent
     du côté interdit de la borne, c'est-à-dire le côté que la phrase désigne.
  Applique cette table telle quelle :
  · "pas de cours APRÈS 16h"            → startTime GREATER_THAN_OR_EQUAL "16:00"
  · "pas de cours AVANT 9h"             → startTime LESS_THAN "09:00"
  · "ne doit pas SE TERMINER après 17h" → endTime GREATER_THAN "17:00"
  · "ne doit pas COMMENCER avant 8h"    → startTime LESS_THAN "08:00"
  · "pas de cours ENTRE 12h et 14h"     → startTime BETWEEN, "values": ["12:00", "14:00"]
  Le sens de l'opérateur suit la PRÉPOSITION, jamais la négation de la phrase :
  "après T" → opérateur supérieur (GREATER_THAN / GREATER_THAN_OR_EQUAL),
  "avant T" → opérateur inférieur (LESS_THAN). La négation ("pas", "jamais",
  "ne doit pas") est DÉJÀ portée par action = PENALIZE : ne l'applique pas une
  seconde fois en retournant l'opérateur, sinon la règle pénalise les séances
  conformes et laisse passer les autres.
  Ainsi "aucune séance ne doit se terminer après 17h" donne
  endTime GREATER_THAN "17:00" — et jamais endTime LESS_THAN "17:00".
  Avant de répondre, relis ta condition : « une séance qui la satisfait est-elle
  bien celle que la phrase veut interdire ? »
- BETWEEN et IN portent leurs bornes dans "values" (liste), jamais dans "value" :
  {"field": "startTime", "operator": "BETWEEN", "values": ["12:00", "14:00"]}.
- Ne répète pas un même champ dans plusieurs conditions reliées par AND : elles
  s'annuleraient. Pour plusieurs valeurs d'un même champ, utilise IN.
- "en dernière heure de la journée" et "en première heure" se traduisent par
  "slotOrder", pas par une heure fixe.
- Omets "aggregate" quand scope vaut LESSON.
"""


# Découpé en deux moitiés plutôt qu'assemblé par str.format : le prompt contient
# des exemples JSON, donc des accolades, et un format() y verrait des champs à
# substituer. Une concaténation ne peut pas se casser en ajoutant un exemple.
CONSIGNE_PROMPT_ENTETE = """
ARTICLES DE LA CIRCULAIRE MINISTÉRIELLE EN RAPPORT AVEC CETTE DEMANDE

Ces extraits de la circulaire n°66 du 04/09/2024 (ministère de l'Éducation,
Tunisie) encadrent le TYPE de règle demandé. Ils t'aident à choisir la portée,
la sévérité et les seuils corrects.

"""

CONSIGNE_PROMPT_PIED = """
RÈGLE ABSOLUE SUR CES ARTICLES : tu traduis la phrase de l'utilisateur, et elle
seule. Ne produis JAMAIS la règle d'un article à la place de la sienne.

- N'AJOUTE jamais une condition, un seuil ou un jour qui ne figure pas dans sa
  phrase.
- N'OMETS jamais un élément qui figure dans sa phrase. Matière, jour,
  demi-journée, heure, salle, niveau : chacun doit se retrouver dans les
  conditions. Un article qui nuance la règle ("trois quarts le matin", "sauf
  nécessité") ne t'autorise PAS à retirer la condition correspondante — la
  nuance appartient au ministère, la règle appartient à l'utilisateur.
- Si un article dit autre chose que l'utilisateur, c'est l'utilisateur qui
  décide : il connaît son établissement, et une dérogation est sa responsabilité.

Ces articles ne servent qu'à te dire quelle portée et quelle sévérité sont
d'usage pour ce genre de règle.

L'ERREUR À NE PAS COMMETTRE, sur un cas réel :
  Utilisateur : « de préférence des maths le matin »
  Article montré : § III.2.a — « trois quarts de l'horaire des matières
  fondamentales sont programmés sur la période du matin, le quart restant
  l'après-midi »
  MAUVAIS : "conditions": [{"field": "subject.code", "operator": "EQUALS", "value": "MATH"}]
            La demi-journée a disparu parce que l'article la nuance : la règle
            récompense alors TOUTES les séances de maths, y compris celles de
            l'après-midi. Ce n'est pas ce que l'utilisateur a demandé.
  BON     : "conditions": [{"field": "subject.code", "operator": "EQUALS", "value": "MATH"},
                           {"field": "period", "operator": "EQUALS", "value": "MORNING"}]
"""


@dataclass
class ConstraintProposal:
    """
    Ce que l'assistant propose, et rien de plus : une règle candidate, son
    verdict, et de quoi décider. L'enregistrement est un autre appel.
    """

    dsl: dict | None
    valid: bool
    summary: str | None = None
    verdict: str | None = None
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    conflicts: list[dict] = field(default_factory=list)
    feasible: bool = True
    matched_lessons: int | None = None
    total_lessons: int | None = None
    examples: list[str] = field(default_factory=list)
    attempts: int = 0
    duration_ms: int = 0
    message: str = ""

    # Articles de la circulaire retrouvés pour cette demande. Liste vide quand
    # aucun ne dépasse le plancher — et c'est une information en soi, pas un
    # échec : la plupart des règles d'un établissement sont des règles maison,
    # que le ministère n'encadre pas. L'interface peut alors le dire, au lieu
    # de laisser croire que toute règle a un fondement réglementaire.
    sources: list[dict] = field(default_factory=list)


class DslTranslator:
    def __init__(
        self,
        ollama: OllamaClient,
        backend: BackendClient,
        schema_ttl_seconds: float = 300.0,
        temperature: float = 0.0,
        num_predict: int = 700,
        max_repair_attempts: int = 1,
        consigne: ConsigneRetriever | None = None,
    ):
        self._ollama = ollama
        self._backend = backend
        # Optionnel, et volontairement : la traduction doit rester possible sans
        # le corpus. Un service déployé sans le fichier de circulaire perd la
        # citation, pas la fonction.
        self._consigne = consigne
        self._schema_ttl = schema_ttl_seconds
        self._options = {"temperature": temperature, "num_predict": num_predict}
        self._max_repair_attempts = max_repair_attempts

        # Cache du catalogue, par jeton d'appelant ? Non : le catalogue est le
        # même pour tous les établissements (ce sont les RÈGLES qui sont propres
        # à chacun, pas les champs disponibles). Un cache global est donc correct
        # et ne fuit aucune donnée d'un tenant vers un autre.
        self._schema_cache: dict | None = None
        self._schema_cached_at: float = 0.0

    # ── entrée principale ─────────────────────────────────────────────────────

    async def translate(
        self,
        request_text: str,
        token: str,
        school_year_id: int | None = None,
        profile_id: int | None = None,
    ) -> ConstraintProposal:
        started = time.monotonic()

        try:
            schema = await self._get_schema(token)
        except BackendError as exc:
            return ConstraintProposal(
                dsl=None, valid=False, errors=[exc.detail],
                message="Impossible de récupérer le catalogue de contraintes : " + exc.detail,
                duration_ms=_elapsed(started),
            )

        # La recherche a lieu AVANT la génération : les articles retrouvés
        # entrent dans le prompt, ils ne sont pas collés après coup à une règle
        # déjà écrite. Une citation ajoutée en fin de course décrirait ce que le
        # modèle aurait pu lire, pas ce qu'il a lu.
        articles = await self._articles_pertinents(request_text)

        system = SYSTEM_PROMPT + "\n" + _describe_schema(schema)
        if articles:
            system += (
                "\n" + CONSIGNE_PROMPT_ENTETE
                + _describe_articles(articles)
                + "\n" + CONSIGNE_PROMPT_PIED
            )

        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": request_text},
        ]

        last_errors: list[str] = []
        # Une passe de génération, puis au plus N passes de correction. Le
        # correctif est guidé par le message du VALIDATEUR, pas par une critique
        # que le modèle ferait de lui-même : c'est ce qui fait converger la boucle.
        for attempt in range(1 + self._max_repair_attempts):
            dsl, parse_error = await self._generate(messages)

            if dsl is None:
                last_errors = [parse_error or "Le modèle n'a pas produit de JSON exploitable."]
                messages.append({"role": "user", "content": _repair_instruction(last_errors)})
                continue

            try:
                analysis = await self._backend.analyze_constraint(
                    token, dsl, school_year_id, profile_id
                )
            except BackendError as exc:
                return ConstraintProposal(
                    dsl=dsl, valid=False, errors=[exc.detail],
                    message="La règle n'a pas pu être vérifiée : " + exc.detail,
                    attempts=attempt + 1, duration_ms=_elapsed(started),
                    sources=_sources(articles, dsl),
                )

            if analysis.get("valid"):
                return _proposal_from_analysis(
                    dsl, analysis, attempt + 1, _elapsed(started), articles
                )

            last_errors = analysis.get("errors") or ["Règle refusée par le backend."]
            logger.info("DSL refusé (tentative %s) : %s", attempt + 1, last_errors)
            messages.append({"role": "assistant", "content": json.dumps(dsl, ensure_ascii=False)})
            messages.append({"role": "user", "content": _repair_instruction(last_errors)})

        return ConstraintProposal(
            dsl=None,
            valid=False,
            errors=last_errors,
            feasible=True,
            attempts=1 + self._max_repair_attempts,
            duration_ms=_elapsed(started),
            # Même sans règle produite, les articles restent utiles : ils disent
            # à l'utilisateur ce que la circulaire prévoit sur le sujet qu'il
            # vient d'aborder, ce qui vaut mieux qu'un échec sec.
            sources=_sources(articles, None),
            message=(
                "Je n'ai pas réussi à traduire cette demande en règle exploitable. "
                "Reformulez-la plus simplement, par exemple : « les classes de terminale "
                "ne doivent pas avoir de mathématiques après 15h le vendredi »."
            ),
        )

    # ── ancrage sur la circulaire ─────────────────────────────────────────────

    async def _articles_pertinents(self, request_text: str) -> list[ArticleConsigne]:
        """
        Les articles de la circulaire qui encadrent ce type de règle.

        Deux filtres, et le second est le plus important :

          - le **plancher** du RAG écarte les demandes que la circulaire ne
            couvre pas. Il y en a beaucoup, et c'est normal : « pas de cours
            pour M. Ben Salah le mercredi » est une contrainte de personne, pas
            une règle du ministère ;
          - seuls les articles **traduisibles en contrainte** (`portee` non
            nulle) sont retenus. Les tableaux de volumes horaires et les
            articles d'affectation sont écartés : ils ne peuvent suggérer ni
            portée ni sévérité, et leurs 1 300 à 2 300 caractères de tableau
            noieraient le catalogue DSL dans le contexte d'un modèle de 7B.

        Un échec de recherche ne fait jamais échouer la traduction : sans
        corpus, on perd la citation, pas la règle.
        """
        if self._consigne is None:
            return []

        try:
            resultats = await self._consigne.rechercher(request_text)
        except Exception as exc:  # noqa: BLE001 — dégradation, pas panne
            logger.warning("Recherche consigne indisponible : %s", exc)
            return []

        return [article for article, _ in resultats if article.portee]

    # ── génération ────────────────────────────────────────────────────────────

    async def _generate(self, messages: list[dict]) -> tuple[dict | None, str | None]:
        try:
            message = await self._ollama.chat(
                messages, tools=None, json_mode=True, options=self._options
            )
        except Exception as exc:
            logger.exception("Échec de l'appel à Ollama")
            return None, f"Le modèle est indisponible ({exc})."

        content = (message.get("content") or "").strip()
        if not content:
            return None, "Réponse vide du modèle."

        try:
            parsed = json.loads(content)
        except json.JSONDecodeError as exc:
            logger.info("JSON invalide du modèle : %r", content[:400])
            return None, f"JSON invalide : {exc}"

        if not isinstance(parsed, dict):
            return None, "Le modèle a renvoyé autre chose qu'un objet JSON."

        return _clean(parsed), None

    # ── catalogue ─────────────────────────────────────────────────────────────

    async def _get_schema(self, token: str) -> dict:
        now = time.monotonic()
        if self._schema_cache is not None and (now - self._schema_cached_at) < self._schema_ttl:
            return self._schema_cache

        schema = await self._backend.get_dsl_schema(token)
        self._schema_cache = schema
        self._schema_cached_at = now
        return schema


# ─────────────────────────────────────────────────────────────────────────────
# Mise en forme du catalogue pour le prompt
# ─────────────────────────────────────────────────────────────────────────────

def _describe_schema(schema: dict) -> str:
    """
    Rend le catalogue lisible par le modèle, en restant compact.

    Un prompt de 3 000 tokens noie un modèle de 7B : chaque champ tient donc sur
    une ligne, avec son type, ses valeurs admises et un exemple. Les valeurs
    fermées sont listées explicitement — c'est précisément là que les modèles
    inventent (« MAGISTRAL » pour un type de séance, « TERMINAL » pour un niveau).
    """
    lines: list[str] = ["", "CHAMPS DISPONIBLES :"]
    for f in schema.get("fields", []):
        line = f"- {f['name']} ({f['type']}) : {f.get('label', '')}"
        allowed = f.get("allowedValues") or []
        if allowed:
            line += " — valeurs : " + ", ".join(allowed)
        elif f.get("example"):
            line += f" — ex. {f['example']}"
        lines.append(line)

    lines.append("")
    lines.append("OPÉRATEURS : " + ", ".join(o["name"] for o in schema.get("operators", [])))
    lines.append("PORTÉES : " + ", ".join(s["name"] for s in schema.get("scopes", [])))
    lines.append("SÉVÉRITÉS : " + ", ".join(s["name"] for s in schema.get("severities", [])))
    lines.append(
        "MÉTRIQUES DE CUMUL : "
        + ", ".join(m["name"] for m in schema.get("aggregateMetrics", []))
    )

    example = schema.get("example")
    if example:
        lines.append("")
        lines.append("EXEMPLE COMPLET :")
        lines.append(json.dumps(example, ensure_ascii=False))

    return "\n".join(lines)


def _describe_articles(articles: list[ArticleConsigne]) -> str:
    """
    Rend les articles au modèle : numéro, page, portée et sévérité d'usage, texte.

    La portée et la sévérité pré-mappées sont données EXPLICITEMENT plutôt que
    laissées à déduire du texte. C'est tout l'intérêt d'avoir annoté le corpus :
    un modèle de 7B lit « six heures par jour au maximum » et hésite entre
    CLASS_DAY et TEACHER_DAY ; l'annotation tranche, et elle a été relue par un
    humain, ce que la déduction du modèle ne sera jamais.

    Le texte n'est pas tronqué : les articles font 250 à 450 caractères depuis
    que le corpus sépare le texte normatif de notre analyse. Trois articles
    ajoutent donc au plus ~1 200 caractères au prompt — assez peu pour ne pas
    noyer le catalogue DSL, ce qui n'aurait pas été vrai en injectant les chunks
    entiers.
    """
    blocs = []
    for article in articles:
        entete = f"§ {article.id} (p. {article.page})"
        if article.portee:
            entete += f" — portée d'usage : {article.portee}"
        if article.severite:
            entete += f", sévérité d'usage : {article.severite}"
        blocs.append(f"{entete}\n  {article.texte}")
    return "\n\n".join(blocs)


def _sources(articles: list[ArticleConsigne], dsl: dict | None) -> list[dict]:
    """
    Les citations rendues à l'interface, avec leur degré de correspondance.

    `concordance` est vrai quand la portée pré-mappée de l'article est celle que
    la règle produite utilise réellement. La distinction n'est pas un détail
    d'affichage : elle sépare « cette règle correspond à l'article II.2 » de
    « voici les articles voisins ». Afficher la première formule pour un article
    que la recherche a seulement remonté serait une citation décorative — et une
    citation décorative dans un outil de traçabilité est pire que pas de citation
    du tout, parce qu'elle a l'apparence d'une preuve.

    `dsl` vaut None quand aucune règle n'a pu être produite : rien ne concorde
    alors avec rien, et les articles sont rendus pour information seule.
    """
    scope = (dsl or {}).get("scope")
    return [
        {
            "id": article.id,
            "page": article.page,
            "citation": article.citation,
            "extrait": article.texte,
            "extrait_ar": article.texte_ar,
            "portee": article.portee,
            "severite": article.severite,
            "concordance": bool(scope) and article.portee == scope,
        }
        for article in articles
    ]


def _repair_instruction(errors: list[str]) -> str:
    return (
        "Ta réponse a été refusée par le validateur pour la ou les raisons suivantes :\n"
        + "\n".join(f"- {e}" for e in errors)
        + "\nCorrige uniquement ce qui est signalé et renvoie le JSON complet, "
          "sans texte autour."
    )


def _clean(parsed: dict) -> dict:
    """
    Normalise ce que le modèle a produit avant de l'envoyer au backend.

    Trois écarts observés, tous bénins pris un par un et tous bloquants pour un
    parseur strict :
      - un bloc "aggregate" laissé à null sur une portée LESSON ;
      - des valeurs numériques là où le contrat attend des chaînes ;
      - des clés à null que le modèle ajoute « pour faire complet ».

    On corrige ces trois-là, et rien d'autre : réécrire davantage reviendrait à
    valider côté Python, c'est-à-dire à maintenir un second validateur qui
    finirait par contredire celui du backend.
    """
    cleaned = {k: v for k, v in parsed.items() if v is not None}

    if cleaned.get("scope") == "LESSON":
        cleaned.pop("aggregate", None)

    conditions = cleaned.get("conditions")
    if isinstance(conditions, list):
        cleaned["conditions"] = [_clean_condition(c) for c in conditions if isinstance(c, dict)]

    if isinstance(cleaned.get("weight"), (int, float)):
        cleaned["weight"] = int(cleaned["weight"])

    return cleaned


def _clean_condition(condition: dict) -> dict:
    cleaned: dict[str, Any] = {k: v for k, v in condition.items() if v is not None}
    if "value" in cleaned and not isinstance(cleaned["value"], str):
        cleaned["value"] = _to_text(cleaned["value"])
    if isinstance(cleaned.get("values"), list):
        cleaned["values"] = [_to_text(v) for v in cleaned["values"]]
    return cleaned


def _to_text(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def _proposal_from_analysis(
    dsl: dict,
    analysis: dict,
    attempts: int,
    duration_ms: int,
    articles: list[ArticleConsigne],
) -> ConstraintProposal:
    conflicts = analysis.get("conflicts") or []
    feasible = analysis.get("feasible", True)

    return ConstraintProposal(
        dsl=dsl,
        valid=True,
        summary=analysis.get("summary"),
        verdict=analysis.get("verdict"),
        warnings=analysis.get("warnings") or [],
        conflicts=conflicts,
        feasible=feasible,
        matched_lessons=analysis.get("matchedLessons"),
        total_lessons=analysis.get("totalLessons"),
        examples=analysis.get("examples") or [],
        attempts=attempts,
        duration_ms=duration_ms,
        # Le message montré à l'utilisateur reprend le VERDICT du backend, pas
        # une reformulation par le modèle : ce qui est affiché doit être ce qui
        # sera appliqué, au mot près.
        message=analysis.get("verdict") or analysis.get("summary") or "",
        sources=_sources(articles, dsl),
    )


def _elapsed(started: float) -> int:
    return int((time.monotonic() - started) * 1000)
