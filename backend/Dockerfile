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
COPY smartschool-api/pom.xml        smartschool-api/
COPY coverage-report/pom.xml        coverage-report/
# `|| true` : le pré-téléchargement est une optimisation, pas une étape
# critique. S'il échoue, le build suivant récupérera ce qui manque.
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
# Les tests tournent dans la CI, pas ici : reconstruire l'image ne doit pas
# relancer 562 tests ni exiger un démon Docker pour Testcontainers.
RUN mvn -B clean package -DskipTests -pl smartschool-api -am

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
