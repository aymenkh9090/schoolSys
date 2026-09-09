#!/usr/bin/env bash
#
# Lancement du service en développement, joignable depuis le réseau local.
#
# Ce script existe pour une seule raison : uvicorn se lie à `127.0.0.1` par
# défaut. Lancé sans `--host 0.0.0.0`, le service répond parfaitement depuis le
# poste — `/docs` s'ouvre, `/health` dit `UP` — et reste injoignable pour
# l'application mobile, qui l'appelle par l'IP du portable sur le réseau local.
# Le symptôme n'accuse alors pas le bon coupable : côté téléphone on lit
# « Serveur injoignable », et on cherche du côté de l'assistant ou du jeton
# alors que rien n'est jamais parti sur le réseau.
#
# `UVICORN_HOST` plutôt qu'un `--host` en dur : uvicorn lit ses options dans
# l'environnement (click, `auto_envvar_prefix="UVICORN"`), donc la valeur reste
# surchargeable sans toucher au script — `UVICORN_HOST=127.0.0.1 ./run-dev.sh`
# pour refermer le service sur le poste.
#
# ATTENTION : la même ligne dans un fichier `.env` ne suffirait PAS. Ni le
# `.env` du dossier, ni `uvicorn --env-file .env` ne changent l'interface
# d'écoute — vérifié, les deux se lient à `127.0.0.1`. Un fichier d'environnement
# est chargé APRÈS l'analyse de la ligne de commande, quand l'hôte est déjà
# résolu. Seule une variable présente dans l'environnement du processus au
# démarrage est lue. D'où ce script, et non une ligne dans `.env.example`.

set -euo pipefail

cd "$(dirname "$0")"

export UVICORN_HOST="${UVICORN_HOST:-0.0.0.0}"

# Le port 8001 est celui que le mobile interroge (`mobile/src/config.ts`) ; le
# 8000 est pris par le conteneur, qui ne voit pas Ollama.
exec .venv/bin/uvicorn app.main:app --reload --port "${UVICORN_PORT:-8001}"
