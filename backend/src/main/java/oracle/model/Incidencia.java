package oracle.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro de bitácora. Referencia los catálogos por id (FK RESTRICT) y
 * guarda la hora de inicio/fin y el tiempo de solución calculado.
 *
 * creado_en se registra manualmente (fecha indicada por el usuario + hora de
 * inicio), por eso se sobreescribe la columna de Auditable para permitir su
 * actualización.
 */
@Entity
@Table(name = "incidencias")
@AttributeOverride(name = "creadoEn", column = @Column(name = "creado_en", nullable = false))
@Getter
@Setter
public class Incidencia extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_data_centro", nullable = false)
    private Long idDataCentro;

    @Column(name = "id_data_sistema", nullable = false)
    private Long idDataSistema;

    @Column(name = "id_data_incidencia", nullable = false)
    private Long idDataIncidencia;

    @Column(name = "ticket", nullable = false, unique = true, length = 59)
    private String ticket;

    @Column(name = "id_data_responsable", nullable = false)
    private Long idDataResponsable;

    @Column(name = "id_data_area", nullable = false)
    private Long idDataArea;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;
 
    @Column(name = "tiempo_solucion")
    private LocalTime tiempoSolucion;
}
