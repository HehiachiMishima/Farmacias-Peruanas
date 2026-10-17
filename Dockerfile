# syntax=docker/dockerfile:1
# ============================================================
# Imagen UNICA: Angular (frontend) + Spring Boot (backend) + nginx.
#
#   - nginx sirve el SPA en el puerto PUBLICO (PORT) y reenvia /api
#     al backend, que corre en un puerto INTERNO (BACKEND_PORT).
#   - supervisord arranca ambos procesos dentro del mismo contenedor.
#   - Un solo contenedor => un solo servicio en Render.
#
# PUERTOS:
#   PORT          -> puerto publico (nginx). Es el UNICO que se publica
#                    con -p. Render lo inyecta (10000). Por defecto 10000.
#   BACKEND_PORT  -> puerto interno del backend Java (por defecto 2003).
#                    NO se publica nunca; solo nginx lo usa por dentro.
#
# El archivo backend/.env NO se copia: las credenciales entran como
# variables de entorno (Render las inyecta).
# ============================================================

# ---------- Etapa 1: build del frontend ----------
FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build -- --configuration production

# ---------- Etapa 2: build del backend ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -DskipTests clean package

# ---------- Etapa 3: runtime ----------
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache nginx supervisor gettext \
    && mkdir -p /run/nginx

# Backend (jar ejecutable) y frontend (SPA compilado).
COPY --from=backend /app/target/*.jar /app/app.jar
COPY --from=frontend /app/dist/frontend/browser /usr/share/nginx/html

# --- Config de nginx: sirve el SPA y proxifica /api al backend ---
RUN cat > /etc/nginx/http.d/default.conf.template <<'NGINX'
server {
    listen ${PORT};
    server_name _;

    root /usr/share/nginx/html;
    index index.html;
    client_max_body_size 10m;

    gzip on;
    gzip_comp_level 5;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_vary on;
    gzip_types text/plain text/css text/javascript application/javascript application/json application/xml image/svg+xml font/woff2;

    location /api/ {
        proxy_pass http://127.0.0.1:${BACKEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_connect_timeout 15s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    location ~* \.(?:js|css|woff2?|ttf|eot|png|jpe?g|gif|svg|webp|ico)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX

# --- Wrapper para lanzar nginx en primer plano (evita problemas de quoting) ---
RUN printf '#!/bin/sh\nexec nginx -g "daemon off;"\n' > /usr/local/bin/run-nginx \
    && chmod +x /usr/local/bin/run-nginx

# --- Supervisord: arranca backend y nginx en el mismo contenedor ---
RUN cat > /etc/supervisord.conf <<'SUPERVISORD'
[supervisord]
nodaemon=true
logfile=/dev/null
logfile_maxbytes=0
pidfile=/run/supervisord.pid

[program:backend]
command=java -XX:MaxRAMPercentage=70 -XX:+UseContainerSupport -jar /app/app.jar --server.port=%(ENV_BACKEND_PORT)s
directory=/app
autorestart=true
startsecs=5
stdout_logfile=/dev/stdout
stdout_logfile_maxbytes=0
stderr_logfile=/dev/stderr
stderr_logfile_maxbytes=0

[program:nginx]
command=/usr/local/bin/run-nginx
autorestart=true
priority=20
stdout_logfile=/dev/stdout
stdout_logfile_maxbytes=0
stderr_logfile=/dev/stderr
stderr_logfile_maxbytes=0
SUPERVISORD

# --- Entrypoint: resuelve los puertos, genera la config de nginx y arranca ---
RUN cat > /entrypoint.sh <<'SH'
#!/bin/sh
set -e

PORT="${PORT:-10000}"
BACKEND_PORT="${BACKEND_PORT:-2003}"

if [ "$PORT" = "$BACKEND_PORT" ]; then
  echo "ERROR: PORT y BACKEND_PORT no pueden coincidir (=$PORT):" >&2
  echo "       nginx (publico) y el backend (interno) chocarian en el mismo puerto." >&2
  exit 1
fi

envsubst '${PORT} ${BACKEND_PORT}' \
  < /etc/nginx/http.d/default.conf.template \
  > /etc/nginx/http.d/default.conf

exec /usr/bin/supervisord -c /etc/supervisord.conf
SH
RUN chmod +x /entrypoint.sh

# PORT = publico (Render lo inyecta; por defecto 10000).
# BACKEND_PORT = interno del backend (por defecto 2003). Deben ser distintos.
ENV PORT=10000
ENV BACKEND_PORT=2003
EXPOSE 10000

ENTRYPOINT ["/entrypoint.sh"]
