#!/usr/bin/env bash
#
# Sauvegarde les DEUX bases de la pile, en un seul geste et sous un seul
# horodatage.
#
# ── Pourquoi les deux, toujours ──────────────────────────────────────────────
# La base applicative référence Keycloak par identifiant : chaque établissement
# porte le sien dans `tenant.keycloak_group_id` (posé par DemoDataRunner), et
# chaque `school_user` correspond à un utilisateur du realm. Sauvegarder l'une
# sans l'autre produit une paire qu'on ne peut plus recoller — les identifiants
# ne désignent plus rien, et toute création de compte échoue. Le symptôme
# ressemble à une panne d'authentification et se cherche du mauvais côté.
#
# D'où un répertoire par sauvegarde, qui rend le couple structurel plutôt que
# conventionnel : on ne peut pas restaurer une moitié par distraction.
#
#   scripts/sauvegarde.sh              → sauvegardes/2026-09-10-143205/
#   scripts/sauvegarde.sh /media/cle   → ailleurs
#
set -euo pipefail
cd "$(dirname "$0")/.."

racine=${1:-sauvegardes}
cible="$racine/$(date +%Y-%m-%d-%H%M%S)"

# Les deux bases doivent tourner. Un refus net vaut mieux qu'un répertoire à
# moitié rempli, qu'on croira complet le jour où on en aura besoin.
for service in app-db keycloak-db; do
  if ! docker compose ps --services --status running 2>/dev/null | grep -qx "$service"; then
    echo "✗ Le service '$service' n'est pas démarré — rien n'a été sauvegardé." >&2
    echo "  Lancer la pile d'abord :  docker compose up -d" >&2
    exit 1
  fi
done

mkdir -p "$cible"
# Sur échec, on ne laisse pas de trace : un `.partiel` oublié finirait par être
# pris pour un dump. `rmdir` échoue si le répertoire contient les deux fichiers,
# c'est-à-dire en cas de succès — d'où le `|| true`.
trap 'rm -f "$cible"/*.partiel; rmdir "$cible" 2>/dev/null || true' EXIT

dumper() {
  local service=$1 utilisateur=$2 base=$3 fichier=$4
  printf '  … %-12s' "$base"
  # Le fichier définitif n'apparaît qu'après un dump réussi : une redirection
  # directe créerait le fichier avant même que pg_dump ne s'exécute.
  docker compose exec -T "$service" pg_dump -U "$utilisateur" "$base" > "$cible/$fichier.partiel"
  mv "$cible/$fichier.partiel" "$cible/$fichier"
  printf '%s\n' "$(du -h "$cible/$fichier" | cut -f1)"
}

echo "Sauvegarde vers $cible"
dumper app-db      postgres smartschool app.sql
dumper keycloak-db keycloak keycloak    keycloak.sql

# Un dump ne se restaure que sur le schéma qui l'a produit : Liquibase et
# `ddl-auto: update` font évoluer les tables au fil des commits. Sans cette
# trace, retrouver la version du code qui va avec un dump vieux de trois
# semaines relève de la fouille.
cat > "$cible/contexte.txt" <<CONTEXTE
Sauvegarde  : $(date '+%Y-%m-%d %H:%M:%S')
Commit      : $(git rev-parse --short HEAD 2>/dev/null || echo 'hors dépôt git') ($(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?'))
TAG images  : ${TAG:-latest}
Restauration:
  scripts/restauration.sh $cible
CONTEXTE

echo
echo "✓ Les deux bases sont sauvegardées. Restauration :"
echo "    scripts/restauration.sh $cible"
