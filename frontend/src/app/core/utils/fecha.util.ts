/** Fecha local en formato ISO corto («2026-10-07»), el que usan los `<input type="date">`. */
export function fechaIso(fecha: Date): string {
  const mes = `${fecha.getMonth() + 1}`.padStart(2, '0');
  const dia = `${fecha.getDate()}`.padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/** Fecha local de hoy en formato ISO corto. */
export function hoy(): string {
  return fechaIso(new Date());
}
