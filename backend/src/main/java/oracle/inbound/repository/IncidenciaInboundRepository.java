package oracle.inbound.repository;

import oracle.inbound.model.IncidenciaInbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidenciaInboundRepository
    extends JpaRepository<IncidenciaInbound, Long>, JpaSpecificationExecutor<IncidenciaInbound> {
}
