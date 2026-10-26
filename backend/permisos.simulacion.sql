-- Active: 1791863596251@@mysql-hehiachi-mishima-servidor-local.g.aivencloud.com@25590@farmacias_peruanas
-- ============================================================
-- PERMISOS DEL PANEL SIMULACIÓN
--
-- Requisito: `panel.simulacion.sql` aplicado y el rol ADMINISTRADOR creado
-- por `schema.sql`. Es idempotente: se puede ejecutar varias veces.
--
--   mysql -h <host> -P <puerto> -u <usuario> -p farmacias_peruanas \
--     < backend/permisos.simulacion.sql
--
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
