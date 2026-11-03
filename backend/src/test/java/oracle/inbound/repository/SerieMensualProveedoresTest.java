package oracle.inbound.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import oracle.inbound.repository.SerieMensualProveedores.Fila;
import oracle.inbound.response.MesIncidenciaProveedor;
import oracle.inbound.response.ProveedorMensualEstadistica;
import org.junit.jupiter.api.Test;

class SerieMensualProveedoresTest {

    @Test
    void agrupaPorProveedorYOrdenaPorTotalDescendente() {
        List<ProveedorMensualEstadistica> serie = SerieMensualProveedores.agrupar(List.of(
                new Fila(1, "Uno", "2026-01", 2),
                new Fila(1, "Uno", "2026-02", 1),
                new Fila(2, "Dos", "2026-01", 5),
                new Fila(3, "Tres", "2026-03", 3)));

        assertThat(serie).extracting(ProveedorMensualEstadistica::idProveedor).containsExactly(2L, 1L, 3L);
        assertThat(serie.get(1).total()).isEqualTo(3);
        assertThat(serie.get(1).meses()).containsExactly(
                new MesIncidenciaProveedor("2026-01", 2), new MesIncidenciaProveedor("2026-02", 1));
    }

    @Test
    void aIgualTotalConservaElOrdenDeLlegada() {
        List<ProveedorMensualEstadistica> serie = SerieMensualProveedores.agrupar(List.of(
                new Fila(4, "Cuatro", "2026-01", 1),
                new Fila(7, "Siete", "2026-01", 1)));

        assertThat(serie).extracting(ProveedorMensualEstadistica::idProveedor).containsExactly(4L, 7L);
    }

    @Test
    void sinFilasDevuelveListaVacia() {
        assertThat(SerieMensualProveedores.agrupar(List.of())).isEmpty();
    }
}
