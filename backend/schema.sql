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

CREATE DATABASE IF NOT EXISTS hehiachi_mishima CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE hehiachi_mishima;

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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora en que se asignó.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora en que se asignó.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    SELECT id INTO v_admin_id
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
    SELECT nombre INTO v_nombre FROM roles WHERE id = OLD.rol_id;
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