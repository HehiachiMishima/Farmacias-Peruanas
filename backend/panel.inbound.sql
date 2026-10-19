-- Active: 1791863596251@@mysql-hehiachi-mishima-servidor-local.g.aivencloud.com@25590@farmacias_peruanas

-- ============================================================
-- Catálogo de proveedores
-- ============================================================
CREATE TABLE IF NOT EXISTS data_proveedor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Identificador único.',
    proveedor VARCHAR(100) NOT NULL COMMENT 'Nombre o razón social del proveedor.',
    creado_por BIGINT NOT NULL COMMENT 'FK al usuario que creó. 0 = sistema/seeding.',
    actualizado_por BIGINT NOT NULL COMMENT 'FK al usuario que modificó. 0 = sistema/seeding.',
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación.',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora exacta de creación (referencia temporal).',
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Fecha y hora de la última modificación.',
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
