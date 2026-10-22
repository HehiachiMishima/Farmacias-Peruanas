# Control Tower · Ingeniería Logística (Farmacias Peruanas)

Plataforma de control operativo: gobierno de identidades, roles y permisos, y
registro/seguimiento de incidencias (Bitácora e Inbound), con acceso por
paneles según los permisos de cada usuario.

- **Frontend**: Angular 22 (standalone, zoneless, signals) + SCSS. Diseño
  "Control Tower": cromo navy, rojo de marca y paleta de señales, modo claro/oscuro.
- **Backend**: Spring Boot 4.1 (Java 21), API REST stateless con JWT (HS256),
  organizado **por paneles**.
- **Base de datos**: MySQL 8 (Aiven en producción).

---

## Paneles

| Panel | Ruta | Módulos | Permiso de entrada |
| --- | --- | --- | --- |
| Administración | `/admin` | Dashboard, Usuarios, Roles, Permisos | `PANEL_ADMIN` |
| Bitácora | `/bitacora` | Incidencias, Control (centros, sistemas, áreas, responsables), Dashboard | `PANEL_BITACORA` |
| Inbound | `/inbound` | Incidencias, Control (proveedores, tipos de incidencia), Dashboard | `PANEL_INBOUND` |

El *launcher* (`/`) muestra los paneles y, en cada uno, los módulos habilitados
para el usuario. Cada ruta protegida valida su permiso con `permisoGuard`, y
cada endpoint con `@PreAuthorize`.

---

## Estructura del proyecto

```
.
├── Dockerfile                  # Imagen única: frontend + backend + nginx
├── docker-compose.yml          # Stack local opcional (MySQL + imagen)
├── render.yaml                 # Blueprint de Render (1 servicio web)
├── backend/
│   ├── .env.example            # Plantilla de variables (el .env real no se sube)
│   ├── schema.sql              # 1) RBAC: usuarios, roles, permisos, vistas, triggers
│   ├── panel.bitacora.sql      # 2) Tablas del panel Bitácora
│   ├── panel.inbound.sql       # 3) Tablas del panel Inbound
│   ├── protection.admin.sql    # 4) Protección del usuario @admin (triggers)
│   ├── data.sql                # 5) Permisos de paneles, catálogos y datos de ejemplo
│   └── src/main/java/oracle/
│       ├── auth/               # Login y sesión vigente
│       ├── admin/              # Panel Administración (usuarios, roles, permisos, resolución)
│       ├── bitacora/           # Panel Bitácora (incidencias + catálogos de control)
│       ├── inbound/            # Panel Inbound (incidencias + proveedores/tipos)
│       └── common/             # Transversal: config, security, exception, support, web
└── frontend/src/app/
    ├── core/                   # config, guards, interceptors, models, services, utils
    ├── features/               # auth, launcher, panels/{admin,bitacora,inbound}
    └── shared/                 # directivas, pipes y UI (icon, modal, charts, …)
```

Cada panel del backend tiene sus propias capas:
`controller / service / repository / model / request / response`.

---

## Base de datos

> Los `.sql` **no** se ejecutan automáticamente ni entran en la imagen: se
> aplican a mano (o con `docker compose`, ver más abajo). Hibernate no toca el
> esquema (`ddl-auto=none`).

**Orden de ejecución** (mismo para local y Aiven):

| Paso | Archivo | Notas |
| --- | --- | --- |
| 1 | `schema.sql` | Crea la BD `farmacias_peruanas`, RBAC, vistas de resolución y triggers. Re-ejecutable. |
| 2 | `panel.bitacora.sql` | Catálogos e `incidencias`. `CREATE TABLE IF NOT EXISTS`. |
| 3 | `panel.inbound.sql` | Proveedores, tipos e `incidencias_inbound`. |
| 4 | **Arrancar el backend una vez** | Crea el usuario semilla `USER_ADMIN` (id = 1) con BCrypt y le asigna `USER_ROL`. |
| 5 | `protection.admin.sql` | Triggers que blindan a `@admin`. Re-ejecutable. |
| 6 | `data.sql` | Permisos de Bitácora/Inbound, catálogos y registros de ejemplo. Requiere el usuario id = 1 (FK de `creado_por`). |

Con el cliente `mysql` (respeta `DELIMITER`):

```bash
mysql -h <host> -P <puerto> -u <usuario> -p < backend/schema.sql
```

> `data.sql`: permisos y catálogos son idempotentes; las incidencias de
> Bitácora usan `INSERT IGNORE` (ticket único). Las de Inbound no tienen clave
> natural: ejecútalo **una sola vez** o se duplicarán los registros de ejemplo.

---

## Configuración del entorno

El backend lee sus credenciales de `backend/.env` (no se sube al repositorio).
Es un **único archivo conmutable**: contiene los bloques de conexión (MySQL
local y Aiven) y para alternar basta con comentar uno y descomentar el otro.

```bash
cp backend/.env.example backend/.env
```

Render **no usa ese archivo**: inyecta las variables desde el dashboard.

| Variable | Descripción |
| --- | --- |
| `PORT` | Puerto **público** del contenedor (nginx). Render lo inyecta (`10000`). |
| `BACKEND_PORT` | Puerto **interno** del backend Java. Debe ser **distinto** de `PORT`. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PARAMS` | Conexión MySQL. En Aiven `DB_PARAMS` incluye `sslMode=REQUIRED&serverTimezone=UTC`. |
| `JWT_SECRET` | Firma JWT. **Mínimo 32 bytes** (`openssl rand -hex 32`). |
| `JWT_ISSUER` | Emisor del token (`iss`). |
| `JWT_EXPIRATION_MS` | Vida del token en ms (opcional, por defecto `3600000`). |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (solo si el SPA se sirve desde otro dominio). |
| `USER_ADMIN`, `USER_PASSWORD`, `USER_EMAIL` | **Obligatorios** para crear el administrador semilla. |
| `USER_NAME`, `USER_LASTNAME`, `USER_PHONE`, `USER_ACTIVO`, `USER_ROL` | Datos opcionales del administrador semilla. |
| `JAVA_OPTS` | (Solo Docker) flags de la JVM. |

> El usuario `@admin` está protegido en la BD (`protection.admin.sql`) **y** en
> el backend (`Protecciones.USUARIOS_PROTEGIDOS`): no se puede editar, borrar
> ni cambiar sus roles/permisos. Si usas otro `USER_ADMIN`, ajusta ambos.

---

## Desarrollo local (sin Docker)

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

API en `http://localhost:2003/api`.

### Frontend

```bash
cd frontend
npm install
npm start
```

SPA en `http://localhost:4200` (usa `environment.development.ts`, que apunta a
`http://localhost:2003/api`).

### Tests del backend

```bash
cd backend
./mvnw test -Dtest='!BackendApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
```

(`BackendApplicationTests` levanta el contexto completo y necesita una BD.)

---

## Docker

La imagen es **100 % producción**: compila el SPA con `apiUrl: '/api'`, no
copia `backend/.env`, los `.sql` ni los certificados, y activa el perfil
`prod` (logs JSON). Todas las credenciales entran por variables de entorno.

### Imagen sola (igual que Render)

```bash
docker build -t farmacias-peruanas:local .
```

```bash
docker run --rm -p 10000:10000 --env-file backend/.env -e PORT=10000 -e BACKEND_PORT=2003 farmacias-peruanas:local
```

App en `http://localhost:10000`; health check en `/api/health` (204). La
imagen trae `HEALTHCHECK` propio.

> Si tu `.env` apunta a un MySQL en `127.0.0.1`, dentro del contenedor usa
> `-e DB_HOST=host.docker.internal`.

### Stack completo con MySQL (`docker compose`)

```bash
docker compose up --build
```

- MySQL 8.4 en `localhost:3307`; al crear el volumen aplica `schema.sql`,
  `panel.bitacora.sql` y `panel.inbound.sql`.
- La app arranca, crea `@admin` y queda en `http://localhost:10000`.
- Luego aplica `protection.admin.sql` y `data.sql`:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" farmacias_peruanas' < backend/protection.admin.sql
```

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" farmacias_peruanas' < backend/data.sql
```

Los secretos (`JWT_SECRET`, `USER_*`) se leen de `backend/.env` si existe.

---

## Despliegue en Render

`render.yaml` crea **un solo servicio web** `farmacias-peruanas`
(`runtime: docker`, `plan: free`, `region: oregon`, rama `farmacias_peruanas`,
`autoDeploy`).

1. Sube el repositorio a GitHub/GitLab.
2. Render: **New + → Blueprint** y selecciona el repositorio.
3. Completa los valores `sync: false`: `DB_*`, `USER_*` y (opcional)
   `CORS_ALLOWED_ORIGINS`.
4. Aplica los `.sql` en Aiven siguiendo el orden de **Base de datos** (el paso 4
   es el primer deploy).
5. Abre `https://farmacias-peruanas.onrender.com`.

Notas:

- `JWT_SECRET` se genera automáticamente (`generateValue: true`).
- nginx reenvía `X-Forwarded-Proto` original (Render termina TLS), así Spring
  reconoce el propio dominio y no aplica CORS a las peticiones del SPA.
- En `plan: free` el servicio se suspende tras ~15 min sin tráfico; el primer
  arranque en frío tarda ~1 min. Para miles de usuarios usa un plan con RAM
  ≥ 1 GB y sube `spring.datasource.hikari.maximum-pool-size` según el límite
  de conexiones de tu plan de Aiven.

### Si el deploy falla

| Síntoma | Causa probable |
| --- | --- |
| Error de YAML al sincronizar el Blueprint | Claves duplicadas o mal escritas en `render.yaml`. |
| Deploy en rojo, health check `502` | El backend no arranca: falta alguna `DB_*` o la base no es alcanzable. |
| `Could not resolve placeholder 'DB_HOST'` | Falta una variable de conexión en el dashboard. |
| `JWT_SECRET ... al menos 32 bytes` | `JWT_SECRET` demasiado corto. |
| Build larga / sin memoria | La build compila Angular + Maven; en `free` conviene reintentar. |

---

## Endpoints principales

Base: `/api`

| Método | Ruta | Acceso |
| --- | --- | --- |
| `POST` | `/auth/login` | Público (rate limit por IP e IP+usuario) |
| `GET` | `/auth/sesion` | JWT — usuario y permisos vigentes |
| `GET` | `/health` | Público |
| `GET/POST/PUT/DELETE` | `/usuarios`, `/roles`, `/permisos` | JWT + permiso |
| `GET` | `/usuarios/resumen` | Roles y permisos de todos los usuarios (2 consultas) |
| `GET/POST/PUT/DELETE` | `/centros`, `/areas`, `/sistemas` | JWT + `*_CONTROL` |
| `GET/POST/PUT/DELETE` | `/incidencias` (+ `/exportar`, `/estadisticas`) | JWT + `*_INCIDENCIAS` / `VER_DASHBOARD` |
| `GET/POST/PUT/DELETE` | `/inbound/incidencias`, `/inbound/control/*` | JWT + permisos `*_INBOUND` |
| `GET` | `/incidencias/analitica` | `VER_DASHBOARD` — KPIs, Pareto de sistemas, matriz centro × sistema, tipos, tendencia y horas |
| `GET` | `/inbound/incidencias/analitica` | `VER_DASHBOARD_INBOUND` — ranking/Pareto de proveedores, matriz proveedor × tipo, SKUs, tendencias |
| `GET` | `/admin/analitica` | `DASHBOARD_ADMIN` — cuentas, roles, permisos, cobertura por panel y altas |

---

## Rendimiento y escalado

- **Listados del frontend** (`ListadoRemoto` / `RecursoRemoto`): un único flujo
  con `switchMap` cancela la petición anterior, los errores no rompen la
  búsqueda y la tabla conserva las filas mientras refresca (sin parpadeo).
- **Catálogos cacheados** en el navegador (`BitacoraCatalogos`,
  `InboundCatalogos`) y en el servidor (`CatalogoBitacora`, `CatalogoInbound`,
  invalidados en cada CRUD): resolver una página de incidencias no hace
  consultas por fila.
- **Permisos**: caché Caffeine por usuario (invalidada al cambiar roles,
  permisos o estado); el frontend resincroniza la sesión al volver a la pestaña.
- **Usuarios**: el listado obtiene roles/permisos de todos con
  `/usuarios/resumen` en lugar de 2 peticiones por usuario.
- **Dashboards**: cada endpoint de analítica lanza sus agregaciones SQL
  (`GROUP BY`, CTE + `ROW_NUMBER`) **en paralelo** con `Mono.zip` sobre un
  scheduler acotado (`EjecutorAnalitica`, `app.analitica.paralelismo`, 6 por
  defecto): la latencia es la de la consulta más lenta y un pico de dashboards
  nunca agota el pool de conexiones de los CRUD. El resultado se cachea por
  filtro (single-flight) y se invalida al escribir incidencias o catálogos.
  Requiere MySQL 8 (CTE y funciones de ventana).
- **Gráficos**: componentes SVG/CSS propios (`OnPush`, sin librerías): KPI con
  variación vs periodo anterior, Pareto, mapa de calor, barras apiladas,
  líneas y columnas. Se adaptan al contenedor sin deformarse.
- **Responsive**: en móvil las tablas se muestran como tarjetas
  (`data-label` automático) y el panel de filtros es una hoja inferior.
- **Infra**: virtual threads, `open-in-view=false`, timeouts JDBC, apagado
  ordenado, nginx con gzip, keep-alive al backend y cache inmutable de assets.

---

## Seguridad

- `backend/.env` y `backend/certs/` están ignorados por Git; **no** los subas.
- Errores de triggers (`SQLSTATE 45000`) llegan como 403 con su mensaje; un
  borrado bloqueado por FK llega como 409 claro.
- nginx añade `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`
  y `Permissions-Policy`.
- Rota `JWT_SECRET` y las credenciales de la base periódicamente.
