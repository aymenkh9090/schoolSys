"""
Outils de l'assistant Cahier de séance : schémas JSON vus par le modèle.

Deux outils seulement, et aucun outil d'écriture. Le cahier de séance est un
document à valeur probante — il se verrouille, il fait foi sur ce qui a été
enseigné. Un assistant n'a rien à y écrire : il le lit, le cite, et laisse
l'enseignant tenir la plume.

La division du travail entre les deux outils est le point important :

  - `search_cahier_seances` répond aux questions de CONTENU (« qu'ai-je fait »,
    « quand ai-je traité tel chapitre ») par similarité sémantique ;
  - `get_couverture_programme` répond aux questions d'ÉTAT (« où en est chaque
    classe », « qui a pris du retard ») par un comptage exact.

Confier la seconde à la recherche sémantique serait une erreur classique : le
modèle recevrait cinq extraits et en déduirait une tendance générale, alors que
la réponse est une liste de dates et de compteurs que le code sait établir sans
approximation.
"""

TOOL_DEFINITIONS = [
    {
        "type": "function",
        "function": {
            "name": "search_cahier_seances",
            "description": (
                "Search the lesson logbooks (cahiers de séance) by meaning and "
                "return the most relevant sessions, each with its date, class "
                "and subject. Use this for any question about what was actually "
                "taught, covered, given as homework, or noted as a difficulty: "
                "'what did I do with 8A last week', 'when did we cover "
                "Pythagoras', 'what homework was set', 'what problems were "
                "reported in maths'."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": (
                            "The topic to look for, in French, in the user's own "
                            "words. Include the class and subject when the "
                            "question mentions them, e.g. '7B SVT respiration'."
                        ),
                    }
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_couverture_programme",
            "description": (
                "List, for every class and subject, how many sessions were "
                "recorded, the date of the most recent one and the chapter it "
                "covered. Use this whenever the question is about progress, "
                "delay, or the overall state rather than the content of a "
                "specific lesson: 'which classes are behind', 'where do we "
                "stand', 'has 7B fallen behind in maths', 'how many sessions "
                "have I recorded'."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
]
