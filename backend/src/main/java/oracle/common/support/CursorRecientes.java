package oracle.common.support;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Cursor (keyset) para recorrer una tabla en el orden {@link Paginacion#RECIENTES}
 * (creado_en DESC, id DESC) sin OFFSET: la siguiente pagina son las filas
 * "anteriores" a la ultima leida. MySQL lo resuelve como rango sobre los indices
 * (..., creado_en) + id, asi que cada lote cuesta lo mismo sin importar cuantas
 * filas se hayan leido antes.
 */
public final class CursorRecientes {

    /** Filas que van despues de (creadoEn, id) en el orden RECIENTES. */
    public static <E> Specification<E> despuesDe(Instant creadoEn, Long id) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.<Instant>get("creadoEn"), creadoEn),
                cb.and(
                        cb.equal(root.get("creadoEn"), creadoEn),
                        cb.lessThan(root.<Long>get("id"), id)));
    }

    private CursorRecientes() {
    }
}
