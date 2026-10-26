package oracle.simulacion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import oracle.simulacion.model.RutaProyeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RutaProyeccionRepository
    extends JpaRepository<RutaProyeccion, Long>, JpaSpecificationExecutor<RutaProyeccion> {

  /** Todas las rutas del archivo: solo para el balanceo, que necesita moverlas una a una. */
  List<RutaProyeccion> findByArchivoIdOrderByIdAsc(Long archivoId);

  Optional<RutaProyeccion> findByIdAndArchivoId(Long id, Long archivoId);

  @Query("""
      SELECT r.archivoId AS archivoId,
             COUNT(r) AS rutas,
             COUNT(DISTINCT r.chuteId) AS chutes,
             COALESCE(SUM(r.totalBultos), 0) AS bultos,
             COALESCE(SUM(r.totalUnidades), 0) AS unidades,
             COALESCE(SUM(r.totalLineas), 0) AS lineas
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
}
