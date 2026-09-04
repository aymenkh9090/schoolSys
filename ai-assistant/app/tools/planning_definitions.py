"""
Outils de l'assistant Planning : schémas JSON vus par le modèle.

Ce que cette liste NE contient pas est aussi important que ce qu'elle contient.
Il n'existe pas d'outil `create_constraint`, `delete_constraint` ni
`optimize_planning`. Le modèle ne peut donc pas, même en le « décidant », écrire
en base ni lancer un calcul de trente secondes : la table de dispatch ne contient
que des lectures, et un nom absent de cette table est rejeté.

La création passe par une route dédiée, appelée par l'INTERFACE après que
l'utilisateur a vu la règle exacte et cliqué sur Confirmer. C'est la différence
entre un assistant qui propose et un agent qui agit — et sur un emploi du temps
d'établissement, seul le premier est acceptable : une règle HARD ajoutée par
erreur rend le planning infaisable pour tout le monde.
"""

TOOL_DEFINITIONS = [
    {
        "type": "function",
        "function": {
            "name": "get_planning_overview",
            "description": (
                "Get the status of the latest timetable generation: score, "
                "whether it is feasible, and how many sessions were placed. "
                "Use this for general questions such as 'where do we stand', "
                "'is the timetable ready', 'is the timetable good'."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "explain_violations",
            "description": (
                "Explain WHY the latest timetable is not optimal: which "
                "constraints are violated, how many times, and which teachers, "
                "classes, rooms and time slots are involved. Use this whenever "
                "the user asks why the timetable is bad, what the problems are, "
                "what is blocking, or asks about the hard/soft score."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "level": {
                        "type": "string",
                        "enum": ["HARD", "MEDIUM", "SOFT", "ALL"],
                        "description": (
                            "Which severity level to explain. Must be exactly one of: "
                            "HARD, MEDIUM, SOFT, ALL. Use HARD for blocking problems."
                        ),
                    }
                },
                "required": ["level"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_active_constraints",
            "description": (
                "List the constraints currently enabled for this school: both "
                "the catalogue constraints of the active profile and the custom "
                "rules written in the DSL. Use this when the user asks which "
                "rules apply, what is configured, or whether a given rule exists."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "suggest_constraint",
            "description": (
                "Analyse the latest generated timetable and report which new "
                "rules would improve it, with the evidence that motivates each "
                "one. Use this when the user asks for advice, for improvements, "
                "or what could be done better. This only REPORTS suggestions — "
                "it never creates anything."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
]
