package oracle.common.support;

/** Utilidades para filtros de listado. */
public final class Filtros {

    /**
     * Normaliza un texto de filtro: recorta y convierte vacio en null,
     * para que el repository lo ignore en el LIKE.
     */
    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    private Filtros() {
    }
}
