package oracle.simulacion.response;

import java.math.BigDecimal;

public record RutaSimulacionResponse(
    Long id,
    Long chuteId,
    String chute,
    Long clusterId,
    String cluster,
    String codigoRuta,
    String tipo,
    BigDecimal totalBultos,
    BigDecimal totalUnidades,
    BigDecimal totalLineas,
    Integer conteoCod) {}
