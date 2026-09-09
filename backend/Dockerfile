
# ── Étage 1 : compilation ────────────────────────────────────────────────────
# L'image maven officielle porte déjà le JDK 21 : rien à installer.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les poms d'abord, seuls. Tant qu'aucune dépendance ne change, Docker réutilise
# la couche suivante et ne retélécharge pas le dépôt Maven à chaque build —
# c'est ce qui fait passer une reconstruction de plusieurs minutes à quelques
# secondes.
COPY pom.xml .
COPY security-module/pom.xml        security-module/
COPY common-module/pom.xml          common-module/
COPY tenant-business/pom.xml        tenant-business/
COPY organisation-business/pom.xml  organisation-business/
COPY smartschool-planning/pom.xml   smartschool-planning/
COPY absence-business/pom.xml       absence-business/
COPY pointage-business/pom.xml      pointage-business/
COPY smartschool-api/pom.xml        smartschool-api/
COPY coverage-report/pom.xml        coverage-report/
# `|| true` : le pré-téléchargement est une optimisation, pas une étape
# critique. S'il échoue, le build suivant récupérera ce qui manque.
#
# Le montage de cache est ce qui fait la différence entre « quelques minutes »
# et « quelques secondes ». La couche ci-dessus n'est réutilisée que tant
# qu'aucun pom ne bouge ; dès qu'un seul change, elle est invalidée et, SANS ce
# montage, tout le dépôt Maven — des centaines de mégaoctets, Timefold et Spring
# Boot compris — était retéléchargé depuis le réseau. Le cache BuildKit, lui,
# vit HORS des couches : il survit à l'invalidation, au changement de pom et au
# `docker builder prune` des couches.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
# Les tests tournent dans la CI, pas ici : reconstruire l'image ne doit pas
# relancer 562 tests ni exiger un démon Docker pour Testcontainers.
#
# Même dépôt Maven que l'étape précédente : les dépendances déjà résolues par
# `go-offline` sont retrouvées ici sans un seul appel réseau.
#
# `-T 1C` : un fil de compilation par cœur. Les neuf modules du projet ne
# forment pas une chaîne — `common-module` et `security-module` n'ont rien à
# s'attendre l'un l'autre — et Maven respecte le graphe de dépendances tout en
# parallélisant ce qui peut l'être.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -T 1C clean package -DskipTests -pl smartschool-api -am

# ── Étage 2 : exécution ──────────────────────────────────────────────────────
# JRE seul : ni Maven, ni compilateur, ni sources dans l'image finale.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Utilisateur non privilégié : un conteneur applicatif n'a aucune raison de
# tourner en root.
RUN useradd --system --create-home --shell /usr/sbin/nologin schoolsys
USER schoolsys

COPY --from=build --chown=schoolsys /build/smartschool-api/target/schoolsys-api-*.jar app.jar

# 8080 : API REST. 9091 : actuator (health, prometheus), séparé volontairement
# pour ne pas exposer la supervision sur le port public.
EXPOSE 8080 9091

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
