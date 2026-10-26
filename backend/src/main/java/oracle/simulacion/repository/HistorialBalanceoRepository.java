package oracle.simulacion.repository;

import java.util.List;
import oracle.simulacion.model.HistorialBalanceo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistorialBalanceoRepository extends JpaRepository<HistorialBalanceo, Long> {

  List<HistorialBalanceo> findByArchivoIdOrderByIdAsc(Long archivoId);

  /** Últimos movimientos para el historial visible (acotado). */
  List<HistorialBalanceo> findTop500ByArchivoIdOrderByIdDesc(Long archivoId);

  /** Borrado en una sola sentencia (el derivado deleteBy… carga y borra fila a fila). */
  @Modifying
  @Query("DELETE FROM HistorialBalanceo h WHERE h.archivoId = :archivoId")
  void borrarPorArchivo(@Param("archivoId") Long archivoId);
}
