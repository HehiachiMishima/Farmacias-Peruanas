-- Active: 1791152181635@@127.0.0.1@3306@farmacias_peruanas
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    hora_inicio TIME NOT NULL DEFAULT(CURRENT_TIME) COMMENT 'Hora de inicio (auto al insertar).',
    hora_fin TIME NULL COMMENT 'Hora de cierre. NULL mientras la incidencia esté abierta.',
    tiempo_solucion TIME NULL COMMENT 'Tiempo total de solución (HH:MM:SS). Lo calcula el backend.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó la incidencia. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó la incidencia. 0 = sistema/seeding.',
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    data_sistema (
        sistema,
        creado_por,
        actualizado_por
    )
VALUES ('IR-TRACK', 1, 1),
    ('KISOFT', 1, 1),
    ('PMM', 1, 1),
    ('SGT', 1, 1),
    ('AMR', 1, 1),
    ('AFRAME', 1, 1),
    ('PSYS', 1, 1),
    ('IP6', 1, 1),
    ('WMS', 1, 1)
ON DUPLICATE KEY UPDATE
    actualizado_por = VALUES(actualizado_por);

INSERT INTO
    data_incidencia (
        id_data_sistema,
        incidencia,
        creado_por,
        actualizado_por
    )
VALUES (
        1,
        'Error en Liberación',
        1,
        1
    ),
    (1, 'Falla en interfaz', 1, 1),
    (1, 'Interfaz WMS', 1, 1),
    (
        1,
        'Diferencia en unidades',
        1,
        1
    ),
    (2, 'Falla de Servidor', 1, 1),
    (3, 'Falla en interfaz', 1, 1),
    (3, 'Interfaz WMS', 1, 1),
    (3, 'Base de Datos', 1, 1),
    (
        3,
        'No se visualiza la OD del día en PMM',
        1,
        1
    ),
    (
        3,
        'Orden que no migra a WMS',
        1,
        1
    ),
    (
        3,
        'Orden que no migra a WMS',
        1,
        1
    ),
    (
        4,
        'Falla Funcionamiento',
        1,
        1
    ),
    (5, 'Base de Datos', 1, 1),
    (6, 'Falla de Servidor', 1, 1),
    (
        6,
        'Lentitud en la migracion a Ip6',
        1,
        1
    ),
    (7, 'Interfaz WMS', 1, 1),
    (
        7,
        'Caida del microservicio',
        1,
        1
    ),
    (8, 'Falla de Servidor', 1, 1),
    (
        8,
        'Falla Funcionamiento',
        1,
        1
    ),
    (8, 'Falla en interfaz', 1, 1),
    (
        8,
        'Caida del servidor 9',
        1,
        1
    ),
    (
        8,
        'No reserva de cubetas de prepicking',
        1,
        1
    ),
    (
        9,
        'No migración de pedidos B2C a WMS',
        1,
        1
    ),
    (9, 'Interfaz WMS', 1, 1),
    (
        9,
        'Orden que no migra a WMS',
        1,
        1
    ),
    (
        9,
        'Traslado (TSC) no migra a WMS',
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    actualizado_por = VALUES(actualizado_por);

INSERT INTO
    data_area (
        area,
        creado_por,
        actualizado_por
    )
VALUES ('OPERACIONES', 1, 1),
    ('LOGÍSTICA', 1, 1),
    ('TI', 1, 1),
    ('MANTENIMIENTO', 1, 1),
    ('CALIDAD', 1, 1),
    ('ADMINISTRACIÓN', 1, 1),
    ('COMERCIAL', 1, 1),
    ('RRHH', 1, 1),
    ('FINANZAS', 1, 1),
    ('COMPRAS', 1, 1),
    ('ALMACÉN', 1, 1),
    ('TRANSPORTE', 1, 1),
    ('PRODUCCIÓN', 1, 1),
    ('SEGURIDAD', 1, 1),
    ('LEGAL', 1, 1),
    ('MARKETING', 1, 1),
    ('ATENCIÓN AL CLIENTE', 1, 1),
    ('PLANIFICACIÓN', 1, 1),
    ('INVENTARIO', 1, 1),
    ('SERVICIOS GENERALES', 1, 1)
ON DUPLICATE KEY UPDATE
    actualizado_por = VALUES(actualizado_por);

INSERT INTO
    data_responsable (
        id_data_area,
        responsable,
        creado_por,
        actualizado_por
    )
VALUES (1, 'CD PUNTA NEGRA', 1, 1),
    (1, 'CD LURÍN', 1, 1),
    (1, 'CD CALLAO', 1, 1),
    (1, 'CD ATE', 1, 1),
    (
        1,
        'CD VILLA EL SALVADOR',
        1,
        1
    ),
    (1, 'CD CHICLAYO', 1, 1),
    (1, 'CD TRUJILLO', 1, 1),
    (1, 'CD AREQUIPA', 1, 1),
    (
        2,
        'TRANSPORTE PRIMARIO',
        1,
        1
    ),
    (
        2,
        'TRANSPORTE SECUNDARIO',
        1,
        1
    ),
    (2, 'ALMACÉN CENTRAL', 1, 1),
    (
        2,
        'DISTRIBUCIÓN URBANA',
        1,
        1
    ),
    (3, 'INFRAESTRUCTURA', 1, 1),
    (3, 'DESARROLLO', 1, 1),
    (3, 'SOPORTE NIVEL 1', 1, 1),
    (3, 'SOPORTE NIVEL 2', 1, 1),
    (3, 'BASE DE DATOS', 1, 1),
    (
        3,
        'REDES Y COMUNICACIONES',
        1,
        1
    ),
    (3, 'CIBERSEGURIDAD', 1, 1),
    (4, 'MECÁNICA', 1, 1),
    (4, 'ELÉCTRICA', 1, 1),
    (4, 'ELECTRÓNICA', 1, 1),
    (4, 'REFRIGERACIÓN', 1, 1),
    (5, 'AUDITORÍA INTERNA', 1, 1),
    (
        5,
        'CONTROL DE PROCESOS',
        1,
        1
    ),
    (5, 'CERTIFICACIONES', 1, 1),
    (6, 'FINANZAS', 1, 1),
    (6, 'CONTABILIDAD', 1, 1),
    (6, 'TESORERÍA', 1, 1),
    (7, 'VENTAS MAYORISTAS', 1, 1),
    (7, 'VENTAS MINORISTAS', 1, 1),
    (7, 'E-COMMERCE', 1, 1),
    (7, 'KEY ACCOUNTS', 1, 1),
    (8, 'RECLUTAMIENTO', 1, 1),
    (8, 'BIENESTAR SOCIAL', 1, 1),
    (8, 'CAPACITACIÓN', 1, 1),
    (8, 'NÓMINA', 1, 1),
    (9, 'CUENTAS POR PAGAR', 1, 1),
    (9, 'CUENTAS POR COBRAR', 1, 1),
    (9, 'PRESUPUESTOS', 1, 1),
    (
        10,
        'COMPRAS NACIONALES',
        1,
        1
    ),
    (
        10,
        'COMPRAS INTERNACIONALES',
        1,
        1
    ),
    (10, 'PROVEEDORES', 1, 1),
    (11, 'RECEPCIÓN', 1, 1),
    (11, 'PICKING', 1, 1),
    (11, 'PACKING', 1, 1),
    (11, 'DESPACHO', 1, 1),
    (11, 'DEVOLUCIONES', 1, 1),
    (12, 'FLOTA PROPIA', 1, 1),
    (12, 'TERCEROS', 1, 1),
    (12, 'ÚLTIMA MILLA', 1, 1),
    (13, 'LÍNEA 1', 1, 1),
    (13, 'LÍNEA 2', 1, 1),
    (13, 'LÍNEA 3', 1, 1),
    (
        13,
        'CONTROL DE CALIDAD',
        1,
        1
    ),
    (14, 'VIGILANCIA', 1, 1),
    (14, 'SALUD OCUPACIONAL', 1, 1),
    (15, 'CONTRATOS', 1, 1),
    (15, 'LITIGIOS', 1, 1),
    (16, 'DIGITAL', 1, 1),
    (16, 'TRADICIONAL', 1, 1),
    (16, 'BRANDING', 1, 1),
    (17, 'CALL CENTER', 1, 1),
    (17, 'POST VENTA', 1, 1),
    (17, 'RECLAMOS', 1, 1),
    (18, 'DEMANDA', 1, 1),
    (18, 'S&OP', 1, 1),
    (19, 'CONTEO CÍCLICO', 1, 1),
    (19, 'AJUSTES', 1, 1),
    (20, 'LIMPIEZA', 1, 1),
    (
        20,
        'MANTENIMIENTO DE INSTALACIONES',
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    actualizado_por = VALUES(actualizado_por);

INSERT INTO incidencias (
    id_data_centro, id_data_sistema, id_data_incidencia, ticket, id_data_responsable, id_data_area, descripcion, 
    hora_inicio, hora_fin, tiempo_solucion, creado_por, actualizado_por, creado_en, actualizado_en
) VALUES 
(1, 1, 2, 'TK-2026-4001', 14, 3, 'Error al cargar la vista de liberacion de ordenes', '13:00:00', '13:45:00', '05:45:00', 1, 1, '2026-01-02 13:00:00.000001', '2026-01-02 13:00:00.000001'),
(2, 8, 19, 'TK-2026-4002', 45, 11, 'Terminal de radiofrecuencia no responde en picking', '19:30:00', '20:00:00', '05:30:00', 1, 1, '2026-01-04 19:30:00.000002', '2026-01-04 19:30:00.000002'),
(3, 9, 22, 'TK-2026-4003', 10, 2, 'Pedidos web B2C no se reflejan en el WMS', '03:45:00', '06:15:00', '07:30:00', 1, 1, '2026-01-07 03:45:00.000003', '2026-01-07 03:45:00.000003'),
(1, 3, 8, 'TK-2026-4004', 17, 3, 'Base de datos PMM con alta latencia', '14:15:00', '15:15:00', '06:00:00', 1, 1, '2026-01-09 14:15:00.000004', '2026-01-09 14:15:00.000004'),
(2, 5, 12, 'TK-2026-4005', 22, 4, 'Robot AMR detenido por fallo en sensor', '22:10:00', '23:40:00', '06:30:00', 1, 1, '2026-01-12 22:10:00.000005', '2026-01-12 22:10:00.000005'),
(3, 6, 14, 'TK-2026-4006', 29, 6, 'Lentitud extrema en migracion a IP6', '01:20:00', '02:50:00', '06:30:00', 1, 1, '2026-01-15 01:20:00.000006', '2026-01-15 01:20:00.000006'),
(1, 2, 5, 'TK-2026-4007', 16, 3, 'Servidor principal de KISOFT caido', '15:45:00', '16:10:00', '05:25:00', 1, 1, '2026-01-18 15:45:00.000007', '2026-01-18 15:45:00.000007'),
(2, 7, 15, 'TK-2026-4008', 11, 2, 'Interfaz WMS en PSYS no procesa despachos', '20:30:00', '22:30:00', '07:00:00', 1, 1, '2026-01-20 20:30:00.000008', '2026-01-20 20:30:00.000008'),
(3, 4, 11, 'TK-2026-4009', 55, 13, 'Falla de funcionamiento en balanza SGT', '04:15:00', '04:45:00', '05:30:00', 1, 1, '2026-01-23 04:15:00.000009', '2026-01-23 04:15:00.000009'),
(1, 1, 4, 'TK-2026-4010', 5, 1, 'Diferencia de unidades reportadas en IR-TRACK', '15:00:00', '15:20:00', '05:20:00', 1, 1, '2026-01-25 15:00:00.000010', '2026-01-25 15:00:00.000010'),
(2, 8, 18, 'TK-2026-4011', 68, 19, 'Falla general de funcionamiento en IP6', '13:45:00', '14:30:00', '05:45:00', 1, 1, '2026-01-28 13:45:00.000011', '2026-01-28 13:45:00.000011'),
(3, 3, 9, 'TK-2026-4012', 65, 17, 'OD del dia no se visualiza en PMM', '19:00:00', '20:15:00', '06:15:00', 1, 1, '2026-01-31 19:00:00.000012', '2026-01-31 19:00:00.000012'),
(1, 9, 25, 'TK-2026-4013', 50, 12, 'Traslado TSC rechazado por WMS', '22:00:00', '22:30:00', '05:30:00', 1, 1, '2026-02-03 22:00:00.000013', '2026-02-03 22:00:00.000013'),
(2, 7, 16, 'TK-2026-4014', 23, 4, 'Caida del microservicio de pagos PSYS', '06:30:00', '08:00:00', '06:30:00', 1, 1, '2026-02-06 06:30:00.000014', '2026-02-06 06:30:00.000014'),
(3, 3, 10, 'TK-2026-4015', 42, 10, 'Orden atascada, no migra a WMS desde PMM', '16:15:00', '17:00:00', '05:45:00', 1, 1, '2026-02-09 16:15:00.000015', '2026-02-09 16:15:00.000015'),
(1, 6, 13, 'TK-2026-4016', 19, 3, 'Falla de servidor en AFRAME', '02:00:00', '04:30:00', '07:30:00', 1, 1, '2026-02-12 02:00:00.000016', '2026-02-12 02:00:00.000016'),
(2, 1, 1, 'TK-2026-4017', 30, 7, 'Error en liberacion automatica IR-TRACK', '14:10:00', '14:40:00', '05:30:00', 1, 1, '2026-02-15 14:10:00.000017', '2026-02-15 14:10:00.000017'),
(3, 8, 21, 'TK-2026-4018', 47, 11, 'No reserva de cubetas de prepicking en IP6', '20:45:00', '21:30:00', '05:45:00', 1, 1, '2026-02-18 20:45:00.000018', '2026-02-18 20:45:00.000018'),
(1, 5, 12, 'TK-2026-4019', 21, 4, 'Base de datos AMR bloqueada', '23:15:00', '23:59:00', '05:44:00', 1, 1, '2026-02-21 23:15:00.000019', '2026-02-21 23:15:00.000019'),
(2, 2, 5, 'TK-2026-4020', 7, 1, 'KISOFT no responde a peticiones', '03:45:00', '05:15:00', '06:30:00', 1, 1, '2026-02-24 03:45:00.000020', '2026-02-24 03:45:00.000020'),
(3, 7, 16, 'TK-2026-4021', 13, 3, 'Interfaz WMS en PSYS con errores', '15:30:00', '16:45:00', '06:15:00', 1, 1, '2026-02-27 15:30:00.000021', '2026-02-27 15:30:00.000021'),
(1, 1, 3, 'TK-2026-4022', 4, 1, 'Falla de interfaz WMS en IR-TRACK', '19:40:00', '20:10:00', '05:30:00', 1, 1, '2026-03-02 19:40:00.000022', '2026-03-02 19:40:00.000022'),
(2, 6, 13, 'TK-2026-4023', 28, 6, 'Falla de servidor AFRAME', '01:30:00', '02:00:00', '05:30:00', 1, 1, '2026-03-05 01:30:00.000023', '2026-03-05 01:30:00.000023'),
(3, 9, 23, 'TK-2026-4024', 46, 11, 'Interfaz WMS caida en despacho', '13:20:00', '14:05:00', '05:45:00', 1, 1, '2026-03-08 13:20:00.000024', '2026-03-08 13:20:00.000024'),
(1, 3, 7, 'TK-2026-4025', 18, 3, 'Interfaz WMS en PMM no responde', '17:50:00', '19:20:00', '06:30:00', 1, 1, '2026-03-11 17:50:00.000025', '2026-03-11 17:50:00.000025');



