package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Operarios asignados a un chute dentro de un archivo. */
@Entity
@Table(name = "parametros_chutes")
@Getter
@Setter
public class ParametroChute {

  /** Operarios con los que arranca cada chute al cargar un Excel. */
  public static final int OPERARIOS_INICIALES = 1;

  @EmbeddedId
  private ParametroChuteId id;

  @Column(name = "operarios", nullable = false)
  private Integer operarios;

  /**
   * Operarios que fijó el usuario (al cargar, 1; luego lo que guarde a mano).
   * Es la base del comparativo y lo que deja «Restablecer»: el balanceo no lo
   * cambia.
   */
  @Column(name = "operarios_original", nullable = false)
  private Integer operariosOriginal;
}
