package oracle.simulacion.repository;

import java.util.List;
import oracle.simulacion.model.HistorialBalanceo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialBalanceoRepository extends JpaRepository<HistorialBalanceo, Long> {

  List<HistorialBalanceo> findByArchivoIdOrderByIdAsc(Long archivoId);

  long countByArchivoIdAndTipoAccion(Long archivoId, String tipoAccion);

  void deleteByArchivoId(Long archivoId);
}
