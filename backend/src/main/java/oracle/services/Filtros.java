package oracle.services;

/** Utilidades para filtros de listado. */
final class Filtros {

    /**
     * Normaliza un texto de filtro: recorta y convierte vacio en null,
     * para que el repository lo ignore en el LIKE.
     */
    static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    private Filtros() {
    }
}
