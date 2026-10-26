package oracle.simulacion.repository;

import java.util.Collection;
import java.util.List;
import oracle.simulacion.model.RutaProyeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RutaProyeccionRepository extends JpaRepository<RutaProyeccion, Long> {

  List<RutaProyeccion> findByArchivoIdOrderByIdAsc(Long archivoId);

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

  @Query("""
      SELECT COUNT(DISTINCT r.chuteId) AS chutes,
             COALESCE(SUM(r.totalBultos), 0) AS bultos,
             COALESCE(SUM(r.totalUnidades), 0) AS unidades,
             COALESCE(SUM(r.totalLineas), 0) AS lineas
        FROM RutaProyeccion r
      """)
  ResumenGlobal resumenGlobal();
}
