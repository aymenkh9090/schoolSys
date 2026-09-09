#!/usr/bin/env bash
#
# Restaure une sauvegarde produite par scripts/sauvegarde.sh — LES DEUX BASES,
# jamais une seule (cf. l'en-tête de sauvegarde.sh pour la raison).
#
#   scripts/restauration.sh                          → la plus récente
#   scripts/restauration.sh sauvegardes/2026-09-10-143205
#   scripts/restauration.sh <répertoire> --oui        → sans confirmation
#
set -euo pipefail
cd "$(dirname "$0")/.."

repertoire=""
sans_confirmation=false
for argument in "$@"; do
  case "$argument" in
    --oui) sans_confirmation=true ;;
    *)     repertoire="$argument" ;;
  esac
done

if [ -z "$repertoire" ]; then
  repertoire=$(ls -1d sauvegardes/*/ 2>/dev/null | sort | tail -1 || true)
  repertoire=${repertoire%/}
  [ -n "$repertoire" ] || { echo "✗ Aucune sauvegarde dans sauvegardes/." >&2; exit 1; }
fi

# Les deux fichiers, ou rien : restaurer la base applicative sur un realm
# Keycloak qui ne lui correspond pas est précisément ce que ces scripts
# existent pour empêcher.
for fichier in app.sql keycloak.sql; do
  [ -s "$repertoire/$fichier" ] || {
    echo "✗ $repertoire/$fichier est absent ou vide — sauvegarde incomplète, on n'y touche pas." >&2
    exit 1
  }
done

echo "Sauvegarde à restaurer : $repertoire"
[ -f "$repertoire/contexte.txt" ] && sed 's/^/  /' "$repertoire/contexte.txt"
echo
echo "⚠ Les bases 'smartschool' et 'keycloak' vont être SUPPRIMÉES puis recréées."
echo "  Tout ce qu'elles contiennent aujourd'hui sera perdu."

if [ "$sans_confirmation" = false ]; then
  # La sauvegarde d'abord : proposer le filet avant de faire le saut coûte une
  # ligne, et c'est la seule occasion de l'attraper.
  echo
  echo "  (sauvegarder l'état actuel d'abord : scripts/sauvegarde.sh)"
  echo
  read -r -p "Taper « restaurer » pour confirmer : " reponse
  [ "$reponse" = "restaurer" ] || { echo "Annulé — rien n'a été touché."; exit 1; }
fi

# On n'arrête que ce qui tourne, et on ne redémarrera que cela : la pile peut
# être lancée sans le profil `app` (API dans l'IDE), auquel cas `backend` et
# `frontend` ne sont pas des conteneurs. Keycloak doit partir aussi — on ne
# supprime pas une base dont un serveur tient les connexions ouvertes.
a_redemarrer=$(docker compose ps --services --status running 2>/dev/null \
  | grep -E '^(backend|frontend|ai-assistant|keycloak)$' || true)

if [ -n "$a_redemarrer" ]; then
  echo
  echo "Arrêt des services qui écrivent : $(echo "$a_redemarrer" | tr '\n' ' ')"
  # shellcheck disable=SC2086
  docker compose stop $(echo "$a_redemarrer" | tr '\n' ' ') >/dev/null
fi

restaurer() {
  local service=$1 utilisateur=$2 base=$3 fichier=$4
  printf '  … %-12s' "$base"
  # WITH (FORCE) — PostgreSQL 13+, et la pile tourne en 16 : coupe les
  # connexions résiduelles au lieu d'échouer sur « database is being accessed
  # by other users ». Sans lui, un client oublié (psql, un IDE, pgAdmin) suffit
  # à faire échouer la restauration à mi-chemin.
  docker compose exec -T "$service" psql -q -U "$utilisateur" -d postgres \
    -c "DROP DATABASE IF EXISTS $base WITH (FORCE);" \
    -c "CREATE DATABASE $base OWNER $utilisateur;" >/dev/null
  docker compose exec -T "$service" psql -q -U "$utilisateur" -d "$base" \
    -v ON_ERROR_STOP=1 < "$repertoire/$fichier" >/dev/null
  printf 'restaurée\n'
}

echo
restaurer app-db      postgres smartschool app.sql
restaurer keycloak-db keycloak keycloak    keycloak.sql

if [ -n "$a_redemarrer" ]; then
  echo
  echo "Redémarrage : $(echo "$a_redemarrer" | tr '\n' ' ')"
  # shellcheck disable=SC2086
  docker compose start $(echo "$a_redemarrer" | tr '\n' ' ') >/dev/null
fi

echo
echo "✓ Les deux bases sont restaurées."

# Le garde-fou de l'étape 2, rappelé au seul moment où il compte vraiment :
# on vient de remettre en place des données qu'on tient à garder, et le seeder
# purge les données pédagogiques dès qu'il ne reconnaît pas la structure des
# niveaux. Une restauration suivie d'une purge serait une farce.
if ! grep -qE '^SPRING_PROFILES_ACTIVE=\s*$' .env 2>/dev/null; then
  echo
  echo "⚠ SPRING_PROFILES_ACTIVE n'est pas désarmé dans .env : le profil 'demo'"
  echo "  reste actif, et DemoDataRunner peut purger les données pédagogiques"
  echo "  au prochain démarrage du backend. Pour l'en empêcher :"
  echo "      echo 'SPRING_PROFILES_ACTIVE=' >> .env && docker compose up -d backend"
fi
