# ── Étage 1 : build Vite ─────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
# `npm ci` et non `npm install` : installation reproductible à partir du
# lock-file, et échec net si package.json et lock-file divergent.
RUN npm ci

COPY . .

# Vite substitue les VITE_* À LA COMPILATION : elles sont figées dans le bundle.
# Les passer en variables d'environnement du conteneur nginx n'aurait aucun
# effet — d'où les ARG ici.
ARG VITE_API_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8081
ARG VITE_KEYCLOAK_REALM=smartschool
ARG VITE_KEYCLOAK_CLIENT_ID=smartschool-frontend
ARG VITE_AI_ASSISTANT_URL=http://localhost:8000
ENV VITE_API_URL=$VITE_API_URL \
    VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID \
    VITE_AI_ASSISTANT_URL=$VITE_AI_ASSISTANT_URL

RUN npm run build

# ── Étage 2 : service statique ───────────────────────────────────────────────
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
