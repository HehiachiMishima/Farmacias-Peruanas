# Plataforma de Control de Accesos

Sistema de gestion de identidades, roles y permisos.

- **Frontend**: Angular 22 (standalone, zoneless) + SCSS.
- **Backend**: Spring Boot 4.1.1 (Java 21), API REST stateless con JWT (HS256).
- **Base de datos**: MySQL.

---

## Estructura del proyecto

```
.
├── Dockerfile               # Imagen unica: frontend + backend + nginx
├── render.yaml              # Blueprint de despliegue en Render (1 servicio)
├── backend/                 # API Spring Boot
│   ├── .env.example         # Plantilla de variables de entorno
│   ├── certs/ca.pem         # CA TLS
│   ├── schema.sql           # Esquema de la base de datos
│   └── src/
└── frontend/                # SPA Angular
    └── src/
```

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
                      ▼  (localhost:BACKEND_PORT, interno)
                Spring Boot (JWT)  ──►  MySQL
```

- `PORT` (por defecto `10000`) es el **unico puerto publicado**; `BACKEND_PORT`
  (por defecto `2003`) es **interno** y no sale del contenedor.
- nginx sirve el SPA y hace de proxy inverso de `/api` hacia el backend.
- `supervisord` arranca **nginx + backend** en el mismo contenedor.
- El SPA usa `apiUrl: '/api'` (mismo origen): **no hay CORS** entre el
  navegador y la API.

---

## Requisitos

- Java 21 y Node 22 (desarrollo sin Docker).
- MySQL accesible.

---

## Configuracion del entorno

El backend lee sus credenciales de `backend/.env` (que **no se sube al
repositorio**). Es un **unico archivo conmutable**: contiene los dos bloques
de conexion (MySQL local y Aiven); para alternar, comenta uno y descomenta el
otro. No hace falta copiar nada encima.

Para empezar, parte de la plantilla en blanco:

```bash
cp backend/.env.example backend/.env
```

Luego edita `backend/.env`: rellena los secretos y deja activo el bloque de
conexion que corresponda (local para desarrollo; Aiven para produccion).

Render **no usa el archivo**: inyecta las variables desde el dashboard
(ver `render.yaml`).

| Variable | Descripcion |
| --- | --- |
| `PORT` | Puerto **publico** del contenedor (nginx). Es el unico expuesto. Render lo inyecta (`10000`). |
| `BACKEND_PORT` | Puerto **interno** del backend Java (no se publica). Debe ser **distinto** de `PORT`. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PARAMS` | Conexion MySQL. |
| `JWT_SECRET` | Firma JWT, minimo 32 caracteres (`openssl rand -hex 32`). |
| `CORS_ALLOWED_ORIGINS` | Origenes del frontend, separados por comas. |
| `USER_*` | Usuario administrador semilla (se crea al arrancar si no existe). |

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

SPA en `http://localhost:4200` (usa `environment.development.ts`).

---

## Docker (solo despliegue)

El `Dockerfile` existe **unicamente** para empaquetar y desplegar en Render
(un solo contenedor: nginx + Spring Boot). En local se trabaja **sin Docker**,
corriendo backend y frontend a mano (ver *Desarrollo local*).

> `PORT` (por defecto `10000`) es el unico puerto publicado; `BACKEND_PORT`
> (por defecto `2003`) es interno. Nunca deben coincidir: el entrypoint lo
> detecta y aborta con un error claro.

---

## Despliegue en Render

`render.yaml` crea **un solo servicio web** (`hehiachi-mishima`) a partir
del `Dockerfile` de la raiz.

Pasos:

1. Sube el repositorio a GitHub/GitLab.
2. En Render: **New + > Blueprint** y selecciona el repositorio.
3. Render pedira los valores marcados con `sync: false`
   (credenciales de la base de datos y datos del administrador).
4. Al terminar, abre la URL de `hehiachi-mishima`.

Notas:

- La imagen escucha en `PORT` (Render usa `10000`); el backend queda en el
  puerto interno `2003`.
- El health check es `GET /api/health` (publico, responde `204`).
- En `plan: free` el servicio se suspende tras 15 min de inactividad y
  tarda ~1 min en reactivarse.

---

## Endpoints principales

Base: `/api`

| Metodo | Ruta | Acceso |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Publico |
| `GET` | `/api/health` | Publico |
| Resto | `/api/**` | Requiere JWT (`Authorization: Bearer ...`) |

---

## Seguridad

- `backend/.env` esta ignorado por Git; **no** lo subas.
- Rota `JWT_SECRET` y las credenciales de la base de datos de forma
  periodica.
- En Render, los secretos se piden al crear el Blueprint y nunca se
  escriben en `render.yaml`.
