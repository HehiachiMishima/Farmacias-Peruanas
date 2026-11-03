package oracle.malla.response;

/** Parámetro tipado de una regla, listo para pintarse en el módulo Configuración. */
public record ParametroMallaResponse(
        String clave,
        String etiqueta,
        String tipo,
        String valor) {
}
