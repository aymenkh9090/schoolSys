# slim : ~50 Mo d'image de base contre ~350 Mo pour l'image complète.
# Suffisant ici : aucune dépendance ne nécessite de compilation native
# (cryptography fournit des wheels précompilés).
FROM python:3.12-slim

WORKDIR /app

# PYTHONUNBUFFERED : sans cela, les logs Python sont bufferisés et
# n'apparaissent pas en temps réel dans `docker logs`.
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# Les dépendances AVANT le code : Docker met en cache cette couche.
# Une modification du code ne relance pas l'installation des paquets.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# Pas de root dans le conteneur : le service n'écrit rien sur le disque.
RUN useradd --create-home --uid 1001 appuser
USER appuser

EXPOSE 8000

# Pas de --reload en production.
# --workers 1 : le service est I/O-bound et stateless ; monter les workers
# multiplierait surtout la charge sur Ollama, qui traite en série de toute façon.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
