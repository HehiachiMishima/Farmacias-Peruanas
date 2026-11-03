package oracle.inbound.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import oracle.inbound.response.MesIncidenciaProveedor;
import oracle.inbound.response.ProveedorMensualEstadistica;

/**
 * Agrupa las filas (proveedor, mes, incidencias) que devuelve MySQL en una
 * serie mensual por proveedor. Logica pura, sin acceso a datos.
 */
final class SerieMensualProveedores {

    /** Fila del GROUP BY: un proveedor en un mes. */
    record Fila(long idProveedor, String proveedor, String mes, long incidencias) {
    }

    /**
     * Las filas llegan ordenadas por proveedor y mes; el resultado queda
     * ordenado por total de incidencias descendente (estable: a igual total
     * conserva el orden de llegada).
     */
    static List<ProveedorMensualEstadistica> agrupar(List<Fila> filas) {
        List<ProveedorMensualEstadistica> resultado = new ArrayList<>();
        List<MesIncidenciaProveedor> meses = null;
        Fila primera = null;
        long total = 0;
        for (Fila f : filas) {
            if (primera == null || f.idProveedor() != primera.idProveedor()) {
                if (primera != null) {
                    resultado.add(new ProveedorMensualEstadistica(primera.idProveedor(), primera.proveedor(), total, meses));
                }
                primera = f;
                meses = new ArrayList<>();
                total = 0;
            }
            meses.add(new MesIncidenciaProveedor(f.mes(), f.incidencias()));
            total += f.incidencias();
        }
        if (primera != null) {
            resultado.add(new ProveedorMensualEstadistica(primera.idProveedor(), primera.proveedor(), total, meses));
        }
        resultado.sort(Comparator.comparingLong(ProveedorMensualEstadistica::total).reversed());
        return resultado;
    }

    private SerieMensualProveedores() {
    }
}
