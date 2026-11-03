package oracle.malla.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import oracle.common.model.Auditable;

/**
 * Regla operativa de la Malla. El catálogo de reglas vive en la base de datos
 * (no en el código) para que un administrador pueda activarlas o ajustar sus
 * parámetros desde el módulo Configuración. El evaluador conoce el código de
 * cada regla y cómo interpretar sus parámetros.
 */
@Entity
@Table(name = "malla_regla")
@Getter
@Setter
public class MallaRegla extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 40, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "descripcion", length = 400, nullable = false)
    private String descripcion;

    /** CENTRO, TURNO, FLUJO, OLA, ZONA, CAPACIDAD o DOTACION. */
    @Column(name = "ambito", length = 20, nullable = false)
    private String ambito;

    /** ALTA, MEDIA o BAJA. */
    @Column(name = "severidad", length = 10, nullable = false)
    private String severidad;

    /** Parámetros efectivos en JSON: {"clave":"valor"}. */
    @Column(name = "parametros", nullable = false, columnDefinition = "TEXT")
    private String parametros;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    @Column(name = "orden", nullable = false)
    private int orden;
}
