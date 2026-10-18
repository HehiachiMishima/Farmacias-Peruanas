package oracle.repository;

import oracle.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {

    boolean existsByTicket(String ticket);

    boolean existsByTicketAndIdNot(String ticket, Long id);
}
