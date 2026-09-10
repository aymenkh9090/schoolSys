"""
Schémas JSON des outils : exactement ce que le modèle reçoit.

Format compatible OpenAI, adopté par Ollama. Séparé des implémentations
parce qu'on ajuste souvent les formulations (prompt engineering) sans
toucher au code métier.
"""

# Fenêtres autorisées, répétées dans les descriptions pour que le modèle
# les voie explicitement plutôt que d'inventer un format.
WINDOW_PARAM = {
    "type": "string",
    "enum": ["5m", "1h", "24h"],
    "description": "Time window. Must be exactly one of: 5m, 1h, 24h",
}

TOOL_DEFINITIONS = [
    {
        "type": "function",
        "function": {
            "name": "get_platform_health",
            "description": (
                "Get the overall health of the platform: application status, "
                "database status, memory, CPU, HTTP latency and error rate. "
                "Use this when the user asks a general question such as "
                "'is everything ok', 'is there a problem', 'how is the platform'."
            ),
            # Aucun paramètre : le modèle ne peut rien se tromper.
            # C'est l'outil le plus fiable, et le plus souvent appelé.
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_memory_usage",
            "description": (
                "Get the CURRENT JVM heap memory usage and CPU load, right now. "
                "Use this when the user asks about memory, heap, RAM or CPU "
                "at this instant. Do NOT use this tool for history, trend or "
                "evolution over a period: use get_metric_history instead. Do NOT "
                "use it for predictions either: use get_resource_forecast."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_http_performance",
            "description": (
                "Get HTTP latency, throughput and error rate over a time window. "
                "Use this when the user asks if the application is slow, about "
                "response times, or about HTTP errors."
            ),
            "parameters": {
                "type": "object",
                "properties": {"window": WINDOW_PARAM},
                "required": ["window"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_slowest_endpoints",
            "description": (
                "List the slowest API endpoints. Use this when the user asks "
                "which page, endpoint, API or service is slow, or wants to know "
                "where the bottleneck is."
            ),
            "parameters": {
                "type": "object",
                "properties": {"window": WINDOW_PARAM},
                "required": ["window"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_metric_history",
            "description": (
                "Get the HISTORY of one metric over a time window: minimum, "
                "average, maximum, current value and whether it is rising, "
                "falling or stable. Use this when the user asks about history, "
                "trend, evolution, 'is it increasing', 'over the last hour', "
                "or compares now with before. Always prefer this tool over "
                "get_memory_usage and get_http_performance whenever the question "
                "mentions a period of time or an evolution. For a PREDICTION "
                "('when will it', 'at this rate', 'will it saturate'), use "
                "get_resource_forecast instead."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "metric": {
                        "type": "string",
                        "enum": ["memory", "cpu", "latency", "errors", "throughput"],
                        "description": (
                            "Which metric to look at. Must be exactly one of: "
                            "memory, cpu, latency, errors, throughput"
                        ),
                    },
                    "window": WINDOW_PARAM,
                },
                "required": ["metric", "window"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_resource_forecast",
            "description": (
                "PREDICT when memory or CPU will reach its alert threshold, "
                "from the trend of the last hour. Use this when the user asks "
                "'when', 'how long before', 'at this rate', 'will it saturate', "
                "'will memory run out', or asks for a forecast or a prediction. "
                "The forecast is already computed: report it as given, never "
                "compute a time or a date yourself."
            ),
            # Aucun paramètre, comme get_memory_usage : les deux ressources
            # tiennent en deux lignes, et un modèle de cette taille choisit
            # d'autant plus mal qu'il a d'options. La fenêtre est fixée à une
            # heure — l'horizon de la prévision ne dépasse jamais la durée
            # observée, et « la journée » ne se prédit pas sur une droite.
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_schools",
            "description": (
                "List the schools (establishments) present on the platform. "
                "Use this when the user asks which schools exist, how many "
                "schools there are, or when you need the exact name of a school "
                "before calling get_school_metrics."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_school_metrics",
            "description": (
                "Get the business figures of ONE school, named by the user: "
                "number of users (by role), students, classes, teachers, and "
                "the number of timetable generations with their outcome. "
                "Use this whenever the question is about a specific school, "
                "for example 'how many timetables were generated for Ibn "
                "Khaldoun', 'how many users does school X have', 'how many "
                "students in Carthage'. This tool is about BUSINESS data, not "
                "about servers: do not use it for memory, CPU or latency."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "school": {
                        "type": "string",
                        "description": (
                            "Name of the school exactly as the user wrote it. "
                            "Approximate spelling is fine, it is matched against "
                            "the real list of schools."
                        ),
                    }
                },
                "required": ["school"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_database_status",
            "description": (
                "Get database connection pool status and database health. "
                "Use this when the user asks about the database, connections, "
                "PostgreSQL, or suspects a database bottleneck."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
]