package oracle.simulacion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import oracle.simulacion.model.RutaProyeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RutaProyeccionRepository
    extends JpaRepository<RutaProyeccion, Long>, JpaSpecificationExecutor<RutaProyeccion> {

  /** Todas las rutas del archivo: solo para el balanceo, que necesita moverlas una a una. */
  List<RutaProyeccion> findByArchivoIdOrderByIdAsc(Long archivoId);

  Optional<RutaProyeccion> findByIdAndArchivoId(Long id, Long archivoId);

  /** Clusters con rutas en un chute: valida que un movimiento no mezcle CL1 y CL2. */
  @Query("""
      SELECT DISTINCT r.clusterId
        FROM RutaProyeccion r
       WHERE r.archivoId = :archivoId
         AND r.chuteId = :chuteId
      """)
  List<Long> clustersEnChute(
      @Param("archivoId") Long archivoId, @Param("chuteId") Long chuteId);

  @Query("""
      SELECT r.archivoId AS archivoId,
             COUNT(r) AS rutas,
             COUNT(DISTINCT r.chuteId) AS chutes,
             COALESCE(SUM(r.totalBultos), 0) AS bultos,
             COALESCE(SUM(r.totalUnidades), 0) AS unidades,
             COALESCE(SUM(r.totalLineas), 0) AS lineas,
             COALESCE(SUM(r.conteoCod), 0) AS cod
        FROM RutaProyeccion r
       WHERE r.archivoId IN :ids
       GROUP BY r.archivoId
      """)
  List<ResumenRutasArchivo> resumenPorArchivos(@Param("ids") Collection<Long> ids);

  /**
   * Carga por (cluster, chute): la configuración y el dashboard trabajan con
   * estos agregados (decenas de filas) en lugar de traer todas las rutas.
   * Usa idx_rutas_archivo_cluster.
   */
  @Query("""
      SELECT r.clusterId AS clusterId,
             r.chuteId AS chuteId,
             COUNT(r) AS rutas,
             COALESCE(SUM(r.totalBultos), 0) AS bultos,
             COALESCE(SUM(r.totalUnidades), 0) AS unidades,
             COALESCE(SUM(r.totalLineas), 0) AS lineas,
             COALESCE(SUM(r.conteoCod), 0) AS cod
        FROM RutaProyeccion r
       WHERE r.archivoId = :archivoId
       GROUP BY r.clusterId, r.chuteId
      """)
  List<CargaRutas> cargaPorArchivo(@Param("archivoId") Long archivoId);

  /**
   * Carga original por (cluster, chute original). El chute original se congela al cargar el Excel,
   * así que el comparativo "antes" no depende de que el historial siga existiendo.
   */
  @Query("""
      SELECT r.clusterId AS clusterId,
             r.chuteOriginalId AS chuteId,
             COUNT(r) AS rutas,
             COALESCE(SUM(r.totalBultos), 0) AS bultos,
             COALESCE(SUM(r.totalUnidades), 0) AS unidades,
             COALESCE(SUM(r.totalLineas), 0) AS lineas,
             COALESCE(SUM(r.conteoCod), 0) AS cod
        FROM RutaProyeccion r
       WHERE r.archivoId = :archivoId
       GROUP BY r.clusterId, r.chuteOriginalId
      """)
  List<CargaRutas> cargaOriginalPorArchivo(@Param("archivoId") Long archivoId);

  /**
   * Rutas que cambiaron de chute, agrupadas por (chute original, chute actual).
   * Con clusterId, solo las de ese cluster (vista de un lanzamiento).
   */
  @Query("""
      SELECT r.chuteOriginalId AS origen,
             r.chuteId AS destino,
             COUNT(r) AS rutas
        FROM RutaProyeccion r
       WHERE r.archivoId = :archivoId
         AND r.chuteId <> r.chuteOriginalId
         AND (:clusterId IS NULL OR r.clusterId = :clusterId)
       GROUP BY r.chuteOriginalId, r.chuteId
      """)
  List<MovimientoRutas> movimientosPorArchivo(
      @Param("archivoId") Long archivoId, @Param("clusterId") Long clusterId);

  /** Rutas movidas de cada cluster (hoy en un chute distinto al original). */
  @Query("""
      SELECT r.clusterId AS clusterId,
             COUNT(r) AS rutas
        FROM RutaProyeccion r
       WHERE r.archivoId = :archivoId
         AND r.chuteId <> r.chuteOriginalId
       GROUP BY r.clusterId
      """)
  List<RutasMovidasCluster> rutasMovidasPorCluster(@Param("archivoId") Long archivoId);

  /** Devuelve todas las rutas del archivo a su chute original sin recorrerlas una a una. */
  @Modifying
  @Query("""
      UPDATE RutaProyeccion r
         SET r.chuteId = r.chuteOriginalId
       WHERE r.archivoId = :archivoId
         AND r.chuteId <> r.chuteOriginalId
      """)
  int restaurarChutes(@Param("archivoId") Long archivoId);

  /** Totales del detalle de rutas; cada lado añade su filtro de chute (ver abajo). */
  String RESUMEN =
      """
      SELECT COUNT(*) AS rutas,
             COALESCE(SUM(CASE WHEN tipo = 'B2C' THEN 1 ELSE 0 END), 0) AS rutasB2c,
             COALESCE(SUM(CASE WHEN tipo = 'B2B' THEN 1 ELSE 0 END), 0) AS rutasB2b,
             COALESCE(SUM(CASE WHEN chute_id <> chute_original_id THEN 1 ELSE 0 END), 0)
                 AS cambios,
             COALESCE(SUM(total_bultos), 0) AS bultos,
             COALESCE(SUM(total_unidades), 0) AS unidades,
             COALESCE(SUM(total_lineas), 0) AS lineas,
             COALESCE(SUM(conteo_cod), 0) AS cod
        FROM rutas_proyeccion
       WHERE archivo_id = :archivoId
         AND (:texto IS NULL OR codigo_ruta LIKE :texto)
         AND (:tipo IS NULL OR tipo = :tipo)
         AND (:clusterId IS NULL OR cluster_id = :clusterId)
         AND (:soloCambios = FALSE OR chute_id <> chute_original_id)
      """;

  /** Rutas que los chutes tenían al cargar el Excel (usa idx_rutas_archivo_chute_original). */
  @Query(value = RESUMEN + " AND chute_original_id IN (:chuteIds)", nativeQuery = true)
  ResumenRutasFiltro resumenAntes(
      @Param("archivoId") Long archivoId,
      @Param("texto") String texto,
      @Param("tipo") String tipo,
      @Param("chuteIds") List<Long> chuteIds,
      @Param("clusterId") Long clusterId,
      @Param("soloCambios") boolean soloCambios);

  /** Rutas que los chutes tienen hoy (usa idx_rutas_archivo_chute). */
  @Query(value = RESUMEN + " AND chute_id IN (:chuteIds)", nativeQuery = true)
  ResumenRutasFiltro resumenDespues(
      @Param("archivoId") Long archivoId,
      @Param("texto") String texto,
      @Param("tipo") String tipo,
      @Param("chuteIds") List<Long> chuteIds,
      @Param("clusterId") Long clusterId,
      @Param("soloCambios") boolean soloCambios);

  /**
   * Totales de un lado de los chutes elegidos: el detalle muestra los del
   * conjunto filtrado, no solo de la página.
   */
  default ResumenRutasFiltro resumenFiltrado(
      Long archivoId,
      String texto,
      String tipo,
      List<Long> chuteIds,
      boolean antes,
      Long clusterId,
      boolean soloCambios) {
    return antes
        ? resumenAntes(archivoId, texto, tipo, chuteIds, clusterId, soloCambios)
        : resumenDespues(archivoId, texto, tipo, chuteIds, clusterId, soloCambios);
  }
}
