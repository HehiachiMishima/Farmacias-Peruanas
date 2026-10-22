package oracle.inbound.response;

import java.util.List;
import oracle.common.response.CeldaMatriz;
import oracle.common.response.PuntoSerie;

/**
 * Analitica del panel Inbound centrada en el proveedor, calculada en SQL.
 *
 * @param granularidad "dia" o "mes": unidad de la serie {@code tendencia}.
 */
public record AnaliticaInboundResponse(
        Kpis kpis,
        List<Proveedor> proveedores,
        List<Tipo> tipos,
        List<CeldaMatriz> proveedorTipo,
        List<Sku> skus,
        List<PuntoSerie> tendencia,
        String granularidad,
        List<ProveedorMensualEstadistica> mensualTop) {

    /**
     * @param totalPeriodoAnterior incidencias del periodo anterior de igual duracion;
     *        null si el filtro no tiene rango completo.
     */
    public record Kpis(
            long total,
            long unidades,
            long proveedores,
            long ordenesCompra,
            long skus,
            long tipos,
            Long totalPeriodoAnterior,
            Long unidadesPeriodoAnterior) {
    }

    /** Ranking de proveedor: volumen, unidades, OC y SKU distintos y su tipo dominante. */
    public record Proveedor(
            long id,
            String proveedor,
            long total,
            long unidades,
            long ordenesCompra,
            long skus,
            String tipoDominante,
            long totalTipoDominante) {
    }

    /** Tipo de incidencia: volumen, unidades y cuantos proveedores la presentan. */
    public record Tipo(String tipo, long total, long unidades, long proveedores) {
    }

    /** SKU recurrente: incidencias, unidades y proveedores distintos. */
    public record Sku(String sku, long total, long unidades, long proveedores) {
    }
}
