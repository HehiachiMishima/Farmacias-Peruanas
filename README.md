# Plataforma de Control de Accesos

Sistema para el gobierno de identidades, roles y permisos y para el registro y
seguimiento de incidencias (Bitacora e Inbound), con acceso por paneles segun
los permisos de cada usuario.

- **Frontend**: Angular 22 (standalone, zoneless, signals) + SCSS.
- **Backend**: Spring Boot 4.1.1 (Java 21), API REST stateless con JWT (HS256).
- **Base de datos**: MySQL (Aiven en produccion).

---

## Paneles

| Panel | Ruta | Modulos | Permiso de entrada |
| --- | --- | --- | --- |
| Administracion | `/admin` | Dashboard, Usuarios, Roles, Permisos | `PANEL_ADMIN` |
| Bitacora | `/bitacora` | Incidencias, Control (centros, sistemas, areas, responsables), Dashboard | `PANEL_BITACORA` |
| Inbound | `/inbound` | Incidencias, Control (proveedores, tipos de incidencia), Dashboard | `PANEL_INBOUND` |

El *launcher* (`/`) muestra unicamente los paneles y modulos habilitados por los
permisos del usuario. Cada ruta protegida valida su permiso con `permisoGuard`.

---

## Estructura del proyecto

```
.
├── Dockerfile                  # Imagen unica: frontend + backend + nginx
├── render.yaml                 # Blueprint de Render (1 solo servicio web)
├── backend/                    # API Spring Boot
│   ├── .env.example            # Plantilla de variables (el .env real no se sube)
│   ├── certs/ca.pem            # CA TLS de Aiven (LOCAL: ignorada por Git)
│   ├── schema.sql              # Esquema de la base de datos
│   ├── data.sql                # Datos iniciales
│   ├── panel.bitacora.sql      # Catalogos del panel Bitacora
│   ├── panel.inbound.sql       # Catalogos del panel Inbound
│   ├── protection.admin.sql    # Proteccion del usuario administrador
│   └── src/
└── frontend/                   # SPA Angular
    └── src/
```

> Los archivos `.sql` y `backend/certs/` **no** entran en la imagen: el
> `Dockerfile` solo copia `backend/pom.xml` y `backend/src`. El esquema y los
> datos de la base se aplican de forma manual; la aplicacion **no** ejecuta
> scripts SQL al arrancar (no hay `schema.sql`/`data.sql` en `resources`).

---

## Arquitectura de la imagen (todo en uno)

Un **unico contenedor / un unico servicio**:

```
        navegador
            │  (HTTP : PORT = puerto publico)
            ▼
      ┌───────────────────────────────┐
      │  nginx  (puerto PORT)          │  sirve el SPA Angular
      │    /            -> SPA         │
      │    /api/*       -> backend     │
      └───────────────┬───────────────┘
                      ▼  (127.0.0.1:BACKEND_PORT, interno)
                Spring Boot (JWT)  ──►  MySQL (Aiven)
```

- `PORT` (por defecto `10000`) es el **unico puerto publicado**; `BACKEND_PORT`
  (por defecto `2003`) es **interno** y no sale del contenedor.
- nginx sirve el SPA y hace de proxy inverso de `/api` hacia el backend.
- `supervisord` arranca **nginx + backend** en el mismo contenedor.
- En produccion el SPA usa `apiUrl: '/api'` (mismo origen): **no hay CORS**
  entre el navegador y la API. En desarrollo si lo hay (Angular en
  `http://localhost:4200` -> backend en `http://localhost:2003`).

---

## Requisitos

- Java 21 y Node 22 (para desarrollo sin Docker).
- Un MySQL accesible (Aiven en produccion).
- Docker (solo para construir/verificar la imagen de despliegue).

---

## Configuracion del entorno

El backend lee sus credenciales de `backend/.env` (que **no se sube al
repositorio**). Es un **unico archivo conmutable**: contiene los dos bloques de
conexion (MySQL local y Aiven) y para alternar basta con comentar uno y
descomentar el otro. No hace falta copiar nada encima.

Para empezar, parte de la plantilla:

```bash
cp backend/.env.example backend/.env
```

Luego edita `backend/.env`, rellena los secretos y deja activo el bloque de
conexion que corresponda (local para desarrollo, Aiven para produccion).

Render **no usa ese archivo**: inyecta las variables desde el dashboard
(ver `render.yaml`).

| Variable | Descripcion |
| --- | --- |
| `PORT` | Puerto **publico** del contenedor (nginx). Es el unico expuesto. Render lo inyecta (`10000`). |
| `BACKEND_PORT` | Puerto **interno** del backend Java (no se publica). Debe ser **distinto** de `PORT`. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PARAMS` | Conexion MySQL. En Aiven `DB_PARAMS` incluye `sslMode=REQUIRED&serverTimezone=UTC`. |
| `JWT_SECRET` | Firma JWT. **Minimo 32 bytes** (`openssl rand -hex 32`). |
| `JWT_ISSUER` | Emisor del token (claim `iss`). Debe coincidir entre emision y validacion. |
| `JWT_EXPIRATION_MS` | Vida del token en ms (opcional, por defecto `3600000`). |
| `CORS_ALLOWED_ORIGINS` | Origenes permitidos del frontend, separados por comas. |
| `USER_ADMIN`, `USER_PASSWORD`, `USER_EMAIL` | **Obligatorios** para crear el administrador semilla. |
| `USER_NAME`, `USER_LASTNAME`, `USER_PHONE`, `USER_ACTIVO`, `USER_ROL` | Datos del administrador semilla (opcionales). |

> El usuario semilla se crea **una sola vez** al arrancar si no existe
> (idempotente). La contrasena se guarda cifrada con BCrypt.

---

## Desarrollo local

> Requiere `backend/.env` con el bloque de conexion **local** activo.
> El backend escucha en `BACKEND_PORT` (`2003`), que es lo que usa el frontend.

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

SPA en `http://localhost:4200` (usa `environment.development.ts`, que apunta al
backend local).

---

## Docker (solo despliegue)

El `Dockerfile` existe **unicamente** para empaquetar y desplegar en Render (un
solo contenedor: nginx + Spring Boot). En local se trabaja **sin Docker**,
corriendo backend y frontend por separado (ver *Desarrollo local*).

La imagen es **100% produccion**: no copia `backend/.env`, no incluye los `.sql`
ni los certificados, y compila el SPA con la configuracion de produccion
(`apiUrl: '/api'`). Todas las credenciales entran por variables de entorno.

Para construir y verificar la imagen exactamente como lo hara Render:

```bash
# Construir
docker build -t farmacias-peruanas:local .

# Probar (solo variables de entorno; nunca backend/.env)
docker run --rm -p 10000:10000 \
  -e PORT=10000 -e BACKEND_PORT=2003 \
  -e DB_HOST=<host> -e DB_PORT=<puerto> -e DB_NAME=<bd> \
  -e DB_USER=<usuario> -e DB_PASSWORD=<clave> \
  -e DB_PARAMS='sslMode=REQUIRED&serverTimezone=UTC' \
  -e JWT_SECRET=<32+ caracteres> -e JWT_ISSUER=farmacias-peruanas \
  farmacias-peruanas:local
```

La app queda en `http://localhost:10000`; el health check responde en
`http://localhost:10000/api/health`.

> `PORT` y `BACKEND_PORT` deben ser **distintos**: si coinciden, el entrypoint
> aborta con un error claro.

---

## Despliegue en Render

`render.yaml` crea **un solo servicio web** llamado `farmacias-peruanas`
(`runtime: docker`, `plan: free`, `region: oregon`) a partir del `Dockerfile`
de la raiz.

Pasos:

1. Sube el repositorio a GitHub/GitLab (rama por defecto `farmacias_peruanas`).
2. En Render: **New + > Blueprint** y selecciona el repositorio.
3. Render pedira los valores marcados con `sync: false` (credenciales de la
   base de datos y datos del administrador). Completalos.
4. Al terminar, abre `https://farmacias-peruanas.onrender.com`.

Notas:

- La imagen escucha en `PORT` (`10000`); el backend queda en el puerto interno
  `2003`.
- El health check es `GET /api/health` (publico, responde `204`). Cualquier
  `2xx`/`3xx` cuenta como sano para Render.
- `CORS_ALLOWED_ORIGINS` se toma del propio servicio (`RENDER_EXTERNAL_URL`),
  asi que no hay que escribirlo a mano.
- En `plan: free` el servicio se suspende tras ~15 min de inactividad y el
  primer arranque en frio tarda ~1 min.

### Variables que Render pide al crear el Blueprint (`sync: false`)

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PARAMS`,
`USER_ADMIN`, `USER_PASSWORD`, `USER_NAME`, `USER_LASTNAME`, `USER_EMAIL`,
`USER_PHONE`.

`JWT_SECRET` se **genera** automaticamente (`generateValue: true`); `PORT`,
`JWT_ISSUER`, `USER_ACTIVO`, `USER_ROL` y `CORS_ALLOWED_ORIGINS` vienen fijados
en el `render.yaml`.

### Si el deploy falla

| Sintoma | Causa probable |
| --- | --- |
| El sync del Blueprint da error de YAML | Claves duplicadas o mal escritas en `render.yaml`. |
| Deploy en rojo, health check `502` | El backend no arranca: falta alguna variable `DB_*` o la base no es alcanzable. |
| `Could not resolve placeholder 'DB_HOST'` | Falta una variable de conexion en el dashboard. |
| `JWT_SECRET ... al menos 32 bytes` | `JWT_SECRET` demasiado corto. |
| Build larga / agota memoria | La construccion compila Angular + Maven; en `free` conviene reintentar. |

---

## Endpoints principales

Base: `/api`

| Metodo | Ruta | Acceso |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Publico |
| `GET` | `/api/health` | Publico |
| `GET/POST` | `/api/usuarios`, `/api/roles`, `/api/permisos` | Requiere JWT + permiso |
| `GET/POST` | `/api/centros`, `/api/areas`, `/api/sistemas` | Requiere JWT + permiso |
| `GET/POST` | `/api/incidencias` | Requiere JWT + permiso |
| `GET/POST` | `/api/inbound/incidencias`, `/api/inbound/control/*` | Requiere JWT + permiso |
| Resto | `/api/**` | Requiere JWT (`Authorization: Bearer ...`) |

La autorizacion fina vive en los `@PreAuthorize` de cada endpoint; el
`SecurityFilterChain` solo exige autenticacion salvo en las rutas publicas.

---

## Rendimiento y escalado

- **Frontend**: busqueda con debounce (300ms), `distinctUntilChanged` y
  `switchMap` (cancela respuestas fuera de orden). Componentes con `@for` +
  `trackBy` (id). Lazy loading por rutas y por panel.
- **Backend**: cache en memoria de catalogos (TTL 10 min) compartido entre
  servicios para evitar `findAll()` por pagina. Vistas + JdbcTemplate para
  resolucion de permisos, `open-in-view=false`, virtual threads habilitados,
  timeouts y pool de conexiones configurados.
- **API**: respuestas estructuradas, manejo global de errores
  (`@RestControllerAdvice`), CORS configurable y rate limiting de login por
  IP/ventana.

---

## Seguridad

- `backend/.env` y `backend/certs/` estan ignorados por Git; **no** los subas.
- Rota `JWT_SECRET` y las credenciales de la base de datos periodicamente.
- En Render los secretos se piden al crear el Blueprint y nunca se escriben en
  `render.yaml`.
- El backend corre detras de nginx con `server.forward-headers-strategy=native`
  para que la IP real del cliente llegue al rate limiter y a los logs.
