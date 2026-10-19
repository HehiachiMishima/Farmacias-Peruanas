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

INSERT INTO
    incidencias (
        id_data_centro,
        id_data_sistema,
        id_data_incidencia,
        ticket,
        id_data_responsable,
        id_data_area,
        descripcion,
        hora_inicio,
        hora_fin,
        tiempo_solucion,
        creado_por,
        actualizado_por,
        creado_en,
        actualizado_en
    )
VALUES (
        1,
        1,
        2,
        'TK-2026-4001',
        14,
        3,
        'Error al cargar la vista de liberacion de ordenes',
        '13:00:00',
        '13:45:00',
        '05:45:00',
        1,
        1,
        '2026-01-02 13:00:00.000001',
        '2026-01-02 13:00:00.000001'
    ),
    (
        2,
        8,
        19,
        'TK-2026-4002',
        45,
        11,
        'Terminal de radiofrecuencia no responde en picking',
        '19:30:00',
        '20:00:00',
        '05:30:00',
        1,
        1,
        '2026-01-04 19:30:00.000002',
        '2026-01-04 19:30:00.000002'
    ),
    (
        3,
        9,
        22,
        'TK-2026-4003',
        10,
        2,
        'Pedidos web B2C no se reflejan en el WMS',
        '03:45:00',
        '06:15:00',
        '07:30:00',
        1,
        1,
        '2026-01-07 03:45:00.000003',
        '2026-01-07 03:45:00.000003'
    ),
    (
        1,
        3,
        8,
        'TK-2026-4004',
        17,
        3,
        'Base de datos PMM con alta latencia',
        '14:15:00',
        '15:15:00',
        '06:00:00',
        1,
        1,
        '2026-01-09 14:15:00.000004',
        '2026-01-09 14:15:00.000004'
    ),
    (
        2,
        5,
        12,
        'TK-2026-4005',
        22,
        4,
        'Robot AMR detenido por fallo en sensor',
        '22:10:00',
        '23:40:00',
        '06:30:00',
        1,
        1,
        '2026-01-12 22:10:00.000005',
        '2026-01-12 22:10:00.000005'
    ),
    (
        3,
        6,
        14,
        'TK-2026-4006',
        29,
        6,
        'Lentitud extrema en migracion a IP6',
        '01:20:00',
        '02:50:00',
        '06:30:00',
        1,
        1,
        '2026-01-15 01:20:00.000006',
        '2026-01-15 01:20:00.000006'
    ),
    (
        1,
        2,
        5,
        'TK-2026-4007',
        16,
        3,
        'Servidor principal de KISOFT caido',
        '15:45:00',
        '16:10:00',
        '05:25:00',
        1,
        1,
        '2026-01-18 15:45:00.000007',
        '2026-01-18 15:45:00.000007'
    ),
    (
        2,
        7,
        15,
        'TK-2026-4008',
        11,
        2,
        'Interfaz WMS en PSYS no procesa despachos',
        '20:30:00',
        '22:30:00',
        '07:00:00',
        1,
        1,
        '2026-01-20 20:30:00.000008',
        '2026-01-20 20:30:00.000008'
    ),
    (
        3,
        4,
        11,
        'TK-2026-4009',
        55,
        13,
        'Falla de funcionamiento en balanza SGT',
        '04:15:00',
        '04:45:00',
        '05:30:00',
        1,
        1,
        '2026-01-23 04:15:00.000009',
        '2026-01-23 04:15:00.000009'
    ),
    (
        1,
        1,
        4,
        'TK-2026-4010',
        5,
        1,
        'Diferencia de unidades reportadas en IR-TRACK',
        '15:00:00',
        '15:20:00',
        '05:20:00',
        1,
        1,
        '2026-01-25 15:00:00.000010',
        '2026-01-25 15:00:00.000010'
    ),
    (
        2,
        8,
        18,
        'TK-2026-4011',
        68,
        19,
        'Falla general de funcionamiento en IP6',
        '13:45:00',
        '14:30:00',
        '05:45:00',
        1,
        1,
        '2026-01-28 13:45:00.000011',
        '2026-01-28 13:45:00.000011'
    ),
    (
        3,
        3,
        9,
        'TK-2026-4012',
        65,
        17,
        'OD del dia no se visualiza en PMM',
        '19:00:00',
        '20:15:00',
        '06:15:00',
        1,
        1,
        '2026-01-31 19:00:00.000012',
        '2026-01-31 19:00:00.000012'
    ),
    (
        1,
        9,
        25,
        'TK-2026-4013',
        50,
        12,
        'Traslado TSC rechazado por WMS',
        '22:00:00',
        '22:30:00',
        '05:30:00',
        1,
        1,
        '2026-02-03 22:00:00.000013',
        '2026-02-03 22:00:00.000013'
    ),
    (
        2,
        7,
        16,
        'TK-2026-4014',
        23,
        4,
        'Caida del microservicio de pagos PSYS',
        '06:30:00',
        '08:00:00',
        '06:30:00',
        1,
        1,
        '2026-02-06 06:30:00.000014',
        '2026-02-06 06:30:00.000014'
    ),
    (
        3,
        3,
        10,
        'TK-2026-4015',
        42,
        10,
        'Orden atascada, no migra a WMS desde PMM',
        '16:15:00',
        '17:00:00',
        '05:45:00',
        1,
        1,
        '2026-02-09 16:15:00.000015',
        '2026-02-09 16:15:00.000015'
    ),
    (
        1,
        6,
        13,
        'TK-2026-4016',
        19,
        3,
        'Falla de servidor en AFRAME',
        '02:00:00',
        '04:30:00',
        '07:30:00',
        1,
        1,
        '2026-02-12 02:00:00.000016',
        '2026-02-12 02:00:00.000016'
    ),
    (
        2,
        1,
        1,
        'TK-2026-4017',
        30,
        7,
        'Error en liberacion automatica IR-TRACK',
        '14:10:00',
        '14:40:00',
        '05:30:00',
        1,
        1,
        '2026-02-15 14:10:00.000017',
        '2026-02-15 14:10:00.000017'
    ),
    (
        3,
        8,
        21,
        'TK-2026-4018',
        47,
        11,
        'No reserva de cubetas de prepicking en IP6',
        '20:45:00',
        '21:30:00',
        '05:45:00',
        1,
        1,
        '2026-02-18 20:45:00.000018',
        '2026-02-18 20:45:00.000018'
    ),
    (
        1,
        5,
        12,
        'TK-2026-4019',
        21,
        4,
        'Base de datos AMR bloqueada',
        '23:15:00',
        '23:59:00',
        '05:44:00',
        1,
        1,
        '2026-02-21 23:15:00.000019',
        '2026-02-21 23:15:00.000019'
    ),
    (
        2,
        2,
        5,
        'TK-2026-4020',
        7,
        1,
        'KISOFT no responde a peticiones',
        '03:45:00',
        '05:15:00',
        '06:30:00',
        1,
        1,
        '2026-02-24 03:45:00.000020',
        '2026-02-24 03:45:00.000020'
    ),
    (
        3,
        7,
        16,
        'TK-2026-4021',
        13,
        3,
        'Interfaz WMS en PSYS con errores',
        '15:30:00',
        '16:45:00',
        '06:15:00',
        1,
        1,
        '2026-02-27 15:30:00.000021',
        '2026-02-27 15:30:00.000021'
    ),
    (
        1,
        1,
        3,
        'TK-2026-4022',
        4,
        1,
        'Falla de interfaz WMS en IR-TRACK',
        '19:40:00',
        '20:10:00',
        '05:30:00',
        1,
        1,
        '2026-03-02 19:40:00.000022',
        '2026-03-02 19:40:00.000022'
    ),
    (
        2,
        6,
        13,
        'TK-2026-4023',
        28,
        6,
        'Falla de servidor AFRAME',
        '01:30:00',
        '02:00:00',
        '05:30:00',
        1,
        1,
        '2026-03-05 01:30:00.000023',
        '2026-03-05 01:30:00.000023'
    ),
    (
        3,
        9,
        23,
        'TK-2026-4024',
        46,
        11,
        'Interfaz WMS caida en despacho',
        '13:20:00',
        '14:05:00',
        '05:45:00',
        1,
        1,
        '2026-03-08 13:20:00.000024',
        '2026-03-08 13:20:00.000024'
    ),
    (
        1,
        3,
        7,
        'TK-2026-4025',
        18,
        'Interfaz WMS en PMM no responde',
        '17:50:00',
        '19:20:00',
        '06:30:00',
        1,
        1,
        '2026-03-11 17:50:00.000025',
        '2026-03-11 17:50:00.000025'
    );

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