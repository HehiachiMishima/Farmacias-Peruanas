package oracle.simulacion.response;

public record BalanceoResponse(
    int rutasMovidas, int operariosAgregados, int operariosMovidos, String mensaje) {}
