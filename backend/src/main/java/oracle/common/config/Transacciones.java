package oracle.common.config;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ejecuta bloques de trabajo dentro de una transaccion.
 *
 * Los services devuelven Mono y ejecutan su cuerpo en Schedulers.boundedElastic,
 * por lo que @Transactional en el metodo publico no abarcaria el trabajo real
 * (solo la construccion del Mono). Este helper permite abrir la transaccion
 * DENTRO del hilo de trabajo, de modo que las lecturas + escritura de una
 * operacion compartan la misma transaccion (evita ventanas TOCTOU inconsistentes).
 *
 * Las lecturas puramente de solo consulta usan consultar() (readOnly=true).
 */
@Component
public class Transacciones {

    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public Transacciones(PlatformTransactionManager gestor) {
        this.escritura = new TransactionTemplate(gestor);
        this.lectura = new TransactionTemplate(gestor);
        this.lectura.setReadOnly(true);
    }

    public <T> T ejecutar(Supplier<T> trabajo) {
        return escritura.execute(estado -> trabajo.get());
    }

    public <T> T consultar(Supplier<T> trabajo) {
        return lectura.execute(estado -> trabajo.get());
    }
}
