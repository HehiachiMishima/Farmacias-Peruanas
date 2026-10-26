package oracle.simulacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Movimiento realizado por el balanceo (ruta movida u operario añadido). */
@Entity
@Table(name = "historial_balanceo")
@Getter
@Setter
public class HistorialBalanceo {

  public static final String ACCION_MOVER_RUTA = "MOVER_RUTA";
  public static final String ACCION_ANADIR_OPERARIO = "AÑADIR_OPERARIO";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "archivo_id", nullable = false)
  private Long archivoId;

  @Column(name = "tipo_accion", length = 30, nullable = false)
  private String tipoAccion;

  @Column(name = "ruta_id")
  private Long rutaId;

  @Column(name = "chute_origen_id")
  private Long chuteOrigenId;

  @Column(name = "chute_destino_id")
  private Long chuteDestinoId;

  @Column(name = "chute_modificado_id")
  private Long chuteModificadoId;

  @Column(name = "creado_por", nullable = false)
  private Long creadoPor;

  @Column(name = "creado_en", nullable = false)
  private LocalDateTime creadoEn;
}
