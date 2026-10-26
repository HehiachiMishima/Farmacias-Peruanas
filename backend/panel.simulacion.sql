
-- ============================================================
-- TABLAS DEL PANEL SIMULACIÓN
--
-- Requisito: `schema.sql` aplicado (usuarios y RBAC). Es re-ejecutable:
-- usa CREATE TABLE IF NOT EXISTS. Los permisos del panel se crean aparte
-- con `permisos.simulacion.sql`.
-- ============================================================
USE farmacias_peruanas;

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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
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
    creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_historial_archivo FOREIGN KEY (archivo_id) REFERENCES archivos_excel (id) ON DELETE CASCADE,
    CONSTRAINT fk_historial_ruta FOREIGN KEY (ruta_id) REFERENCES rutas_proyeccion (id) ON DELETE SET NULL,
    CONSTRAINT fk_historial_origen FOREIGN KEY (chute_origen_id) REFERENCES chutes (id),
    CONSTRAINT fk_historial_destino FOREIGN KEY (chute_destino_id) REFERENCES chutes (id),
    CONSTRAINT fk_historial_modificado FOREIGN KEY (chute_modificado_id) REFERENCES chutes (id),
    CONSTRAINT fk_historial_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios (id),
    INDEX idx_historial_archivo_fecha (archivo_id, creado_en DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Registro de movimientos realizados por el botón de balanceo.';