package oracle.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LimiteSolicitudesTest {

    @Test
    void admiteHastaElMaximoYLuegoDiceCuantoEsperar() {
        LimiteSolicitudes limite = new LimiteSolicitudes();

        for (int i = 0; i < 3; i++) {
            assertThat(limite.consumir("ana", 3, 60_000)).isZero();
        }

        assertThat(limite.consumir("ana", 3, 60_000)).isBetween(1L, 60_000L);
    }

    @Test
    void cadaClaveCuentaPorSeparado() {
        LimiteSolicitudes limite = new LimiteSolicitudes();

        assertThat(limite.consumir("ana", 1, 60_000)).isZero();

        assertThat(limite.consumir("luis", 1, 60_000)).isZero();
        assertThat(limite.consumir("ana", 1, 60_000)).isPositive();
    }

    @Test
    void laVentanaVencidaLiberaLugar() throws InterruptedException {
        LimiteSolicitudes limite = new LimiteSolicitudes();
        assertThat(limite.consumir("ana", 1, 50)).isZero();

        Thread.sleep(80);

        assertThat(limite.consumir("ana", 1, 50)).isZero();
    }
}
