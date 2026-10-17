/**
 * Configuración transversal de la aplicación en un único punto:
 * claves de almacenamiento, rutas de navegación, duraciones de UI y
 * márgenes de sesión. Evita "números y cadenas mágicas" repartidos por el
 * código y concentra aquí lo que cambia entre entornos o por diseño.
 */
export const CONFIG = {
  almacenamiento: {
    sesion: 'oracle.sesion',
    tema: 'oracle.tema',
  },
  rutas: {
    login: '/login',
    inicio: '/',
    sinAcceso: '/sin-acceso',
  },
  notificaciones: {
    duracion: 4200,
    duracionError: 6000,
  },
  sesion: {
    /** Margen (ms) antes del vencimiento real para forzar el cierre. */
    margenExpiracionMs: 0,
  },
} as const;
