package oracle.simulacion.response;

import java.math.BigDecimal;

public record RutaSimulacionResponse(
    Long id,
    Long chuteId,
    String chute,
    Long clusterId,
    String cluster,
    String codigoRuta,
    BigDecimal totalBultos,
    BigDecimal totalUnidades,
    BigDecimal totalLineas,
    Integer conteoCod) {}
