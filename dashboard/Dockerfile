# syntax=docker/dockerfile:1

# ---- Build: compile the static bundle ----
FROM node:26.13.1-alpine AS build
WORKDIR /app

# Dependencies first, so this layer is cached until package-lock.json changes.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Tests and lint run in CI, not in the image build.
COPY . .
RUN npm run build

# ---- Runtime: nginx serves the bundle and proxies the API ----
# The unprivileged variant runs as a non-root user and listens on 8080.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine

# Where /api/ requests go; the image's entrypoint substitutes it into the template at startup.
ENV ANALYSIS_URL=http://analysis-service:8082

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
