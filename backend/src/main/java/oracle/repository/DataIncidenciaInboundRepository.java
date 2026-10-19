package oracle.repository;

import oracle.model.DataIncidenciaInbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DataIncidenciaInboundRepository
    extends JpaRepository<DataIncidenciaInbound, Long>, JpaSpecificationExecutor<DataIncidenciaInbound> {
  boolean existsByIncidenciaInboundIgnoreCase(String incidenciaInbound);
  boolean existsByIncidenciaInboundIgnoreCaseAndIdNot(String incidenciaInbound, Long id);
}
