package oracle.response;

/** Grupo agregado (una barra): etiqueta del catalogo, nº de incidencias y segundos acumulados. */
public record GrupoEstadistica(String etiqueta, long count, long segundos) {
}
