DROP TABLE IF EXISTS data_centro;
DROP TABLE IF EXISTS data_sistema;
DROP TABLE IF EXISTS data_area;
DROP TABLE IF EXISTS data_incidencia;
DROP TABLE IF EXISTS data_responsable;
DROP TABLE IF EXISTS incidencias;
START TRANSACTION;

-- Autor de todos los registros: el usuario con rol ADMINISTRADOR (el
-- semilla que crea el backend al arrancar). Su id no siempre es 1: en
-- Aiven depende de los usuarios que ya existían. Si no hay ninguno, los
-- INSERT fallan (creado_por NOT NULL) y no se guarda nada.
SET @actor = (
    SELECT MIN(ur.usuario_id)
    FROM usuarios_roles ur
    JOIN roles r ON r.id = ur.rol_id
    WHERE r.nombre = 'ADMINISTRADOR'
);

-- ============================================================
-- 1) CENTROS
-- ============================================================
INSERT INTO data_centro (id, centro, creado_por, actualizado_por) VALUES
    (1, 'Santa Anita - CD11', @actor, @actor),
    (2, 'Punta Negra - 9040', @actor, @actor),
    (3, 'CT Norte - 9020',    @actor, @actor);

-- ============================================================
-- 2) SISTEMAS
-- ============================================================
INSERT INTO data_sistema (id, sistema, creado_por, actualizado_por) VALUES
    (1, 'IR-TRACK', @actor, @actor),
    (2, 'KISOFT',   @actor, @actor),
    (3, 'PMM',      @actor, @actor),
    (4, 'SGT',      @actor, @actor),
    (5, 'AMR',      @actor, @actor),
    (6, 'AFRAME',   @actor, @actor),
    (7, 'PSYS',     @actor, @actor),
    (8, 'IP6',      @actor, @actor),
    (9, 'WMS',      @actor, @actor);

-- ============================================================
-- 3) ÁREAS
-- ============================================================
INSERT INTO data_area (id, area, creado_por, actualizado_por) VALUES
    (1, 'Aplicaciones', @actor, @actor),
    (2, 'Vanderlande',  @actor, @actor),
    (3, 'TI',           @actor, @actor),
    (4, 'Schaefer',     @actor, @actor),
    (5, 'IP6',          @actor, @actor),
    (6, 'Corporativo',  @actor, @actor);

-- ============================================================
-- 4) INCIDENCIAS (Catálogo)
-- ============================================================
INSERT INTO data_incidencia (id, id_data_sistema, incidencia, creado_por, actualizado_por) VALUES
    ( 1, 1, 'Error en Liberación',                   @actor, @actor),
    ( 2, 2, 'Falla de Servidor',                     @actor, @actor),
    ( 3, 1, 'Falla en interfaz',                     @actor, @actor),
    ( 4, 3, 'Interfaz WMS',                          @actor, @actor),
    ( 5, 4, 'Falla Funcionamiento',                  @actor, @actor),
    ( 6, 5, 'Base de Datos',                         @actor, @actor),
    ( 7, 8, 'Falla en interfaz',                     @actor, @actor),
    ( 8, 6, 'Lentitud en la migración a IP6',        @actor, @actor),
    ( 9, 7, 'Caída del microservicio',               @actor, @actor),
    (10, 3, 'No se visualiza la OD del día en PMM',  @actor, @actor),
    (11, 8, 'Caída del servidor 9',                  @actor, @actor),
    (12, 1, 'Diferencia en unidades',                @actor, @actor),
    (13, 3, 'Orden que no migró a WMS',              @actor, @actor),
    (14, 9, 'No migración de pedidos B2C a WMS',     @actor, @actor),
    (15, 3, 'Órdenes que no migraron a WMS',         @actor, @actor),
    (16, 9, 'Órdenes no migraron a WMS',             @actor, @actor),
    (17, 9, 'Traslado (TSC) no migra a WMS',         @actor, @actor),
    (18, 3, 'Órdenes B2B que no migraron a WMS',     @actor, @actor),
    (19, 8, 'Caída de comunicación ADAPTO',          @actor, @actor),
    (20, 8, 'Pérdida de secuencia AFRAME',           @actor, @actor),
    -- Faltantes para la bitácora
    (21, 3, 'Falla en interfaz',                     @actor, @actor),  -- PMM
    (22, 6, 'Falla de Servidor',                     @actor, @actor),  -- AFRAME
    (23, 7, 'Interfaz WMS',                          @actor, @actor),  -- PSYS
    (24, 1, 'Interfaz WMS',                          @actor, @actor),  -- IR-TRACK
    (25, 8, 'Falla de Servidor',                     @actor, @actor),  -- IP6
    (26, 3, 'Base de Datos',                         @actor, @actor),  -- PMM
    (27, 8, 'Falla Funcionamiento',                  @actor, @actor),  -- IP6
    (28, 9, 'Interfaz WMS',                          @actor, @actor);  -- WMS

-- ============================================================
-- 5) RESPONSABLES
-- ============================================================
INSERT INTO data_responsable (id, id_data_area, responsable, creado_por, actualizado_por) VALUES
    ( 1, 1, 'Julio Oliva',      @actor, @actor),
    ( 2, 2, 'Vanderlande',      @actor, @actor),
    ( 3, 3, 'Marco Herrera',    @actor, @actor),
    ( 4, 4, 'Kind Regards',     @actor, @actor),
    ( 5, 5, 'Oliver Gea',       @actor, @actor),
    ( 6, 6, 'Daniel Doig',      @actor, @actor),
    ( 7, 4, 'Schaefer',         @actor, @actor),
    ( 8, 1, 'Vladimir Antay',   @actor, @actor),
    ( 9, 1, 'Jhonatan Sanchez', @actor, @actor),
    (10, 1, 'Ronald Enciso',    @actor, @actor),
    (11, 1, 'Miguel mauricio',  @actor, @actor),
    (12, 1, 'Edwin Carpio',     @actor, @actor);

-- ============================================================
-- 6) BITÁCORA DE INCIDENCIAS
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
INSERT INTO incidencias
    (id_data_centro, id_data_sistema, id_data_incidencia, ticket,
     id_data_responsable, id_data_area, descripcion,
     hora_inicio, hora_fin, tiempo_solucion_seg,
     creado_por, actualizado_por, creado_en)
VALUES
    -- 1/7/26
    (1, 1,  1, 'GLPI - 2326996', 1, 1, 'Pedidos Migraron sin campo ruta en WMS',
     '17:42:00', '17:48:00',   360, @actor, @actor, '2026-07-01 22:42:00'),
    -- 4/7/26
    (1, 1,  1, 'GLPI - 2334746', 1, 1, 'Pedidos Migraron sin campo ruta en WMS',
     '12:16:00', '12:45:00',  1740, @actor, @actor, '2026-07-04 17:16:00'),
    -- 11/7/26
    (1, 2,  2, 'GLPI - 2475502', 1, 1, 'No transmite TPRO de WMS A Kisoft',
     '11:35:00', '13:32:00',  7020, @actor, @actor, '2026-07-11 16:35:00'),
    -- 20/7/26 (17:09)
    (1, 1,  3, 'GLPI - 2377818', 1, 1, 'Pedidos no migran a WMS',
     '17:09:00', '17:18:00',   540, @actor, @actor, '2026-07-20 22:09:00'),
    -- 20/7/26 (11:20) — PMM
    (1, 3, 21, 'GLPI - 2364540', 1, 1, 'Pedidos B2C no migran a WMS',
     '11:20:00', '13:58:00',  9480, @actor, @actor, '2026-07-20 16:20:00'),
    -- 30/7/26
    (1, 3,  4, 'GLPI - 2380210', 1, 1, 'Pedidos no migran a WMS',
     '17:47:00', '19:03:00',  4560, @actor, @actor, '2026-07-30 22:47:00'),
    -- 4/8/26
    (1, 3,  4, 'GLPI - 2292250', 1, 1, 'Falla en la migración de pedidos a WMS',
     '15:20:00', '15:28:00',   480, @actor, @actor, '2026-08-04 20:20:00'),
    -- 7/8/26 (16:22)
    (1, 1,  1, 'GLPI - 2399144', 1, 1, 'Órdenes Migraron sin campo ruta en WMS',
     '16:22:00', '16:48:00',  1560, @actor, @actor, '2026-08-07 21:22:00'),
    -- 7/8/26 (07:00) — SGT
    (2, 4,  5, 'V-790015', 2, 2,
     'Las cubetas en las zonas de automatismo GTP, ODS, AAA y Lacteos no tenian mensaje de accion luego de confirmar la linea. En el caso de GTP no se almacenaban al ADAPTO y tampoco ingresaban a las estaciones de picking para ser consumidas',
     '07:00:00', '10:00:00', 10800, @actor, @actor, '2026-08-07 12:00:00'),
    -- 7/8/26 (10:00) — SGT
    (2, 4,  5, 'V-790056', 2, 2,
     'Las cajas y cubetas vacias del Order Start desviaban a la zona de errores en Aframe, teniendo aproximadamente 50 ordenes sin trabajar',
     '10:00:00', '11:00:00',  3600, @actor, @actor, '2026-08-07 15:00:00'),
    -- 8/8/26 (03:49)
    (1, 1,  3, 'GLPI - 2399628', 1, 1, 'Pedidos sin orden Cross',
     '03:49:00', '07:01:00', 11520, @actor, @actor, '2026-08-08 08:49:00'),
    -- 8/8/26 (14:17)
    (1, 1,  1, 'GLPI - 2400472', 1, 1, 'Pedido migró sin ruta y sin detalle',
     '14:17:00', '14:59:00',  2520, @actor, @actor, '2026-08-08 19:17:00'),
    -- 10/8/26 (09:30) — AMR
    (2, 5,  6, 'V-790922', 3, 3,
     'Puertas en rojo en todas las puertas de AMR debido a que la base de datos se encontraba llena deteniendo todo el AMR.',
     '09:30:00', '14:00:00', 16200, @actor, @actor, '2026-08-10 14:30:00'),
    -- 10/8/26 (08:00) — AFRAME
    (2, 6, 22, '1507198', 4, 4, 'Perdida de secuencia afectando 60 bultos en Aframe.',
     '08:00:00', '09:30:00',  5400, @actor, @actor, '2026-08-10 13:00:00'),
    -- 10/8/26 (15:57) — PSYS
    (1, 7, 23, 'GLPI - 2403163', 1, 1, 'Falla en la migración de pedidos a WMS',
     '15:57:00', '16:11:00',   840, @actor, @actor, '2026-08-10 20:57:00'),
    -- 20/8/26
    (1, 7, 23, 'GLPI - 2424475', 1, 1, 'Pedidos no migran a WMS',
     '18:30:00', '18:39:00',   540, @actor, @actor, '2026-08-20 23:30:00'),
    -- 22/8/26
    (1, 2,  2, 'GLPI - 2427423', 1, 1, 'Sin conexión a servidor 172.18.206.29',
     '12:20:00', '12:40:00',  1200, @actor, @actor, '2026-08-22 17:20:00'),
    -- 23/8/26 (cruza medianoche) — IR-TRACK
    (3, 1, 24, 'GLPI - 2428659', 1, 1, 'Cargas OTM no migran a WMS 9020',
     '23:30:00', '02:25:00', 10500, @actor, @actor, '2026-08-24 04:30:00'),
    -- 24/8/26 (cruza medianoche)
    (1, 3,  4, 'GLPI - 2431812', 1, 1, 'Órdenes no migran WMS',
     '19:53:00', '00:35:00', 16920, @actor, @actor, '2026-08-25 00:53:00'),
    -- 26/8/26
    (1, 7, 23, 'GLPI - 2435435', 1, 1, 'Falla en la migración de pedidos a WMS',
     '14:38:00', '14:52:00',   840, @actor, @actor, '2026-08-26 19:38:00'),
    -- 8/9/26 (cruza medianoche) — IP6
    (2, 8, 25, 'V-800869', 5, 5,
     'Error de conexion con SGA de Ip6 hacia TPS, esto no permitia actualizar los movimientos de las plataformas en Ip6.',
     '23:30:00', '00:50:00',  4800, @actor, @actor, '2026-09-09 04:30:00'),
    -- 9/9/26 — IR-TRACK
    (1, 1, 24, 'GLPI - 2466308', 1, 1, 'Órdenes Migraron sin campo ruta en WMS',
     '23:24:00', '23:57:00',  1980, @actor, @actor, '2026-09-10 04:24:00'),
    -- 15/9/26 — PSYS
    (1, 7, 23, 'GLPI - 2476865', 1, 1, 'Órdenes no migran WMS',
     '19:24:00', '19:36:00',   720, @actor, @actor, '2026-09-16 00:24:00'),
    -- 18/9/26 — PMM
    (2, 3, 26, 'GLPI - 2481353', 6, 6,
     'La corrida de malla no liberó por completo los pedidos en PMM. Como consecuencia, no fue posible programar los cluster en OTM.',
     '04:55:00', '07:52:00', 10620, @actor, @actor, '2026-09-18 09:55:00'),
    -- 19/9/26 (07:00) — IP6
    (2, 8, 27, 'V-804634', 5, 5,
     'Durante el inicio de operaciones, se tenia el order pool menor a 100 cubetas, teniendo picos de 30 cubetas pedidas del adapto.',
     '07:00:00', '11:30:00', 16200, @actor, @actor, '2026-09-19 12:00:00'),
    -- 19/9/26 (10:50) — AMR
    (2, 5,  6, 'V-804663', 3, 3,
     'Se detuvo el movimiento de los robots de AMR debido a que el disco var estaba lleno por lo que no se podian almacenar nuevos movimientos.',
     '10:50:00', '13:00:00',  7800, @actor, @actor, '2026-09-19 15:50:00'),
    -- 20/9/26 (cruza medianoche) — IP6
    (2, 8, 27, 'V-804959', 5, 5,
     'Se detuvo el movimiento de los robots de AMR debido a que el disco var estaba lleno por lo que no se podian almacenar nuevos movimientos.',
     '23:30:00', '01:00:00',  5400, @actor, @actor, '2026-09-21 04:30:00'),
    -- 23/9/26 — IP6
    (2, 8,  7, 'V-805867', 5, 5,
     'Retraso en la caida de pedidos desde MDW a IP6 (wave pick en estado -400 ) la revision por parte de ip6 fue por sergio lorente',
     '06:50:00', '09:01:00',  7860, @actor, @actor, '2026-09-23 11:50:00'),
    -- 24/9/26 — IP6
    (2, 8,  7, 'V-806213', 5, 5,
     'Retraso en la caida de pedidos desde MDW a IP6 (wave pick en estado -400 ) la revision por parte de ip6 fue por oliver Gea',
     '06:30:00', '07:10:00',  2400, @actor, @actor, '2026-09-24 11:30:00'),
    -- 25/9/26 — AFRAME
    (2, 6,  8, '1507532', 7, 4,
     'Los dias 25 y 26 se presento lentitud en la migracion de los bultos trabajados en Aframe hacia Ip6, se realizo un reinicio en WAMAS.',
     '07:00:00', '23:00:00', 57600, @actor, @actor, '2026-09-25 12:00:00'),
    -- 26/9/26 — PSYS
    (2, 7,  9, '2497404', 1, 1,
     'Pedidos del CL01 no migran desde PSYS hacia OTM / IR TRACK revision por parte de ronald enciso / juan seperak / jorge villacorta',
     '04:40:00', '09:30:00', 17400, @actor, @actor, '2026-09-26 09:40:00'),
    -- 28/9/26 (04:15) — PMM
    (2, 3, 10, '2499222', 6, 6,
     'retraso en la caida de la OD y ejecución de la malla, revisión por parte de Daniel Doig y operador central',
     '04:15:00', '06:17:00',  7320, @actor, @actor, '2026-09-28 09:15:00'),
    -- 28/9/26 (10:00) — IP6
    (2, 8, 11, 'V-807338', 5, 5,
     'Durante las horas de las 10:00 a las 10:30 se presento la caida del servidor 9 en todas las estaciones del automatismo, se procedio con el cambio al servidor 13 de manera temporal hasta que se terminara de revisar la incidencia.',
     '10:00:00', '10:30:00',  1800, @actor, @actor, '2026-09-28 15:00:00'),
    -- 30/9/26 — IR-TRACK
    (2, 1, 12, '2503978', 8, 1,
     'Diferencia de 8 unidades entre IR Track y WMS el siguiente pedido Q020137948771',
     '09:00:00', '11:00:00',  7200, @actor, @actor, '2026-09-30 14:00:00'),
    -- 1/10/26 — PMM
    (2, 3, 13, '2506617', 1, 1,
     'Orden de B2B que no migra a WMS QRE0138034959',
     '08:45:00', '10:30:00',  6300, @actor, @actor, '2026-10-01 13:45:00'),
    -- 5/10/26 (05:15) — PMM
    (2, 3, 10, '2515811', 6, 6,
     'retraso en la caida de la OD y ejecución de la malla, revisión por parte de Daniel Doig y operador central',
     '05:15:00', '07:50:00',  9300, @actor, @actor, '2026-10-05 10:15:00'),
    -- 5/10/26 (10:07) — WMS
    (1, 9, 14, '2516658', 8, 1,
     'Los pedidos fueron programados en OTM, pero no migran a WMS, se reporta al área de aplicaciones.',
     '10:07:00', '11:55:00',  6480, @actor, @actor, '2026-10-05 15:07:00'),
    -- 5/10/26 (13:52) — WMS
    (1, 9, 28, '2517404', 9, 1, 'Pedido migro sin Ruta',
     '13:52:00', '14:13:00',  1260, @actor, @actor, '2026-10-05 18:52:00'),
    -- 5/10/26 (11:49) — KISOFT
    (1, 2,  2, '2516884', 9, 1, 'Falta de transmisión del TPRO',
     '11:49:00', '13:24:00',  5700, @actor, @actor, '2026-10-05 16:49:00'),
    -- 6/10/26 (cruza medianoche) — PMM
    (2, 3, 13, '2519298', 8, 1,
     'Orden de B2B que no migra a WMS QRE0138530253',
     '09:00:00', '00:00:00', 54000, @actor, @actor, '2026-10-06 14:00:00'),
    -- 7/10/26 (09:00, cruza medianoche) — PMM
    (2, 3, 15, '2521429', 10, 1,
     'Ordenes de B2B que no migra a WMS Q690138530035-QRE0138676534',
     '09:00:00', '00:00:00', 54000, @actor, @actor, '2026-10-07 14:00:00'),
    -- 7/10/26 (00:26) — WMS
    (1, 9, 16, '2522085', 10, 1, '1 pedido no migraro a wms.',
     '00:26:00', '13:00:00', 45240, @actor, @actor, '2026-10-07 05:26:00'),
    -- 10/10/26 — PMM
    (2, 3, 15, '2526633', 10, 1,
     'Orden de B2B que no migra a WMS QRE0138967944',
     '09:00:00', '10:00:00',  3600, @actor, @actor, '2026-10-10 14:00:00'),
    -- 12/10/26 (09:00, cruza medianoche) — PMM
    (2, 3, 15, '2528684', 10, 1,
     'Orden de B2B que no migra a WMS QRE0138966544',
     '09:00:00', '00:00:00', 54000, @actor, @actor, '2026-10-12 14:00:00'),
    -- 12/10/26 (00:49) — WMS
    (1, 9, 16, '2529418', 10, 1, '2 pedidos no migraron a wms.',
     '00:49:00', '13:20:00', 45060, @actor, @actor, '2026-10-12 05:49:00'),
    -- 15/10/26 (cruza medianoche) — WMS
    (1, 9, 16, '2537405', 11, 1, '1 pedido no migraron a wms.',
     '20:43:00', '00:00:00', 11820, @actor, @actor, '2026-10-16 01:43:00'),
    -- 16/10/26 — WMS
    (1, 9, 16, '2538663', 12, 1, '2 pedidos no migraron a WMS',
     '13:33:00', '14:45:00',  4320, @actor, @actor, '2026-10-16 18:33:00'),
    -- 17/10/26 (11:55) — WMS
    (1, 9, 16, '2540079', 9, 1, '2 pedidos no migraron a WMS',
     '11:55:00', '12:42:00',  2820, @actor, @actor, '2026-10-17 16:55:00'),
    -- 17/10/26 (14:17) — WMS
    (1, 9, 17, '2540320', 9, 1,
     '1 TSC estaba en estado "Con planificación" en IR-TRACK pero no figuraba en WMS',
     '14:17:00', '15:08:00',  3060, @actor, @actor, '2026-10-17 19:17:00'),
    -- 20/10/26 — PMM
    (2, 3, 18, '2543940', 12, 1,
     'Ordenes de B2B que no migra a WMS QRE0140071943,QRE0140071945,QRE0140082725,QRE0140071941,QRE0140071942',
     '09:00:00', '10:51:00',  6660, @actor, @actor, '2026-10-20 14:00:00'),
    -- 26/10/26 (08:00) — IP6
    (2, 8, 19, 'V-816459', 5, 5,
     'No tenemos comunicación de SGT, las cubetas no se mueven afectando decanting, cubetas vacias, cubetas sin destino.',
     '08:00:00', '11:30:00', 12600, @actor, @actor, '2026-10-26 13:00:00'),
    -- 26/10/26 (18:00) — IP6
    (2, 8, 20, 'Sin respuesta', 7, 4,
     'Se presento perdida de secuencia, pasando bultos vacios por el fillyng point.',
     '18:00:00', '23:00:00', 18000, @actor, @actor, '2026-10-26 23:00:00'),
    -- 30/10/26 — IP6
    (2, 8, 19, 'V-818189', 5, 5,
     'No tenemos comunicación de SGT, las cubetas no se mueven afectando decanting, cubetas vacias, cubetas sin destino.',
     '05:45:00', '07:35:00',  6600, @actor, @actor, '2026-10-30 10:45:00');

SET FOREIGN_KEY_CHECKS = 1;

COMMIT;