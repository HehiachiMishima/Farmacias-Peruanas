package oracle.services;

import java.time.LocalDateTime;
import oracle.model.Auditable;

/**
 * Auditoria manual: el proyecto no tiene @EnableJpaAuditing (la capa config
 * esta fuera de alcance), asi que el service fija fechas e id del actor
 * antes de guardar. Ver columnas creado_por/actualizado_por del esquema.
 * El actor se resuelve en el hilo de la peticion (ActorActual) y se pasa
 * aqui; 0 = sistema/seeding, convencion del esquema.
 */
final class Auditoria {

    static void alCrear(Auditable entidad, Long actor) {
        LocalDateTime ahora = LocalDateTime.now();
        entidad.setCreadoEn(ahora);
        entidad.setActualizadoEn(ahora);
        entidad.setCreadoPor(actor);
        entidad.setActualizadoPor(actor);
    }

    static void alActualizar(Auditable entidad, Long actor) {
        entidad.setActualizadoEn(LocalDateTime.now());
        entidad.setActualizadoPor(actor);
    }

    private Auditoria() {
    }
}
