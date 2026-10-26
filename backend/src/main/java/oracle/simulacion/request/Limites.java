package oracle.simulacion.request;

/** Topes de los parámetros de simulación (validación de requests y balanceo). */
public final class Limites {

  /** Operarios por chute: el balanceo tampoco supera este tope. */
  public static final int MAX_OPERARIOS = 50;
  /** Límite de horas por cluster: un mes de operación continua. */
  public static final String MAX_HORAS = "744.00";
  /** Bultos por hora por operario. */
  public static final String MAX_VELOCIDAD = "100000.00";
  /** Clusters o chutes por petición. */
  public static final int MAX_PARAMETROS = 2_000;

  private Limites() {}
}
