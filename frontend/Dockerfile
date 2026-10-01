FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Production bundle talks to the same origin; Nginx proxies /api.
ENV VITE_API_BASE_URL=
ENV VITE_AI_SERVICE_URL=
RUN npm run build

FROM nginx:1.27-alpine
# nginx:1.27-alpine still ships OpenSSL 3.3.3-r0 (CVE-2026-31789). Alpine 3.21 has the fix.
RUN apk upgrade --no-cache libssl3 libcrypto3
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
