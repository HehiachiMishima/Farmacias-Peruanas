package oracle.malla.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo técnico de las reglas de la malla: qué parámetros acepta cada
 * código, con qué etiqueta se muestran y qué valor usan si la base no los
 * trae. Los textos largos (nombre/descripción) viven en {@code malla_regla}
 * para que se puedan corregir desde Configuración sin recompilar.
 */
public final class ReglasMallaCatalogo {

    public record ParametroDef(String clave, String etiqueta, String tipo, String porDefecto) {
    }

    public static final String OLA_LANZAMIENTO = "OLA_LANZAMIENTO";
    public static final String OLA_SECUENCIA = "OLA_SECUENCIA";
    public static final String OLA_MAX_PICKING = "OLA_MAX_PICKING";
    public static final String OLA_CONFIRMADA = "OLA_CONFIRMADA";
    public static final String TURNO_COBERTURA = "TURNO_COBERTURA";
    public static final String TURNO_REFRIGERIO = "TURNO_REFRIGERIO";
    public static final String FLUJO_VENTANA_TURNO = "FLUJO_VENTANA_TURNO";
    public static final String FLUJO_DURACION = "FLUJO_DURACION";
    public static final String FLUJO_CONFIRMADO = "FLUJO_CONFIRMADO";
    public static final String CAPACIDAD_DECLARADA = "CAPACIDAD_DECLARADA";
    public static final String DOTACION_PLANIFICADA = "DOTACION_PLANIFICADA";
    public static final String ZONA_LPH_DISENO = "ZONA_LPH_DISENO";

    private ReglasMallaCatalogo() {
    }

    /** Parámetros que entiende el evaluador para un código. */
    public static List<ParametroDef> parametros(String codigo) {
        return switch (codigo) {
            case OLA_LANZAMIENTO -> List.of(
                    new ParametroDef("limites", "Hora límite por ola", "LISTA_HORAS", "07:00,15:00"),
                    new ParametroDef("toleranciaMin", "Tolerancia (min)", "ENTERO", "0"));
            case OLA_MAX_PICKING -> List.of(
                    new ParametroDef("horasMax", "Máximo de picking (h)", "ENTERO", "8"));
            case TURNO_COBERTURA -> List.of(
                    new ParametroDef("minutosGapMax", "Hueco permitido (min)", "ENTERO", "0"));
            case TURNO_REFRIGERIO -> List.of(
                    new ParametroDef("exigirRefrigerio", "Exigir refrigerio", "BOOLEANO", "true"));
            case FLUJO_VENTANA_TURNO -> List.of(
                    new ParametroDef("incluirIL", "Evaluar también pasos de IL", "BOOLEANO", "false"));
            case FLUJO_DURACION -> List.of(
                    new ParametroDef("horasMax", "Duración máxima (h)", "ENTERO", "12"));
            default -> List.of();
        };
    }

    /** Valores por defecto, en el mismo orden en que se declaran. */
    public static Map<String, String> porDefecto(String codigo) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (ParametroDef def : parametros(codigo)) {
            valores.put(def.clave(), def.porDefecto());
        }
        return valores;
    }
}
