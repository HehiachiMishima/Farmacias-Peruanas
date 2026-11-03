-- ============================================================
-- PANEL MALLA OPERATIVA · esquema + datos capturados
-- ============================================================
-- Requiere la base creada por backend/instalacion.sql (RBAC, roles y
-- permisos). Ejecutar después de la instalación:
--
--   mysql -h <host> -P <puerto> -u <usuario> -p farmacias_peruanas \
--     < backend/panel.malla.sql
--
-- Contenido:
--   1) Tablas normalizadas de la malla (centros, turnos, flujos, olas,
--      zonas, capacidad y dotación).
--   2) Datos capturados de Punta Negra (9040) y Santa Anita (9011).
--   3) Permisos del panel y asignación al rol ADMINISTRADOR.
--   4) Historial de cambios (malla_cambio).
--
-- Es reejecutable: crea lo que falte y siembra PN y STA sólo si esos
-- centros todavía no existen, así no pisa lo editado desde Configuración
-- ni los parámetros de las reglas. Bases ya instaladas con una versión
-- anterior: aplicar antes backend/migracion_malla_historial.sql.
-- ============================================================

USE farmacias_peruanas;
SET NAMES utf8mb4;

-- ############################################################
-- 1. Tablas de la Malla Operativa
-- ############################################################

CREATE TABLE IF NOT EXISTS malla_centro (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador interno de la malla.',
    codigo VARCHAR(20) NOT NULL COMMENT 'Código del centro de distribución (9040, 9011).',
    nombre VARCHAR(80) NOT NULL COMMENT 'Nombre del centro de distribución.',
    centro_data_id BIGINT NULL COMMENT 'Referencia lógica a data_centro.id (sin FK: data.sql es opcional).',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Fecha de creación (UTC).',
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)) COMMENT 'Última modificación (UTC).',
    CONSTRAINT uq_malla_centro_codigo UNIQUE (codigo),
    INDEX idx_malla_centro_activo (activo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Centros de distribución de la malla operativa.';

CREATE TABLE IF NOT EXISTS malla_turno (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    centro_id BIGINT NOT NULL,
    codigo VARCHAR(8) NOT NULL COMMENT 'T1, T2, T3.',
    nombre VARCHAR(40) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    hora_refrigerio TIME NULL,
    tipo_refrigerio VARCHAR(24) NULL COMMENT 'Almuerzo, Cena, ...',
    nota VARCHAR(200) NULL,
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_malla_turno UNIQUE (centro_id, codigo),
    CONSTRAINT fk_malla_turno_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Turnos por centro de distribución.';

CREATE TABLE IF NOT EXISTS malla_flujo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    centro_id BIGINT NOT NULL,
    proceso ENUM('REC', 'ALM', 'REA', 'OLA', 'DES') NOT NULL COMMENT 'Recepción, almacenamiento, reabastecimiento, picking, despacho.',
    orden SMALLINT NOT NULL COMMENT 'Orden del paso dentro del proceso.',
    responsable ENUM('IL', 'OPS') NOT NULL COMMENT 'Ingeniería Logística u Operaciones.',
    hora_inicio TIME NOT NULL,
    hora_fin TIME NULL,
    actividad VARCHAR(200) NOT NULL,
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_malla_flujo UNIQUE (centro_id, proceso, orden),
    INDEX idx_malla_flujo_proceso (centro_id, proceso),
    CONSTRAINT fk_malla_flujo_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Flujo de procesos (IL/OPS) por centro.';

CREATE TABLE IF NOT EXISTS malla_ola (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    centro_id BIGINT NOT NULL,
    cluster VARCHAR(10) NOT NULL,
    nombre VARCHAR(20) NOT NULL COMMENT 'Ola 1, Ola 2, ...',
    caida_pedidos TIME NULL,
    lanzamiento TIME NULL,
    cierre_picking TIME NULL,
    despacho TIME NULL,
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    nota VARCHAR(320) NULL,
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_malla_ola UNIQUE (centro_id, cluster, nombre),
    INDEX idx_malla_ola_cluster (centro_id, cluster),
    CONSTRAINT fk_malla_ola_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Olas de picking por cluster y centro.';

CREATE TABLE IF NOT EXISTS malla_zona (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    centro_id BIGINT NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    lph_real INT NULL COMMENT 'Líneas por hora medidas.',
    lph_diseno INT NULL COMMENT 'Líneas por hora de diseño.',
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_malla_zona UNIQUE (centro_id, nombre),
    CONSTRAINT fk_malla_zona_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Zonas de almacenamiento y su productividad.';

CREATE TABLE IF NOT EXISTS malla_capacidad (
    centro_id BIGINT PRIMARY KEY,
    unidades_dia INT NULL,
    lineas_dia INT NULL,
    bultos_recepcion_dia INT NULL,
    proveedores_dia INT NULL,
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_malla_capacidad_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Capacidad diaria declarada por centro.';

CREATE TABLE IF NOT EXISTS malla_dotacion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    centro_id BIGINT NOT NULL,
    turno_id BIGINT NOT NULL,
    proceso ENUM('REC', 'ALM', 'REA', 'OLA', 'DES') NOT NULL,
    personas INT NULL COMMENT 'Dotación planificada; NULL = por definir.',
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_malla_dotacion UNIQUE (centro_id, turno_id, proceso),
    CONSTRAINT fk_malla_dotacion_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE,
    CONSTRAINT fk_malla_dotacion_turno FOREIGN KEY (turno_id) REFERENCES malla_turno (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Dotación planificada por proceso y turno.';

CREATE TABLE IF NOT EXISTS malla_dotacion_real (
    centro_id BIGINT PRIMARY KEY,
    inbound INT NULL,
    outbound INT NULL,
    despacho INT NULL,
    total INT NULL,
    semana VARCHAR(10) NULL COMMENT 'Semana de la captura (S44).',
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT fk_malla_dotacion_real_centro FOREIGN KEY (centro_id) REFERENCES malla_centro (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Personas reales por flujo y centro.';

CREATE TABLE IF NOT EXISTS malla_regla (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL COMMENT 'Código que entiende el evaluador del backend.',
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(400) NOT NULL,
    ambito VARCHAR(20) NOT NULL COMMENT 'OLA, TURNO, FLUJO, ZONA, CAPACIDAD o DOTACION.',
    severidad VARCHAR(10) NOT NULL COMMENT 'ALTA, MEDIA o BAJA.',
    parametros TEXT NOT NULL COMMENT 'Parámetros efectivos en JSON: {"clave":"valor"}.',
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    orden SMALLINT NOT NULL DEFAULT 0,
    creado_por BIGINT NOT NULL DEFAULT 0,
    actualizado_por BIGINT NOT NULL DEFAULT 0,
    creado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    actualizado_en DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT uq_malla_regla_codigo UNIQUE (codigo),
    INDEX idx_malla_regla_orden (activa, orden)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de reglas evaluadas por el módulo Configuración.';

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

-- ############################################################
-- 2. Datos capturados
-- ############################################################

-- Sólo se siembran los centros que aún no existen (por código): si ya
-- están, se conserva lo que se haya editado desde Configuración.
SET @pn_existe := EXISTS (SELECT 1 FROM malla_centro WHERE codigo = '9040');
SET @sta_existe := EXISTS (SELECT 1 FROM malla_centro WHERE codigo = '9011');

-- 2.1 Centros
INSERT INTO malla_centro (codigo, nombre, centro_data_id, activo, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW('9040', 'Punta Negra', 2, TRUE, 1, 1),
    ROW('9011', 'Santa Anita', 1, TRUE, 1, 1)
) AS v (codigo, nombre, centro_data_id, activo, creado_por, actualizado_por)
WHERE NOT EXISTS (SELECT 1 FROM malla_centro c WHERE c.codigo = v.codigo);

-- Id de cada centro recién creado; NULL si ya existía (no se siembra).
SET @pn := (SELECT id FROM malla_centro WHERE codigo = '9040' AND NOT @pn_existe);
SET @sta := (SELECT id FROM malla_centro WHERE codigo = '9011' AND NOT @sta_existe);

-- 2.2 Turnos
INSERT INTO malla_turno (centro_id, codigo, nombre, hora_inicio, hora_fin, hora_refrigerio, tipo_refrigerio, nota, confirmado, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW(@pn, 'T1', 'Turno 1', '07:00', '16:00', '13:00', 'Almuerzo', 'Traslape con T2 desde las 13:00', TRUE, 1, 1),
    ROW(@pn, 'T2', 'Turno 2', '13:00', '22:00', '19:00', 'Cena', NULL, TRUE, 1, 1),
    ROW(@pn, 'T3', 'Turno 3', '22:00', '06:00', '01:00', 'Cena', NULL, TRUE, 1, 1),
    ROW(@sta, 'T1', 'Turno 1', '07:00', '16:00', '13:00', 'Almuerzo', 'Traslape con T2 desde las 13:00', TRUE, 1, 1),
    ROW(@sta, 'T2', 'Turno 2', '13:00', '22:00', '19:00', 'Cena', NULL, TRUE, 1, 1),
    ROW(@sta, 'T3', 'Turno 3', '22:00', '06:00', '01:00', 'Cena', NULL, TRUE, 1, 1)
) AS v (centro_id, codigo, nombre, hora_inicio, hora_fin, hora_refrigerio, tipo_refrigerio, nota, confirmado, creado_por, actualizado_por)
WHERE v.centro_id IS NOT NULL;

-- 2.3 Flujos de proceso
INSERT INTO malla_flujo (centro_id, proceso, orden, responsable, hora_inicio, hora_fin, actividad, confirmado, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW(@pn, 'REC', 1, 'IL', '06:30', NULL, 'Validar citas y ASN del día', FALSE, 1, 1),
    ROW(@pn, 'REC', 2, 'OPS', '07:00', '17:00', 'Descarga y verificación', FALSE, 1, 1),
    ROW(@pn, 'REC', 3, 'OPS', '07:00', '18:00', 'Registro de recepción en WMS', FALSE, 1, 1),
    ROW(@pn, 'REC', 4, 'IL', '12:00', NULL, 'Seguimiento de recepciones pendientes', FALSE, 1, 1),
    ROW(@pn, 'REC', 5, 'OPS', '18:00', NULL, 'Liberar LPN a almacenamiento', FALSE, 1, 1),
    ROW(@pn, 'ALM', 1, 'IL', '07:00', NULL, 'Generar tareas de ubicación', FALSE, 1, 1),
    ROW(@pn, 'ALM', 2, 'OPS', '07:00', '22:00', 'Ubicar LPN en reserva', FALSE, 1, 1),
    ROW(@pn, 'ALM', 3, 'IL', '20:00', NULL, 'Proyección de cierre de almacenamiento', FALSE, 1, 1),
    ROW(@pn, 'ALM', 4, 'OPS', '22:00', NULL, 'Cierre de almacenamiento', FALSE, 1, 1),
    ROW(@pn, 'REA', 1, 'IL', '12:30', NULL, 'Correr reabastecimiento entre olas', FALSE, 1, 1),
    ROW(@pn, 'REA', 2, 'OPS', '13:00', '15:00', 'Ejecutar reabastecimiento entre olas', FALSE, 1, 1),
    ROW(@pn, 'REA', 3, 'IL', '21:30', NULL, 'Correr olas de reabastecimiento nocturno', FALSE, 1, 1),
    ROW(@pn, 'REA', 4, 'OPS', '22:00', '05:30', 'Ejecutar reabastecimiento nocturno', FALSE, 1, 1),
    ROW(@pn, 'REA', 5, 'IL', '05:30', NULL, 'Validar ubicaciones de picking completas', FALSE, 1, 1),
    ROW(@pn, 'OLA', 1, 'IL', '05:00', NULL, 'Validar caída de pedidos por cluster', FALSE, 1, 1),
    ROW(@pn, 'OLA', 2, 'IL', '07:00', NULL, 'Lanzar olas · ola 1', FALSE, 1, 1),
    ROW(@pn, 'OLA', 3, 'OPS', '07:00', '13:00', 'Picking ola 1', FALSE, 1, 1),
    ROW(@pn, 'OLA', 4, 'OPS', '13:00', NULL, 'Cierre de picking ola 1', FALSE, 1, 1),
    ROW(@pn, 'OLA', 5, 'IL', '15:00', NULL, 'Lanzar olas · ola 2', FALSE, 1, 1),
    ROW(@pn, 'OLA', 6, 'OPS', '15:00', '21:00', 'Picking ola 2', FALSE, 1, 1),
    ROW(@pn, 'OLA', 7, 'OPS', '21:00', NULL, 'Cierre de picking ola 2', FALSE, 1, 1),
    ROW(@pn, 'DES', 1, 'OPS', '13:00', '15:00', 'Consolidar y cerrar bultos ola 1', FALSE, 1, 1),
    ROW(@pn, 'DES', 2, 'IL', '14:30', NULL, 'Validar bultos cerrados en WMS', FALSE, 1, 1),
    ROW(@pn, 'DES', 3, 'OPS', '15:00', NULL, 'Despacho ola 1', FALSE, 1, 1),
    ROW(@pn, 'DES', 4, 'OPS', '21:00', '23:00', 'Consolidar y cerrar bultos ola 2', FALSE, 1, 1),
    ROW(@pn, 'DES', 5, 'OPS', '23:00', NULL, 'Despacho ola 2', FALSE, 1, 1),
    ROW(@sta, 'REC', 1, 'IL', '06:30', NULL, 'Validar citas y ASN del día', FALSE, 1, 1),
    ROW(@sta, 'REC', 2, 'OPS', '07:00', '17:00', 'Descarga y verificación', FALSE, 1, 1),
    ROW(@sta, 'REC', 3, 'OPS', '07:00', '18:00', 'Registro de recepción en WMS', FALSE, 1, 1),
    ROW(@sta, 'REC', 4, 'IL', '12:00', NULL, 'Seguimiento de recepciones pendientes', FALSE, 1, 1),
    ROW(@sta, 'REC', 5, 'OPS', '18:00', NULL, 'Liberar LPN a almacenamiento', FALSE, 1, 1),
    ROW(@sta, 'ALM', 1, 'IL', '07:00', NULL, 'Generar tareas de ubicación', FALSE, 1, 1),
    ROW(@sta, 'ALM', 2, 'OPS', '07:00', '22:00', 'Ubicar LPN en reserva', FALSE, 1, 1),
    ROW(@sta, 'ALM', 3, 'IL', '20:00', NULL, 'Proyección de cierre de almacenamiento', FALSE, 1, 1),
    ROW(@sta, 'ALM', 4, 'OPS', '22:00', NULL, 'Cierre de almacenamiento', FALSE, 1, 1),
    ROW(@sta, 'REA', 1, 'IL', '12:30', NULL, 'Correr reabastecimiento entre olas', FALSE, 1, 1),
    ROW(@sta, 'REA', 2, 'OPS', '13:00', '15:00', 'Ejecutar reabastecimiento entre olas', FALSE, 1, 1),
    ROW(@sta, 'REA', 3, 'IL', '21:30', NULL, 'Correr olas de reabastecimiento nocturno', FALSE, 1, 1),
    ROW(@sta, 'REA', 4, 'OPS', '22:00', '05:30', 'Ejecutar reabastecimiento nocturno', FALSE, 1, 1),
    ROW(@sta, 'REA', 5, 'IL', '05:30', NULL, 'Validar ubicaciones de picking completas', FALSE, 1, 1),
    ROW(@sta, 'OLA', 1, 'IL', '05:00', NULL, 'Validar caída de pedidos por cluster', FALSE, 1, 1),
    ROW(@sta, 'OLA', 2, 'IL', '07:00', NULL, 'Lanzar olas · ola 1', FALSE, 1, 1),
    ROW(@sta, 'OLA', 3, 'OPS', '07:00', '13:00', 'Picking ola 1', FALSE, 1, 1),
    ROW(@sta, 'OLA', 4, 'OPS', '13:00', NULL, 'Cierre de picking ola 1', FALSE, 1, 1),
    ROW(@sta, 'OLA', 5, 'IL', '15:00', NULL, 'Lanzar olas · ola 2', FALSE, 1, 1),
    ROW(@sta, 'OLA', 6, 'OPS', '15:00', '21:00', 'Picking ola 2', FALSE, 1, 1),
    ROW(@sta, 'OLA', 7, 'OPS', '21:00', NULL, 'Cierre de picking ola 2', FALSE, 1, 1),
    ROW(@sta, 'DES', 1, 'OPS', '13:00', '15:00', 'Consolidar y cerrar bultos ola 1', FALSE, 1, 1),
    ROW(@sta, 'DES', 2, 'IL', '14:30', NULL, 'Validar bultos cerrados en WMS', FALSE, 1, 1),
    ROW(@sta, 'DES', 3, 'OPS', '15:00', NULL, 'Despacho ola 1', FALSE, 1, 1),
    ROW(@sta, 'DES', 4, 'OPS', '21:00', '23:00', 'Consolidar y cerrar bultos ola 2', FALSE, 1, 1),
    ROW(@sta, 'DES', 5, 'OPS', '23:00', NULL, 'Despacho ola 2', FALSE, 1, 1)
) AS v (centro_id, proceso, orden, responsable, hora_inicio, hora_fin, actividad, confirmado, creado_por, actualizado_por)
WHERE v.centro_id IS NOT NULL;

-- 2.4 Olas
INSERT INTO malla_ola (centro_id, cluster, nombre, caida_pedidos, lanzamiento, cierre_picking, despacho, confirmado, nota, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW(@pn, '1', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '1', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '2', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '2', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '3', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '3', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '4', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@pn, '4', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '1', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '1', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '2', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '2', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '3', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '3', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '4', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '4', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '5', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '5', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '6', 'Ola 1', '05:00', '07:00', '13:00', '15:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1),
    ROW(@sta, '6', 'Ola 2', '13:00', '15:00', '21:00', '23:00', FALSE, 'Regla de la bitácora (27/07): olas lanzadas como máximo a las 07:00 y 15:00, lo más consolidadas posible.', 1, 1)
) AS v (centro_id, cluster, nombre, caida_pedidos, lanzamiento, cierre_picking, despacho, confirmado, nota, creado_por, actualizado_por)
WHERE v.centro_id IS NOT NULL;

-- 2.5 Zonas
INSERT INTO malla_zona (centro_id, nombre, lph_real, lph_diseno, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW(@pn, 'GTP', 4890, NULL, 1, 1),
    ROW(@pn, 'ODS', 4237, NULL, 1, 1),
    ROW(@pn, 'AAA', 1007, NULL, 1, 1),
    ROW(@pn, 'LAC', 741, NULL, 1, 1),
    ROW(@pn, 'A-Frame', 3787, NULL, 1, 1),
    ROW(@sta, 'Knapp', 1986, NULL, 1, 1),
    ROW(@sta, 'A-Frame', 1768, NULL, 1, 1),
    ROW(@sta, 'Estantería', 1150, NULL, 1, 1),
    ROW(@sta, 'Mezzanine', 789, NULL, 1, 1),
    ROW(@sta, 'Full LPN', 175, NULL, 1, 1)
) AS v (centro_id, nombre, lph_real, lph_diseno, creado_por, actualizado_por)
WHERE v.centro_id IS NOT NULL;

-- 2.6 Capacidad diaria (NULL = por definir)
INSERT INTO malla_capacidad (centro_id, unidades_dia, lineas_dia, bultos_recepcion_dia, proveedores_dia, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW(@pn, NULL, NULL, NULL, NULL, 1, 1),
    ROW(@sta, NULL, NULL, NULL, NULL, 1, 1)
) AS v (centro_id, unidades_dia, lineas_dia, bultos_recepcion_dia, proveedores_dia, creado_por, actualizado_por)
WHERE v.centro_id IS NOT NULL;

-- 2.7 Dotación planificada (NULL = por definir)
INSERT INTO malla_dotacion (centro_id, turno_id, proceso, personas, creado_por, actualizado_por)
SELECT t.centro_id, t.id, v.proceso, v.personas, 1, 1
FROM (VALUES
    ROW(@pn, 'T1', 'REC', NULL),
    ROW(@pn, 'T2', 'REC', NULL),
    ROW(@pn, 'T3', 'REC', NULL),
    ROW(@pn, 'T1', 'ALM', NULL),
    ROW(@pn, 'T2', 'ALM', NULL),
    ROW(@pn, 'T3', 'ALM', NULL),
    ROW(@pn, 'T1', 'REA', NULL),
    ROW(@pn, 'T2', 'REA', NULL),
    ROW(@pn, 'T3', 'REA', NULL),
    ROW(@pn, 'T1', 'OLA', NULL),
    ROW(@pn, 'T2', 'OLA', NULL),
    ROW(@pn, 'T3', 'OLA', NULL),
    ROW(@pn, 'T1', 'DES', NULL),
    ROW(@pn, 'T2', 'DES', NULL),
    ROW(@pn, 'T3', 'DES', NULL),
    ROW(@sta, 'T1', 'REC', NULL),
    ROW(@sta, 'T2', 'REC', NULL),
    ROW(@sta, 'T3', 'REC', NULL),
    ROW(@sta, 'T1', 'ALM', NULL),
    ROW(@sta, 'T2', 'ALM', NULL),
    ROW(@sta, 'T3', 'ALM', NULL),
    ROW(@sta, 'T1', 'REA', NULL),
    ROW(@sta, 'T2', 'REA', NULL),
    ROW(@sta, 'T3', 'REA', NULL),
    ROW(@sta, 'T1', 'OLA', NULL),
    ROW(@sta, 'T2', 'OLA', NULL),
    ROW(@sta, 'T3', 'OLA', NULL),
    ROW(@sta, 'T1', 'DES', NULL),
    ROW(@sta, 'T2', 'DES', NULL),
    ROW(@sta, 'T3', 'DES', NULL)
) AS v (centro_id, turno, proceso, personas)
JOIN malla_turno t ON t.centro_id = v.centro_id AND t.codigo = v.turno;

-- 2.8 Dotación real
INSERT INTO malla_dotacion_real (centro_id, inbound, outbound, despacho, total, semana, creado_por, actualizado_por)
SELECT * FROM (VALUES
    ROW(@pn, 28, 378, 59, 464, 'S44', 1, 1),
    ROW(@sta, 43, 162, 22, 227, 'S44', 1, 1)
) AS v (centro_id, inbound, outbound, despacho, total, semana, creado_por, actualizado_por)
WHERE v.centro_id IS NOT NULL;

-- 2.9 Catálogo de reglas del módulo Configuración
-- Los parámetros son los valores por defecto del evaluador. Al reejecutar
-- el script se actualizan nombre, descripción, ámbito, severidad y orden;
-- los parámetros y la activación ajustados desde Configuración se conservan.
INSERT INTO malla_regla (codigo, nombre, descripcion, ambito, severidad, parametros, activa, orden, creado_por, actualizado_por) VALUES
    ('OLA_LANZAMIENTO', 'Lanzamiento máximo de olas', 'Cada ola debe lanzarse a más tardar en la hora límite que le toca según su orden en el cluster (07:00 la primera, 15:00 la segunda).', 'OLA', 'ALTA', '{"limites":"07:00,15:00","toleranciaMin":"0"}', TRUE, 1, 1, 1),
    ('OLA_SECUENCIA', 'Secuencia de hitos de la ola', 'La caída de pedidos debe preceder al lanzamiento, el lanzamiento al cierre de picking y el cierre al despacho.', 'OLA', 'ALTA', '{}', TRUE, 2, 1, 1),
    ('OLA_MAX_PICKING', 'Duración máxima de picking', 'El picking de una ola no puede superar las horas configuradas entre el lanzamiento y el cierre.', 'OLA', 'MEDIA', '{"horasMax":"8"}', TRUE, 3, 1, 1),
    ('OLA_CONFIRMADA', 'Olas confirmadas', 'Todas las olas de cada cluster deben estar confirmadas antes de operar.', 'OLA', 'BAJA', '{}', TRUE, 4, 1, 1),
    ('TURNO_COBERTURA', 'Cobertura diaria de turnos', 'Los turnos de cada centro deben cubrir las 24 horas sin huecos mayores a la tolerancia configurada.', 'TURNO', 'ALTA', '{"minutosGapMax":"0"}', TRUE, 5, 1, 1),
    ('TURNO_REFRIGERIO', 'Refrigerio dentro del turno', 'Cada turno debe tener un refrigerio y debe caer dentro de su ventana horaria.', 'TURNO', 'MEDIA', '{"exigirRefrigerio":"true"}', TRUE, 6, 1, 1),
    ('FLUJO_VENTANA_TURNO', 'Pasos dentro de un turno', 'Los pasos operativos (OPS) deben iniciar y terminar dentro de la ventana de algún turno.', 'FLUJO', 'MEDIA', '{"incluirIL":"false"}', TRUE, 7, 1, 1),
    ('FLUJO_DURACION', 'Duración máxima de un paso', 'Un paso con hora de fin no puede durar más de las horas configuradas.', 'FLUJO', 'MEDIA', '{"horasMax":"12"}', TRUE, 8, 1, 1),
    ('FLUJO_CONFIRMADO', 'Pasos del flujo confirmados', 'Todos los pasos del flujo de procesos deben estar confirmados.', 'FLUJO', 'BAJA', '{}', TRUE, 9, 1, 1),
    ('CAPACIDAD_DECLARADA', 'Capacidad diaria declarada', 'Cada centro debe declarar unidades, líneas, bultos de recepción y proveedores por día.', 'CAPACIDAD', 'ALTA', '{}', TRUE, 10, 1, 1),
    ('DOTACION_PLANIFICADA', 'Dotación planificada por turno', 'Cada proceso debe tener personas asignadas en todos los turnos del centro.', 'DOTACION', 'ALTA', '{}', TRUE, 11, 1, 1),
    ('ZONA_LPH_DISENO', 'LPH de diseño por zona', 'Cada zona de almacenamiento debe tener una productividad de diseño (LPH) definida.', 'ZONA', 'BAJA', '{}', TRUE, 12, 1, 1)
ON DUPLICATE KEY UPDATE
    nombre = VALUES(nombre),
    descripcion = VALUES(descripcion),
    ambito = VALUES(ambito),
    severidad = VALUES(severidad),
    orden = VALUES(orden),
    actualizado_por = VALUES(actualizado_por);

-- ############################################################
-- 3. Permisos del panel Malla Operativa
-- ############################################################
-- El trigger trg_permisos_after_insert concede cada permiso nuevo al rol
-- ADMINISTRADOR. El INSERT IGNORE lo garantiza también si el trigger no
-- está instalado.
INSERT INTO permisos (nombre, descripcion, activo, creado_por, actualizado_por) VALUES
    ('PANEL_MALLA', 'Permite el ingreso al panel Malla Operativa.', TRUE, 1, 1),
    ('VER_MALLA', 'Permite ver el tablero de la malla operativa.', TRUE, 1, 1),
    ('MODIFICAR_MALLA', 'Permite editar turnos, flujos, olas, zonas y capacidad en la malla operativa.', TRUE, 1, 1),
    ('EXPORTAR_MALLA', 'Permite exportar la malla operativa a Excel.', TRUE, 1, 1),
    ('CONFIGURAR_REGLAS_MALLA', 'Permite activar reglas de la malla y cambiar sus umbrales.', TRUE, 1, 1)
ON DUPLICATE KEY UPDATE
    descripcion = VALUES(descripcion),
    activo = TRUE;

INSERT IGNORE INTO roles_permisos (rol_id, permiso_id, creado_por)
SELECT r.id, p.id, 0
FROM roles r
JOIN permisos p ON p.nombre IN ('PANEL_MALLA', 'VER_MALLA', 'MODIFICAR_MALLA', 'EXPORTAR_MALLA',
                               'CONFIGURAR_REGLAS_MALLA')
WHERE r.nombre = 'ADMINISTRADOR';

-- ############################################################
-- 4. Verificación rápida
-- ############################################################
SELECT 'centros' AS tabla, COUNT(*) AS filas FROM malla_centro
UNION ALL SELECT 'turnos', COUNT(*) FROM malla_turno
UNION ALL SELECT 'flujos', COUNT(*) FROM malla_flujo
UNION ALL SELECT 'olas', COUNT(*) FROM malla_ola
UNION ALL SELECT 'zonas', COUNT(*) FROM malla_zona
UNION ALL SELECT 'capacidad', COUNT(*) FROM malla_capacidad
UNION ALL SELECT 'dotacion', COUNT(*) FROM malla_dotacion
UNION ALL SELECT 'dotacion_real', COUNT(*) FROM malla_dotacion_real
UNION ALL SELECT 'reglas', COUNT(*) FROM malla_regla;
