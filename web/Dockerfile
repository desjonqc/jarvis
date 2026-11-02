# syntax=docker/dockerfile:1
# The web app, built then served by Caddy (with the JARVIS API proxied under /api, see deploy/Caddyfile).

# ---- Build (on the build machine's architecture : the result is plain static files) ----
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- Serve ----
FROM caddy:2-alpine
COPY --from=build /app/dist /srv
