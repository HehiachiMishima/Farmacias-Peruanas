-- Active: 1791152181635@@127.0.0.1@3306@farmacias_peruanas
-- ============================================================
-- Catálogos / desplegables
-- ============================================================

CREATE TABLE IF NOT EXISTS data_centro (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    centro VARCHAR(50) NOT NULL COMMENT 'Nombre de la sede. Ej: CD PUNTA NEGRA.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
    CONSTRAINT uq_data_centro_centro UNIQUE (centro)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de centros.';

CREATE TABLE IF NOT EXISTS data_sistema (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    sistema VARCHAR(50) NOT NULL COMMENT 'Sistema donde ocurrió la incidencia.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
    CONSTRAINT uq_data_sistema_sistema UNIQUE (sistema)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de sistemas.';

-- ------------------------------------------------------------
-- IMPORTANTE: data_area debe crearse ANTES de data_responsable
-- porque data_responsable tiene FK hacia data_area.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    area VARCHAR(50) NOT NULL COMMENT 'Nombre del área. Ej: OPERACIONES, LOGÍSTICA.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
    CONSTRAINT uq_data_area_area UNIQUE (area)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de áreas.';

CREATE TABLE IF NOT EXISTS data_responsable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    id_data_area BIGINT NOT NULL COMMENT 'FK al área a la que pertenece el responsable.',
    responsable VARCHAR(50) NOT NULL COMMENT 'Nombre del responsable. Ej: CD PUNTA NEGRA.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
    CONSTRAINT uq_data_responsable_responsable UNIQUE (responsable),
    -- Llave compuesta: permite que tablas dependientes referencien (responsable, área)
    -- y garanticen que el par es coherente.
    CONSTRAINT uq_data_responsable_id_area UNIQUE (id, id_data_area),
    CONSTRAINT fk_data_responsable_area FOREIGN KEY (id_data_area) REFERENCES data_area (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Catálogo de responsables por área.';

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
    CONSTRAINT fk_data_incidencia_sistema FOREIGN KEY (id_data_sistema) REFERENCES data_sistema (id) ON DELETE RESTRICT ON UPDATE CASCADE
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