"""
Implémentations des outils Planning.

Chaque handler renvoie une CHAÎNE COURTE ET DÉJÀ INTERPRÉTÉE : le modèle relit
du texte, pas du JSON. Rendre la réponse brute du backend obligerait un modèle
de 7B à naviguer dans une structure imbriquée, exercice où il se trompe de champ
et finit par annoncer un score qui n'existe pas.

Les handlers sont liés au JETON de l'utilisateur courant, une instance par
requête. C'est ce qui rend l'isolation multi-tenant automatique : il n'existe
aucun moyen, depuis la boucle d'outils, de désigner un autre établissement — le
tenant n'est pas un paramètre, c'est une propriété du jeton.
"""

import logging

from app.clients.backend import BackendClient, BackendError

logger = logging.getLogger(__name__)

ALLOWED_LEVELS = {"HARD", "MEDIUM", "SOFT", "ALL"}

# Bornes de restitution : au-delà, on remplit la fenêtre de contexte du modèle
# avec du détail qu'il résumera de travers.
MAX_CONSTRAINTS_LISTED = 20
MAX_VIOLATIONS_LISTED = 6
MAX_EXAMPLES_PER_VIOLATION = 3


def _sanitize_level(level: str | None) -> str:
    """On ne fait jamais confiance à un argument venant du LLM."""
    if level is None:
        return "ALL"
    normalized = str(level).strip().upper()
    if normalized not in ALLOWED_LEVELS:
        logger.info("Niveau invalide du LLM : %r → repli sur ALL", level)
        return "ALL"
    return normalized


class PlanningToolHandlers:
    def __init__(
        self,
        backend: BackendClient,
        token: str,
        school_year_id: int | None = None,
        profile_id: int | None = None,
    ):
        self._backend = backend
        self._token = token
        self._school_year_id = school_year_id
        self._profile_id = profile_id

    # ── état général ──────────────────────────────────────────────────────────

    async def get_planning_overview(self) -> str:
        try:
            jobs = await self._backend.get_jobs(self._token, self._school_year_id)
        except BackendError as exc:
            return f"Timetable status unavailable: {exc.detail}"

        if not jobs:
            return "No timetable generation has been run yet for this school year."

        job = jobs[0]
        lines = [
            f"Latest generation #{job.get('idTimetableJob')}: status {job.get('status')}",
            f"Score: {job.get('scoreAchieved') or 'not available'}",
        ]
        if job.get("errorMessage"):
            lines.append(f"Error: {job['errorMessage']}")
        return "\n".join(lines)

    # ── explication des violations ────────────────────────────────────────────

    async def explain_violations(self, level: str = "ALL") -> str:
        """
        Restitue l'explication de score produite par Timefold via le backend.

        Les chiffres cités ici viennent tous des ConstraintMatch du solveur.
        L'assistant n'en calcule aucun : « le professeur Ahmed est affecté à deux
        cours à 10h » est vrai parce que le solveur l'a constaté, pas parce que le
        modèle l'a déduit d'un tableau qu'il aurait mal lu.
        """
        wanted = _sanitize_level(level)

        job_id = await self._latest_job_id()
        if job_id is None:
            return "No timetable has been generated yet, so there is nothing to explain."

        try:
            explanation = await self._backend.get_score_explanation(self._token, job_id)
        except BackendError as exc:
            return f"Score explanation unavailable: {exc.detail}"

        score = explanation.get("score") or "unknown"
        feasible = explanation.get("feasible")
        lines = [
            f"Timetable #{job_id} — score {score} "
            f"({'feasible' if feasible else 'NOT feasible: hard constraints are violated'})."
        ]

        buckets = [
            ("HARD", "hardViolations", "BLOCKING (hard)"),
            ("MEDIUM", "mediumViolations", "Important (medium)"),
            ("SOFT", "softViolations", "Preference (soft)"),
        ]
        reported = 0
        for name, key, label in buckets:
            if wanted not in (name, "ALL"):
                continue
            violations = explanation.get(key) or []
            if not violations:
                continue
            lines.append("")
            lines.append(f"{label}:")
            for violation in violations[:MAX_VIOLATIONS_LISTED]:
                reported += 1
                lines.append(
                    f"  - {violation.get('label')} — {violation.get('count')} occurrence(s), "
                    f"score {violation.get('score')}"
                )
                for example in (violation.get("examples") or [])[:MAX_EXAMPLES_PER_VIOLATION]:
                    lines.append(f"      · {example}")
                if violation.get("suggestion"):
                    lines.append(f"      → {violation['suggestion']}")

        if reported == 0:
            return (
                f"Timetable #{job_id} — score {score}. "
                f"No violation at the requested level ({wanted})."
            )
        return "\n".join(lines)

    # ── contraintes actives ───────────────────────────────────────────────────

    async def get_active_constraints(self) -> str:
        parts: list[str] = []

        try:
            profiles = await self._backend.get_profiles(self._token)
        except BackendError as exc:
            return f"Constraints unavailable: {exc.detail}"

        active = self._pick_profile(profiles)
        if active is None:
            return "No constraint profile is configured for this school yet."

        profile_id = active.get("idConstraintProfile")
        parts.append(f"Active constraint profile: {active.get('name')} (id {profile_id})")

        try:
            detail = await self._backend.get_profile(self._token, profile_id)
            settings = [s for s in (detail.get("settings") or []) if s.get("enabled")]
            parts.append(f"Catalogue constraints enabled: {len(settings)}")
            for setting in settings[:MAX_CONSTRAINTS_LISTED]:
                params = setting.get("parametersJson") or ""
                parts.append(
                    f"  - {setting.get('constraintName')} "
                    f"[{setting.get('importance')}, weight {setting.get('weight')}]"
                    + (f" {params}" if params else "")
                )
        except BackendError as exc:
            parts.append(f"  (catalogue constraints unavailable: {exc.detail})")

        try:
            custom = await self._backend.get_custom_constraints(self._token, profile_id)
            enabled = [c for c in custom if c.get("enabled")]
            parts.append(f"Custom DSL rules enabled: {len(enabled)}")
            for rule in enabled[:MAX_CONSTRAINTS_LISTED]:
                parts.append(f"  - [{rule.get('code')}] {rule.get('summary') or rule.get('name')}")
        except BackendError as exc:
            parts.append(f"  (custom rules unavailable: {exc.detail})")

        return "\n".join(parts)

    # ── suggestions ───────────────────────────────────────────────────────────

    async def suggest_constraint(self) -> str:
        try:
            result = await self._backend.get_suggestions(self._token, self._school_year_id)
        except BackendError as exc:
            return f"Suggestions unavailable: {exc.detail}"

        suggestions = result.get("suggestions") or []
        if not suggestions:
            return (
                "The latest timetable shows no recurring problem worth a new rule. "
                "Nothing to suggest."
            )

        lines = [f"Analysis of timetable #{result.get('jobId')} — {len(suggestions)} suggestion(s):"]
        for suggestion in suggestions:
            lines.append("")
            lines.append(f"- {suggestion.get('title')}")
            lines.append(f"  Observed: {suggestion.get('observation')}")
            lines.append(f"  Why: {suggestion.get('rationale')}")
            for evidence in (suggestion.get("evidence") or [])[:MAX_EXAMPLES_PER_VIOLATION]:
                lines.append(f"      · {evidence}")
        lines.append("")
        lines.append(
            "These are proposals only. Ask the user whether they want one of them, "
            "then they must confirm it in the interface before anything is saved."
        )
        return "\n".join(lines)

    # ── helpers ───────────────────────────────────────────────────────────────

    async def _latest_job_id(self) -> int | None:
        try:
            jobs = await self._backend.get_jobs(self._token, self._school_year_id)
        except BackendError:
            return None
        for job in jobs:
            # Un job annulé ou en échec n'a pas d'explication de score à donner.
            if job.get("status") in ("SOLVED", "INFEASIBLE"):
                return job.get("idTimetableJob")
        return None

    def _pick_profile(self, profiles: list) -> dict | None:
        if not profiles:
            return None
        if self._profile_id is not None:
            for profile in profiles:
                if profile.get("idConstraintProfile") == self._profile_id:
                    return profile
        for profile in profiles:
            if profile.get("active"):
                return profile
        return profiles[0]

    # Table de dispatch : associe le nom vu par le LLM à la méthode Python.
    # Aucune écriture n'y figure — c'est délibéré et c'est la garantie que le
    # modèle ne peut rien modifier, quelle que soit la façon dont on lui parle.
    def as_registry(self) -> dict:
        return {
            "get_planning_overview": self.get_planning_overview,
            "explain_violations": self.explain_violations,
            "get_active_constraints": self.get_active_constraints,
            "suggest_constraint": self.suggest_constraint,
        }
