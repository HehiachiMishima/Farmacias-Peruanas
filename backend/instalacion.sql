-- Active: 1791152181635@@127.0.0.1@3306@farmacias_peruanas
-- ============================================================
-- INSTALACIÓN COMPLETA: Atlas · Farmacias Peruanas
-- ============================================================
-- Crea la base desde cero en un solo paso (local o Aiven):
--
--   mysql -h <host> -P <puerto> -u <usuario> -p < backend/instalacion.sql
--
-- ¡ATENCIÓN! Empieza con DROP DATABASE: borra farmacias_peruanas y
-- todos sus datos. Haz un respaldo antes si algo importa.
--
-- Después: arrancar el backend una vez. Crea el usuario semilla
-- (USER_ADMIN del .env, id = 1) y le asigna USER_ROL. Opcional: cargar
-- los datos de ejemplo de Bitácora con data.sql.
--
-- Fechas: creado_en / actualizado_en en UTC; horas de negocio (TIME)
-- tal cual. Ver la nota de fechas al inicio de la sección 1.
-- ============================================================

DROP DATABASE IF EXISTS farmacias_peruanas;

-- ############################################################
-- 1. Base de datos, RBAC (usuarios, roles, permisos), vistas y triggers
--    (origen: schema.sql)
-- ############################################################
-- ============================================================
-- ESQUEMA: RBAC + Overrides
-- ============================================================
-- Lógica combinada:
--   1) RBAC puro        → roles, permisos, roles_permisos
--   2) Asignación       → usuarios_roles
--   3) Overrides        → usuarios_permisos (grant/deny por usuario)
--   4) Resolución       → v_permisos_efectivos + v_permisos_finales (deny-wins)
--
-- Protecciones (todas por NOMBRE, sin flags):
--
--   * Rol 'ADMINISTRADOR':
--       - No se puede eliminar.
--       - No se puede modificar NADA.
--       - Se le pueden AGREGAR permisos.
--       - No se le pueden QUITAR permisos.
--       - Un trigger AFTER INSERT en `permisos` le asigna
--         automáticamente cada permiso nuevo.
--
--   * Permisos BASE (lista hardcodeada por nombre):
--       - No se pueden eliminar.
--       - No se pueden renombrar, cambiar descripción ni desactivar.
--
-- Fechas (no dependen de la zona horaria del servidor ni de la sesión):
--   * creado_en / actualizado_en: DATETIME(6) en UTC. El DEFAULT usa
--     UTC_TIMESTAMP(6); un UPDATE manual debe fijar
--     actualizado_en = UTC_TIMESTAMP(6) (no hay ON UPDATE).
--   * Horas de negocio (TIME): hora de negocio tal cual, sin conversión.
--     La zona de negocio la define el backend, que también convierte los
--     filtros y agrupaciones por día a esa zona.

CREATE DATABASE IF NOT EXISTS farmacias_peruanas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE farmacias_peruanas;

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- Limpieza de objetos de versiones previas
-- ------------------------------------------------------------
DROP VIEW IF EXISTS v_permisos_finales;

DROP VIEW IF EXISTS v_permisos_efectivos;

DROP TRIGGER IF EXISTS trg_roles_admin_update;

DROP TRIGGER IF EXISTS trg_roles_admin_delete;

DROP TRIGGER IF EXISTS trg_roles_permisos_admin_delete;

DROP TRIGGER IF EXISTS trg_permisos_after_insert;

DROP TRIGGER IF EXISTS trg_permisos_base_update;

DROP TRIGGER IF EXISTS trg_permisos_base_delete;

DROP TRIGGER IF EXISTS trg_transacciones_after_insert;

DROP TRIGGER IF EXISTS trg_transacciones_after_update;

DROP TRIGGER IF EXISTS trg_transacciones_incidencia_after_insert;

DROP TRIGGER IF EXISTS trg_transacciones_incidencia_after_update;

-- ============================================================
-- 1) usuarios
-- ============================================================
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único del usuario.',
    usuario VARCHAR(50) NOT NULL COMMENT 'Nombre de login, único.',
    contrasena VARCHAR(100) NOT NULL COMMENT 'Contraseña hasheada con bcrypt.',
    nombre VARCHAR(100) NOT NULL COMMENT 'Nombre real del usuario.',
    apellido VARCHAR(100) NOT NULL COMMENT 'Apellido real del usuario.',
    email VARCHAR(100) NOT NULL COMMENT 'Correo electrónico único.',
    telefono VARCHAR(20) NULL COMMENT 'Teléfono de contacto. Opcional.',
    activo BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'TRUE = puede iniciar sesión; FALSE = bloqueado.',
    creado_por BIGINT DEFAULT NULL COMMENT 'ID del usuario que creó este registro. NULL si es el primero. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'ID del usuario que actualizó el registro por última vez. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_usuarios_usuario UNIQUE (usuario),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    INDEX idx_usuarios_activo_nombre (activo, apellido, nombre) COMMENT 'Listado de activos/bloqueados ordenado alfabéticamente. Cubre también filtro solo por activo.',
    INDEX idx_usuarios_nombre (apellido, nombre) COMMENT 'Listado general ordenado alfabéticamente, sin filtrar por estado.',
    INDEX idx_usuarios_creado_en (creado_en) COMMENT 'Ordenamiento temporal por fecha de alta.'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Usuarios del sistema. @hehiachi está protegido por nombre en triggers.';

-- ============================================================
-- 2) roles
-- ============================================================
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único del rol.',
    nombre VARCHAR(50) NOT NULL COMMENT 'Nombre del rol, único. Ej: ADMINISTRADOR.',
    descripcion VARCHAR(200) NULL COMMENT 'Descripción legible del rol.',
    activo BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'TRUE = sus permisos cuentan; FALSE = inactivo.',
    creado_por BIGINT NOT NULL COMMENT 'ID del usuario que creó el rol. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'ID del usuario que modificó el rol. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_roles_nombre UNIQUE (nombre),
    INDEX idx_roles_activo (activo) COMMENT 'Filtrado masivo de roles activos/inactivos.'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Roles. ADMINISTRADOR está protegido por nombre en triggers.';

-- ============================================================
-- 3) permisos
-- ============================================================
CREATE TABLE IF NOT EXISTS permisos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único del permiso.',
    nombre VARCHAR(50) NOT NULL COMMENT 'Nombre del permiso, único. Ej: CREAR_INCIDENCIAS.',
    descripcion VARCHAR(200) NULL COMMENT 'Descripción legible del permiso.',
    activo BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'TRUE = el permiso cuenta; FALSE = ignorado en la resolución.',
    creado_por BIGINT NOT NULL COMMENT 'ID del usuario que creó el permiso. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'ID del usuario que modificó el permiso. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_permisos_nombre UNIQUE (nombre),
    INDEX idx_permisos_activo (activo) COMMENT 'Filtrado masivo de permisos activos/inactivos.'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de permisos. Los base están protegidos por nombre en triggers.';

-- ============================================================
-- 4) roles_permisos — plantilla RBAC
-- ============================================================
CREATE TABLE IF NOT EXISTS roles_permisos (
    rol_id BIGINT NOT NULL COMMENT 'FK al rol.',
    permiso_id BIGINT NOT NULL COMMENT 'FK al permiso.',
    creado_por BIGINT NOT NULL COMMENT 'ID del usuario que otorgó el permiso al rol. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora en que se asignó (UTC).',
    PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT fk_roles_permisos_rol_id FOREIGN KEY (rol_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_roles_permisos_permiso_id FOREIGN KEY (permiso_id) REFERENCES permisos (id) ON DELETE CASCADE,
    INDEX idx_roles_permisos_permiso_id (permiso_id, rol_id) COMMENT 'Reverse lookup: qué roles tienen un permiso concreto.'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Plantilla de permisos por rol (RBAC).';

-- ============================================================
-- 5) usuarios_roles — asignación de roles a usuarios
-- ============================================================
CREATE TABLE IF NOT EXISTS usuarios_roles (
    usuario_id BIGINT NOT NULL COMMENT 'FK al usuario.',
    rol_id BIGINT NOT NULL COMMENT 'FK al rol.',
    creado_por BIGINT NOT NULL COMMENT 'ID del usuario que asignó el rol. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora en que se asignó (UTC).',
    PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_usuarios_roles_usuario_id FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuarios_roles_rol_id FOREIGN KEY (rol_id) REFERENCES roles (id) ON DELETE CASCADE,
    INDEX idx_usuarios_roles_rol_id (rol_id, usuario_id) COMMENT 'Reverse lookup: qué usuarios tienen un rol concreto.'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Asignación usuario-rol.';

-- ============================================================
-- 6) usuarios_permisos — overrides individuales
-- ============================================================
CREATE TABLE IF NOT EXISTS usuarios_permisos (
    usuario_id BIGINT NOT NULL COMMENT 'FK al usuario.',
    permiso_id BIGINT NOT NULL COMMENT 'FK al permiso.',
    otorgado BOOLEAN NOT NULL COMMENT 'TRUE = concede; FALSE = deniega (deny-wins).',
    creado_por BIGINT NOT NULL COMMENT 'ID del usuario que creó el override. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'ID del usuario que modificó el override. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    PRIMARY KEY (usuario_id, permiso_id),
    CONSTRAINT fk_usuarios_permisos_usuario_id FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuarios_permisos_permiso_id FOREIGN KEY (permiso_id) REFERENCES permisos (id) ON DELETE CASCADE,
    INDEX idx_usuarios_permisos_permiso_id (permiso_id, usuario_id) COMMENT 'Reverse lookup: qué usuarios tienen override sobre un permiso.'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Overrides individuales de permisos.';

-- ============================================================
-- VISTAS DE RESOLUCIÓN — respetan activo de rol y permiso
-- ============================================================
-- Resolución:
--   v_permisos_efectivos → une rol (TRUE) + override (otorgado)
--   v_permisos_finales   → MIN(otorgado) → deny-wins
CREATE OR REPLACE VIEW v_permisos_efectivos AS
SELECT ur.usuario_id, rp.permiso_id, TRUE AS otorgado, 'ROL' AS origen
FROM
    usuarios_roles ur
    JOIN roles r ON r.id = ur.rol_id
    AND r.activo = TRUE
    JOIN roles_permisos rp ON rp.rol_id = ur.rol_id
    JOIN permisos p ON p.id = rp.permiso_id
    AND p.activo = TRUE
UNION ALL
SELECT up.usuario_id, up.permiso_id, up.otorgado, 'OVERRIDE' AS origen
FROM
    usuarios_permisos up
    JOIN permisos p ON p.id = up.permiso_id
    AND p.activo = TRUE;

CREATE OR REPLACE VIEW v_permisos_finales AS
SELECT
    usuario_id,
    permiso_id,
    MIN(otorgado) AS permitido
FROM v_permisos_efectivos
GROUP BY
    usuario_id,
    permiso_id;

-- ============================================================
-- TRIGGER PREVIO AL SEEDING: auto-asignación de permisos al
-- rol ADMINISTRADOR. Se crea ANTES del seed para que los
-- permisos base queden auto-asignados sin INSERT manual.
-- ============================================================
DELIMITER $$

CREATE TRIGGER trg_permisos_after_insert
AFTER INSERT ON permisos
FOR EACH ROW
BEGIN
    DECLARE v_admin_id BIGINT;

    SELECT id
      INTO v_admin_id
      FROM roles
     WHERE nombre = 'ADMINISTRADOR'
     LIMIT 1;

    IF v_admin_id IS NOT NULL THEN
        INSERT IGNORE INTO roles_permisos (rol_id, permiso_id, creado_por)
        VALUES (v_admin_id, NEW.id, 0);
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- DATOS INICIALES
-- ============================================================

-- ------------------------------------------------------------
-- Rol núcleo (primero, para que el trigger lo encuentre)
-- ------------------------------------------------------------
INSERT INTO
    roles (
        nombre,
        descripcion,
        activo,
        creado_por,
        actualizado_por
    )
VALUES (
        'ADMINISTRADOR',
        'Rol núcleo con acceso total al sistema. No editable ni eliminable, solo se le pueden agregar permisos.',
        TRUE,
        0,
        0
    )
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion);

-- ------------------------------------------------------------
-- Permisos BASE (protegidos por nombre en triggers)
-- (el trigger los asigna automáticamente a ADMINISTRADOR)
-- ------------------------------------------------------------
INSERT INTO
    permisos (
        nombre,
        descripcion,
        activo,
        creado_por,
        actualizado_por
    )
VALUES (
        'ASIGNAR_ROLES',
        'Permite asignar roles a los usuarios.',
        TRUE,
        0,
        0
    ),
    (
        'VER_ROLES',
        'Permite listar y consultar roles.',
        TRUE,
        0,
        0
    ),
    (
        'CREAR_ROLES',
        'Permite crear nuevos roles.',
        TRUE,
        0,
        0
    ),
    (
        'MODIFICAR_ROLES',
        'Permite editar roles no protegidos.',
        TRUE,
        0,
        0
    ),
    (
        'ELIMINAR_ROLES',
        'Permite eliminar roles no protegidos.',
        TRUE,
        0,
        0
    ),
    (
        'ASIGNAR_PERMISOS',
        'Permite asignar permisos a un rol.',
        TRUE,
        0,
        0
    ),
    (
        'VER_PERMISOS',
        'Permite listar y consultar permisos.',
        TRUE,
        0,
        0
    ),
    (
        'CREAR_PERMISOS',
        'Permite crear nuevos permisos.',
        TRUE,
        0,
        0
    ),
    (
        'MODIFICAR_PERMISOS',
        'Permite editar permisos no base.',
        TRUE,
        0,
        0
    ),
    (
        'ELIMINAR_PERMISOS',
        'Permite eliminar permisos no base.',
        TRUE,
        0,
        0
    ),
    (
        'VER_USUARIOS',
        'Permite listar y consultar usuarios.',
        TRUE,
        0,
        0
    ),
    (
        'CREAR_USUARIOS',
        'Permite crear nuevos usuarios.',
        TRUE,
        0,
        0
    ),
    (
        'MODIFICAR_USUARIOS',
        'Permite editar usuarios existentes.',
        TRUE,
        0,
        0
    ),
    (
        'ELIMINAR_USUARIOS',
        'Permite eliminar usuarios no protegidos.',
        TRUE,
        0,
        0
    ),
    (
        'PANEL_ADMIN',
        'Permite el ingreso al panel administrador.',
        TRUE,
        0,
        0
    ),
    (
        'DASHBOARD_ADMIN',
        'Permite ver el dashboard de administradores.',
        TRUE,
        0,
        0
    )
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion);

-- ============================================================
-- TRIGGERS DE PROTECCIÓN
-- ============================================================
DELIMITER $$

-- ------------------------------------------------------------
-- ROL 'ADMINISTRADOR' (protección por NOMBRE)
-- ------------------------------------------------------------
CREATE TRIGGER trg_roles_admin_update
BEFORE UPDATE ON roles
FOR EACH ROW
BEGIN
    IF OLD.nombre = 'ADMINISTRADOR' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede modificar el rol ADMINISTRADOR. Solo se le pueden agregar permisos.';
    END IF;
END$$

CREATE TRIGGER trg_roles_admin_delete
BEFORE DELETE ON roles
FOR EACH ROW
BEGIN
    IF OLD.nombre = 'ADMINISTRADOR' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede eliminar el rol ADMINISTRADOR.';
    END IF;
END$$

CREATE TRIGGER trg_roles_permisos_admin_delete
BEFORE DELETE ON roles_permisos
FOR EACH ROW
BEGIN
    DECLARE v_nombre VARCHAR(50);

    SELECT nombre
      INTO v_nombre
      FROM roles
     WHERE id = OLD.rol_id;

    IF v_nombre = 'ADMINISTRADOR' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden quitar permisos al rol ADMINISTRADOR. Solo se pueden agregar.';
    END IF;
END$$

-- ------------------------------------------------------------
-- PERMISOS BASE (por NOMBRE)
-- ------------------------------------------------------------
CREATE TRIGGER trg_permisos_base_update
BEFORE UPDATE ON permisos
FOR EACH ROW
BEGIN
    IF OLD.nombre IN (
        'ASIGNAR_ROLES', 'VER_ROLES', 'CREAR_ROLES', 'MODIFICAR_ROLES', 'ELIMINAR_ROLES',
        'ASIGNAR_PERMISOS', 'VER_PERMISOS', 'CREAR_PERMISOS', 'MODIFICAR_PERMISOS', 'ELIMINAR_PERMISOS',
        'VER_USUARIOS', 'CREAR_USUARIOS', 'MODIFICAR_USUARIOS', 'ELIMINAR_USUARIOS',
        'PANEL_ADMIN', 'DASHBOARD_ADMIN'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede modificar un permiso base. Ni siquiera se puede desactivar.';
    END IF;
END$$

CREATE TRIGGER trg_permisos_base_delete
BEFORE DELETE ON permisos
FOR EACH ROW
BEGIN
    IF OLD.nombre IN (
        'ASIGNAR_ROLES', 'VER_ROLES', 'CREAR_ROLES', 'MODIFICAR_ROLES', 'ELIMINAR_ROLES',
        'ASIGNAR_PERMISOS', 'VER_PERMISOS', 'CREAR_PERMISOS', 'MODIFICAR_PERMISOS', 'ELIMINAR_PERMISOS',
        'VER_USUARIOS', 'CREAR_USUARIOS', 'MODIFICAR_USUARIOS', 'ELIMINAR_USUARIOS',
        'PANEL_ADMIN', 'DASHBOARD_ADMIN'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede eliminar un permiso base. Es inmutable e intocable.';
    END IF;
END$$

DELIMITER ;

-- ############################################################
-- 2. Panel Bitácora: catálogos e incidencias
--    (origen: panel.bitacora.sql)
-- ############################################################
USE farmacias_peruanas;

-- ============================================================
-- Catálogos / desplegables
-- ============================================================

-- ============================================================
-- data_centro
-- ============================================================
CREATE TABLE IF NOT EXISTS data_centro (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    centro VARCHAR(50) NOT NULL COMMENT 'Nombre de la sede. Ej: CD PUNTA NEGRA.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_data_centro_centro UNIQUE (centro),
    INDEX idx_data_centro_creado_en (creado_en, id),
    INDEX idx_data_centro_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_centro_centro (centro)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de centros.';

-- ============================================================
-- data_sistema
-- ============================================================
CREATE TABLE IF NOT EXISTS data_sistema (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    sistema VARCHAR(50) NOT NULL COMMENT 'Sistema donde ocurrió la incidencia.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_data_sistema_sistema UNIQUE (sistema),
    INDEX idx_data_sistema_creado_en (creado_en, id),
    INDEX idx_data_sistema_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_sistema_sistema (sistema)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de sistemas.';

-- ============================================================
-- data_area (debe ir antes de data_responsable)
-- ============================================================
CREATE TABLE IF NOT EXISTS data_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    area VARCHAR(50) NOT NULL COMMENT 'Nombre del área. Ej: OPERACIONES, LOGÍSTICA.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_data_area_area UNIQUE (area),
    INDEX idx_data_area_creado_en (creado_en, id),
    INDEX idx_data_area_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_area_area (area)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de áreas.';

-- ============================================================
-- data_responsable
-- ============================================================

CREATE TABLE IF NOT EXISTS data_responsable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    id_data_area BIGINT NOT NULL COMMENT 'FK al área a la que pertenece el responsable.',
    responsable VARCHAR(50) NOT NULL COMMENT 'Nombre del responsable. Ej: Juan Pérez.',
    creado_por BIGINT NOT NULL,
    actualizado_por BIGINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_data_responsable_area_responsable UNIQUE (id_data_area, responsable),
    CONSTRAINT uq_data_responsable_id_area UNIQUE (id, id_data_area),
    CONSTRAINT fk_data_responsable_area FOREIGN KEY (id_data_area) REFERENCES data_area (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_data_responsable_area_creado_en (id_data_area, creado_en),
    INDEX idx_data_responsable_creado_en (creado_en, id),
    INDEX idx_data_responsable_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_responsable_responsable (responsable)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de responsables por área.';

-- ============================================================
-- data_incidencia
-- ============================================================

CREATE TABLE IF NOT EXISTS data_incidencia (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    id_data_sistema BIGINT NOT NULL COMMENT 'FK al sistema.',
    incidencia VARCHAR(50) NOT NULL COMMENT 'Tipo o categoría de la incidencia.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_data_incidencia_sistema_incidencia UNIQUE (id_data_sistema, incidencia),
    CONSTRAINT uq_data_incidencia_id_sistema UNIQUE (id, id_data_sistema),
    CONSTRAINT fk_data_incidencia_sistema FOREIGN KEY (id_data_sistema) REFERENCES data_sistema (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_data_incidencia_sistema_creado_en (id_data_sistema, creado_en),
    INDEX idx_data_incidencia_creado_en (creado_en, id),
    INDEX idx_data_incidencia_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_incidencia_incidencia (incidencia)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de incidencias por sistema.';

-- ============================================================
-- Incidencias con FKs a los catálogos
-- ============================================================
CREATE TABLE IF NOT EXISTS incidencias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único de la incidencia.',
    id_data_centro BIGINT NOT NULL COMMENT 'FK al catálogo de centros.',
    id_data_sistema BIGINT NOT NULL COMMENT 'FK al catálogo de sistemas.',
    id_data_incidencia BIGINT NOT NULL COMMENT 'FK al catálogo de incidencias.',
    ticket VARCHAR(59) NOT NULL COMMENT 'Número de ticket asociado, único.',
    id_data_responsable BIGINT NOT NULL COMMENT 'FK al catálogo de responsables.',
    id_data_area BIGINT NOT NULL COMMENT 'FK al catálogo de áreas.',
    descripcion VARCHAR(255) NOT NULL COMMENT 'Descripción detallada del problema o solución.',
    hora_inicio TIME NOT NULL COMMENT 'Hora de inicio (hora de negocio, sin conversión).',
    hora_fin TIME NOT NULL COMMENT 'Hora de cierre (hora de negocio, sin conversión).',
    tiempo_solucion_seg INT UNSIGNED NULL COMMENT 'Duración en segundos. Lo calcula el backend.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó la incidencia. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó la incidencia. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha de la incidencia + hora de inicio, en UTC.',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_incidencias_ticket UNIQUE (ticket),
    CONSTRAINT fk_incidencias_centro FOREIGN KEY (id_data_centro) REFERENCES data_centro (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_sistema FOREIGN KEY (id_data_sistema) REFERENCES data_sistema (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_incidencia FOREIGN KEY (
        id_data_incidencia,
        id_data_sistema
    ) REFERENCES data_incidencia (id, id_data_sistema) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_responsable_area FOREIGN KEY (
        id_data_responsable,
        id_data_area
    ) REFERENCES data_responsable (id, id_data_area) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_area FOREIGN KEY (id_data_area) REFERENCES data_area (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_incidencias_centro_creado_en (id_data_centro, creado_en),
    INDEX idx_incidencias_sistema_creado_en (id_data_sistema, creado_en),
    INDEX idx_incidencias_incidencia_creado_en (id_data_incidencia, creado_en),
    INDEX idx_incidencias_responsable_creado_en (
        id_data_responsable,
        creado_en
    ),
    INDEX idx_incidencias_area_creado_en (id_data_area, creado_en),
    INDEX idx_incidencias_incidencia_sistema (
        id_data_incidencia,
        id_data_sistema
    ),
    INDEX idx_incidencias_responsable_area (
        id_data_responsable,
        id_data_area
    ),
    INDEX idx_incidencias_hora_fin_creado_en (hora_fin, creado_en),
    INDEX idx_incidencias_creado_en (creado_en),
    INDEX idx_incidencias_creado_por (creado_por),
    INDEX idx_incidencias_actualizado_por (actualizado_por)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Bitácora de incidencias.';

-- ------------------------------------------------------------
-- Insert manual: hora_inicio y hora_fin van en hora de negocio (lo que se
-- ve en la web); tiempo_solucion_seg en segundos; creado_en es la fecha +
-- hora de inicio convertida a UTC (Lima 2026-10-29 21:30 -> '2026-10-30
-- 02:30:00'); actualizado_en se omite (DEFAULT en UTC).
-- ------------------------------------------------------------

-- ############################################################
-- 3. Panel Inbound: proveedores, tipos e incidencias inbound
--    (origen: panel.inbound.sql)
-- ############################################################
USE farmacias_peruanas;

-- ============================================================
-- Catálogo de proveedores
-- ============================================================
CREATE TABLE IF NOT EXISTS data_proveedor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    proveedor VARCHAR(100) NOT NULL COMMENT 'Nombre o razón social del proveedor.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_data_proveedor_proveedor UNIQUE (proveedor),
    INDEX idx_data_proveedor_creado_en (creado_en, id),
    INDEX idx_data_proveedor_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_proveedor_proveedor (proveedor)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de proveedores.';

-- ============================================================
-- Catálogo de incidencias inbound
-- ============================================================
CREATE TABLE IF NOT EXISTS data_incidencia_inbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    incidencia_inbound VARCHAR(100) NOT NULL COMMENT 'Tipo o categoría de incidencia de inbound.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT uq_data_incidencia_inbound_incidencia UNIQUE (incidencia_inbound),
    INDEX idx_data_incidencia_inbound_creado_en (creado_en, id),
    INDEX idx_data_incidencia_inbound_actualizado_en (actualizado_en, id),
    FULLTEXT INDEX ft_data_incidencia_inbound_incidencia (incidencia_inbound)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de incidencias de inbound.';

-- ============================================================
-- Bitácora de incidencias inbound
-- ============================================================
CREATE TABLE IF NOT EXISTS incidencias_inbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único de la incidencia inbound.',
    orden_compra VARCHAR(30) NOT NULL COMMENT 'Número de orden de compra.',
    id_data_proveedor BIGINT NOT NULL COMMENT 'FK al catálogo de proveedores.',
    id_data_incidencia_inbound BIGINT NOT NULL COMMENT 'FK al catálogo de incidencias inbound.',
    sku_producto VARCHAR(30) NOT NULL COMMENT 'SKU o EAN del producto.',
    n_unidades INT UNSIGNED NOT NULL COMMENT 'Cantidad de unidades afectadas.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora exacta de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha y hora de la última modificación (UTC).',
    CONSTRAINT fk_incidencias_inbound_proveedor FOREIGN KEY (id_data_proveedor) REFERENCES data_proveedor (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_inbound_incidencia FOREIGN KEY (id_data_incidencia_inbound) REFERENCES data_incidencia_inbound (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_inbound_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incidencias_inbound_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES usuarios (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_incidencias_inbound_orden_compra (orden_compra),
    INDEX idx_incidencias_inbound_proveedor_creado_en (id_data_proveedor, creado_en),
    INDEX idx_incidencias_inbound_incidencia_creado_en (
        id_data_incidencia_inbound,
        creado_en
    ),
    INDEX idx_incidencias_inbound_sku (sku_producto),
    INDEX idx_incidencias_inbound_creado_en (creado_en),
    INDEX idx_incidencias_inbound_actualizado_en (actualizado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Bitácora de incidencias inbound.';

-- ############################################################
-- 4. Panel Simulación: registros Excel, clusters, chutes, rutas e historial
--    (origen: panel.simulacion.sql)
-- ############################################################
USE farmacias_peruanas;

-- ============================================================
-- TABLAS DEL PANEL SIMULACIÓN
--
-- Los permisos del panel se crean en la sección 5.
-- ============================================================

-- ============================================================
-- 1) archivos_excel (Cabecera del archivo subido)
-- ============================================================
CREATE TABLE IF NOT EXISTS archivos_excel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_registro VARCHAR(150) NOT NULL COMMENT 'Nombre del registro (Ej: Ventas Enero).',
    nombre_archivo VARCHAR(255) NOT NULL COMMENT 'Nombre original del archivo .xlsx/.csv.',
    etiqueta VARCHAR(50) NULL COMMENT 'Etiqueta visual (Ej: URGENTE, OK).',
    color_hex VARCHAR(10) NULL COMMENT 'Color del registro (Ej: #6366f1).',
    estado ENUM('PROYECCION', 'BALANCEADO') DEFAULT 'PROYECCION' COMMENT 'Indica si los datos actuales son los originales o ya fueron modificados.',
    creado_por BIGINT NOT NULL COMMENT 'ID del usuario que subió el archivo.',
    actualizado_por BIGINT NOT NULL COMMENT 'ID del usuario que modificó por última vez.',
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_archivos_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id),
    INDEX idx_archivos_creado_por (creado_por),
    INDEX idx_archivos_estado (estado),
    INDEX idx_archivos_fecha (creado_en DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Registros de archivos Excel subidos.';

-- ============================================================
-- 2) simulacion_config (Parámetros globales del archivo)
-- ============================================================
CREATE TABLE IF NOT EXISTS simulacion_config (
    archivo_id BIGINT PRIMARY KEY,
    velocidad_base DECIMAL(10, 2) NOT NULL DEFAULT 100.00 COMMENT 'Bultos por hora por operario.',
    creado_por BIGINT NOT NULL,
    actualizado_por BIGINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_config_archivo FOREIGN KEY (archivo_id) REFERENCES archivos_excel (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Configuración de velocidad para la simulación.';

-- ============================================================
-- 3) clusters (Catálogo de Clusters)
-- ============================================================
CREATE TABLE IF NOT EXISTS clusters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE COMMENT 'Ej: CL1, CL2, CP3.',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_clusters_activo (activo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de clusters.';

-- ============================================================
-- 4) chutes (Catálogo de Chutes)
-- ============================================================
CREATE TABLE IF NOT EXISTS chutes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE COMMENT 'Ej: CHUTE 1, CHUTE 2.',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_chutes_activo (activo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de chutes.';

-- ============================================================
-- 5) parametros_clusters (Límites por archivo)
-- ============================================================
CREATE TABLE IF NOT EXISTS parametros_clusters (
    archivo_id BIGINT NOT NULL,
    cluster_id BIGINT NOT NULL,
    limite_horas DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Tiempo límite en horas para este cluster.',
    PRIMARY KEY (archivo_id, cluster_id),
    CONSTRAINT fk_param_cluster_archivo FOREIGN KEY (archivo_id) REFERENCES archivos_excel (id) ON DELETE CASCADE,
    CONSTRAINT fk_param_cluster_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Tiempos límite por cluster para cada archivo.';

-- ============================================================
-- 6) parametros_chutes (Operarios por archivo)
-- ============================================================
CREATE TABLE IF NOT EXISTS parametros_chutes (
    archivo_id BIGINT NOT NULL,
    chute_id BIGINT NOT NULL,
    operarios INT NOT NULL DEFAULT 2 COMMENT 'Cantidad de operarios asignados.',
    PRIMARY KEY (archivo_id, chute_id),
    CONSTRAINT fk_param_chute_archivo FOREIGN KEY (archivo_id) REFERENCES archivos_excel (id) ON DELETE CASCADE,
    CONSTRAINT fk_param_chute_chute FOREIGN KEY (chute_id) REFERENCES chutes (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Asignación de operarios por chute para cada archivo.';

-- ============================================================
-- 7) rutas_proyeccion (El corazón de los datos y búsquedas masivas)
-- ============================================================
CREATE TABLE IF NOT EXISTS rutas_proyeccion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archivo_id BIGINT NOT NULL,
    chute_id BIGINT NOT NULL,
    cluster_id BIGINT NOT NULL,
    codigo_ruta VARCHAR(50) NOT NULL COMMENT 'Ej: 3030, 3012.',
    total_bultos DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_unidades DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_lineas DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    conteo_cod INT NOT NULL DEFAULT 0 COMMENT 'Cuántos códigos (COD) componen esta ruta.',
    CONSTRAINT fk_ruta_archivo FOREIGN KEY (archivo_id) REFERENCES archivos_excel (id) ON DELETE CASCADE,
    CONSTRAINT fk_ruta_chute FOREIGN KEY (chute_id) REFERENCES chutes (id),
    CONSTRAINT fk_ruta_cluster FOREIGN KEY (cluster_id) REFERENCES clusters (id),
    INDEX idx_ruta_codigo (codigo_ruta),
    INDEX idx_ruta_bultos (total_bultos),
    INDEX idx_rutas_archivo_chute (archivo_id, chute_id),
    INDEX idx_rutas_archivo_cluster (archivo_id, cluster_id),
    FULLTEXT INDEX ft_rutas_busqueda (codigo_ruta)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Rutas del Excel, agrupadas y optimizadas para balanceo.';

-- ============================================================
-- 8) historial_balanceo (Log de cambios del algoritmo)
-- ============================================================
CREATE TABLE IF NOT EXISTS historial_balanceo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archivo_id BIGINT NOT NULL,
    tipo_accion ENUM(
        'MOVER_RUTA',
        'AÑADIR_OPERARIO'
    ) NOT NULL,
    ruta_id BIGINT NULL COMMENT 'FK a la ruta que se movió.',
    chute_origen_id BIGINT NULL,
    chute_destino_id BIGINT NULL,
    chute_modificado_id BIGINT NULL COMMENT 'Chute al que se le sumó el operario.',
    creado_por BIGINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_historial_archivo FOREIGN KEY (archivo_id) REFERENCES archivos_excel (id) ON DELETE CASCADE,
    CONSTRAINT fk_historial_ruta FOREIGN KEY (ruta_id) REFERENCES rutas_proyeccion (id) ON DELETE SET NULL,
    CONSTRAINT fk_historial_origen FOREIGN KEY (chute_origen_id) REFERENCES chutes (id),
    CONSTRAINT fk_historial_destino FOREIGN KEY (chute_destino_id) REFERENCES chutes (id),
    CONSTRAINT fk_historial_modificado FOREIGN KEY (chute_modificado_id) REFERENCES chutes (id),
    CONSTRAINT fk_historial_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id),
    INDEX idx_historial_archivo_fecha (archivo_id, creado_en DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Registro de movimientos realizados por el botón de balanceo.';

-- ############################################################
-- 5. Permisos del panel Simulación
--    (origen: permisos.simulacion.sql)
-- ############################################################
USE farmacias_peruanas;

-- ============================================================
-- PERMISOS DEL PANEL SIMULACIÓN
--
-- Se asignan al rol ADMINISTRADOR (trigger de la sección 1).
-- El resto de roles reciben estos permisos desde Administración > Roles.
-- ============================================================
INSERT INTO
    permisos (
        nombre,
        descripcion,
        activo,
        creado_por,
        actualizado_por
    )
VALUES (
        'PANEL_SIMULACION',
        'Permite el ingreso al panel de simulación.',
        TRUE,
        1,
        1
    ),
    (
        'VER_REGISTRO_SIMULACION',
        'Permite ver el módulo de registro de simulación.',
        TRUE,
        1,
        1
    ),
    (
        'CREAR_REGISTRO_SIMULACION',
        'Permite subir archivos Excel de simulación.',
        TRUE,
        1,
        1
    ),
    (
        'MODIFICAR_REGISTRO_SIMULACION',
        'Permite editar la información de los registros de simulación.',
        TRUE,
        1,
        1
    ),
    (
        'ELIMINAR_REGISTRO_SIMULACION',
        'Permite eliminar registros de simulación.',
        TRUE,
        1,
        1
    ),
    (
        'VER_CONFIGURACION_SIMULACION',
        'Permite ver la configuración y el historial de balanceo.',
        TRUE,
        1,
        1
    ),
    (
        'MODIFICAR_CONFIGURACION_SIMULACION',
        'Permite editar parámetros, mover rutas y ejecutar el balanceo.',
        TRUE,
        1,
        1
    ),
    (
        'VER_DASHBOARD_SIMULACION',
        'Permite ver el dashboard de simulación.',
        TRUE,
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion),
    activo = TRUE;

-- El trigger `trg_permisos_after_insert` (schema.sql) ya concede cada permiso
-- nuevo al rol ADMINISTRADOR. Este INSERT IGNORE lo garantiza también si el
-- trigger no está instalado en la base, sin duplicar asignaciones.
INSERT IGNORE INTO roles_permisos (rol_id, permiso_id, creado_por)
SELECT
    r.id,
    p.id,
    0
FROM
    roles r
    JOIN permisos p ON p.nombre IN (
        'PANEL_SIMULACION',
        'VER_REGISTRO_SIMULACION',
        'CREAR_REGISTRO_SIMULACION',
        'MODIFICAR_REGISTRO_SIMULACION',
        'ELIMINAR_REGISTRO_SIMULACION',
        'VER_CONFIGURACION_SIMULACION',
        'MODIFICAR_CONFIGURACION_SIMULACION',
        'VER_DASHBOARD_SIMULACION'
    )
WHERE
    r.nombre = 'ADMINISTRADOR';

-- ############################################################
-- 6. Permisos de los paneles Bitácora e Inbound
-- ############################################################
USE farmacias_peruanas;

-- Se asignan al rol ADMINISTRADOR (trigger de la sección 1). Los
-- catálogos e incidencias de ejemplo de Bitácora están en data.sql.
INSERT INTO
    permisos (
        nombre,
        descripcion,
        activo,
        creado_por,
        actualizado_por
    )
VALUES (
        'PANEL_BITACORA',
        'Permite el ingreso al panel bitácora.',
        TRUE,
        1,
        1
    ),
    (
        'VER_INCIDENCIAS',
        'Permite ver el módulo de incidencias.',
        TRUE,
        1,
        1
    ),
    (
        'CREAR_INCIDENCIAS',
        'Permite registrar incidencias.',
        TRUE,
        1,
        1
    ),
    (
        'MODIFICAR_INCIDENCIAS',
        'Permite editar incidencias.',
        TRUE,
        1,
        1
    ),
    (
        'ELIMINAR_INCIDENCIAS',
        'Permite eliminar incidencias.',
        TRUE,
        1,
        1
    ),
    (
        'VER_CONTROL',
        'Permite ver el módulo de control de catálogos.',
        TRUE,
        1,
        1
    ),
    (
        'CREAR_CONTROL',
        'Permite crear catálogos de control.',
        TRUE,
        1,
        1
    ),
    (
        'MODIFICAR_CONTROL',
        'Permite editar catálogos de control.',
        TRUE,
        1,
        1
    ),
    (
        'ELIMINAR_CONTROL',
        'Permite eliminar catálogos de control sin uso.',
        TRUE,
        1,
        1
    ),
    (
        'VER_DASHBOARD',
        'Permite ver el dashboard de bitácora.',
        TRUE,
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion);

INSERT INTO
    permisos (
        nombre,
        descripcion,
        activo,
        creado_por,
        actualizado_por
    )
VALUES (
        'PANEL_INBOUND',
        'Permite el ingreso al panel de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'VER_INCIDENCIAS_INBOUND',
        'Permite ver el módulo de incidencias de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'CREAR_INCIDENCIAS_INBOUND',
        'Permite registrar incidencias de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'MODIFICAR_INCIDENCIAS_INBOUND',
        'Permite editar incidencias de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'ELIMINAR_INCIDENCIAS_INBOUND',
        'Permite eliminar incidencias de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'VER_CONTROL_INBOUND',
        'Permite ver el módulo de control de catálogos de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'CREAR_CONTROL_INBOUND',
        'Permite crear catálogos de control de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'MODIFICAR_CONTROL_INBOUND',
        'Permite editar catálogos de control de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'ELIMINAR_CONTROL_INBOUND',
        'Permite eliminar catálogos de control de inbound.',
        TRUE,
        1,
        1
    ),
    (
        'VER_DASHBOARD_INBOUND',
        'Permite ver el dashboard de inbound.',
        TRUE,
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion);

-- ############################################################
-- 7. Protección del usuario @admin
--    (origen: protection.admin.sql)
-- ############################################################
USE farmacias_peruanas;

-- ------------------------------------------------------------
-- USUARIO '@admin' (protección TOTAL por NOMBRE)
--   - No se puede eliminar.
--   - No se puede modificar NINGÚN campo.
--   - usuarios_roles: no se puede agregar ni quitar.
--
-- Puede crearse antes que el usuario: el backend da de alta a @admin al
-- arrancar y el trigger permite asignarle su primer rol.
-- El backend replica estas reglas (Protecciones.USUARIOS_PROTEGIDOS) para
-- responder 403 limpio antes de llegar al SIGNAL de MySQL.
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_usuarios_admin_update;
DROP TRIGGER IF EXISTS trg_usuarios_admin_delete;
DROP TRIGGER IF EXISTS trg_usuarios_roles_admin_insert;
DROP TRIGGER IF EXISTS trg_usuarios_roles_admin_delete;
DROP TRIGGER IF EXISTS trg_usuarios_permisos_admin_insert;
DROP TRIGGER IF EXISTS trg_usuarios_permisos_admin_update;
DROP TRIGGER IF EXISTS trg_usuarios_permisos_admin_delete;

DELIMITER $$

CREATE TRIGGER trg_usuarios_admin_update
BEFORE UPDATE ON usuarios
FOR EACH ROW
BEGIN
    IF OLD.usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede modificar al usuario @admin.';
    END IF;
END$$

CREATE TRIGGER trg_usuarios_admin_delete
BEFORE DELETE ON usuarios
FOR EACH ROW
BEGIN
    IF OLD.usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede eliminar al usuario @admin.';
    END IF;
END$$

CREATE TRIGGER trg_usuarios_roles_admin_insert
BEFORE INSERT ON usuarios_roles
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    DECLARE v_roles INT;
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = NEW.usuario_id;
    IF v_usuario = '@admin' THEN
        SELECT COUNT(*) INTO v_roles FROM usuarios_roles WHERE usuario_id = NEW.usuario_id;
        IF v_roles > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'No se puede modificar el rol de @admin.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_usuarios_roles_admin_delete
BEFORE DELETE ON usuarios_roles
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = OLD.usuario_id;
    IF v_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se puede modificar el rol de @admin.';
    END IF;
END$$

-- Bloquea INSERT de overrides para @admin
CREATE TRIGGER trg_usuarios_permisos_admin_insert
BEFORE INSERT ON usuarios_permisos
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = NEW.usuario_id;
    IF v_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden otorgar ni denegar permisos al usuario @admin.';
    END IF;
END$$

-- Bloquea UPDATE sobre overrides de @admin y también
-- impide "mover" un override hacia @admin cambiando usuario_id
CREATE TRIGGER trg_usuarios_permisos_admin_update
BEFORE UPDATE ON usuarios_permisos
FOR EACH ROW
BEGIN
    DECLARE v_old_usuario VARCHAR(50);
    DECLARE v_new_usuario VARCHAR(50);
    SELECT usuario INTO v_old_usuario FROM usuarios WHERE id = OLD.usuario_id;
    SELECT usuario INTO v_new_usuario FROM usuarios WHERE id = NEW.usuario_id;
    IF v_old_usuario = '@admin' OR v_new_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden modificar los permisos del usuario @admin.';
    END IF;
END$$

-- Bloquea DELETE de overrides de @admin
CREATE TRIGGER trg_usuarios_permisos_admin_delete
BEFORE DELETE ON usuarios_permisos
FOR EACH ROW
BEGIN
    DECLARE v_usuario VARCHAR(50);
    SELECT usuario INTO v_usuario FROM usuarios WHERE id = OLD.usuario_id;
    IF v_usuario = '@admin' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No se pueden eliminar los permisos del usuario @admin.';
    END IF;
END$$

DELIMITER ;
