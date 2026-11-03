-- ############################################################
-- Migración: panel Malla (aplicar una vez sobre una base creada con la
-- versión anterior de panel.malla.sql).
--   1. Quita la vista v_malla_centro y las columnas origen_id (sin uso).
--   2. Crea el historial de cambios malla_cambio.
--   3. Actualiza la descripción de OLA_LANZAMIENTO (límite por orden de la
--      ola en el cluster, no por su nombre).
--   4. Permiso CONFIGURAR_REGLAS_MALLA para activar reglas y cambiar umbrales.
-- ############################################################

USE farmacias_peruanas;
SET NAMES utf8mb4;

DROP VIEW IF EXISTS v_malla_centro;

ALTER TABLE malla_centro DROP COLUMN origen_id;
ALTER TABLE malla_flujo DROP COLUMN origen_id;
ALTER TABLE malla_ola DROP COLUMN origen_id;

CREATE TABLE IF NOT EXISTS malla_cambio (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    centro_id BIGINT NOT NULL COMMENT 'Sin FK: el historial sobrevive al centro eliminado.',
    codigo_centro VARCHAR(20) NOT NULL,
    accion VARCHAR(10) NOT NULL COMMENT 'CREAR, GUARDAR o ELIMINAR.',
    resumen VARCHAR(300) NOT NULL,
    creado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    INDEX idx_malla_cambio_centro (centro_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Historial de cambios de la malla por centro.';

UPDATE malla_regla
SET descripcion = 'Cada ola debe lanzarse a más tardar en la hora límite que le toca según su orden en el cluster (07:00 la primera, 15:00 la segunda).'
WHERE codigo = 'OLA_LANZAMIENTO';

INSERT INTO permisos (nombre, descripcion, activo, creado_por, actualizado_por) VALUES
    ('CONFIGURAR_REGLAS_MALLA', 'Permite activar reglas de la malla y cambiar sus umbrales.', TRUE, 1, 1)
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion),
    activo = TRUE;

INSERT IGNORE INTO roles_permisos (rol_id, permiso_id, creado_por)
SELECT r.id, p.id, 0
FROM roles r
JOIN permisos p ON p.nombre = 'CONFIGURAR_REGLAS_MALLA'
WHERE r.nombre = 'ADMINISTRADOR';
