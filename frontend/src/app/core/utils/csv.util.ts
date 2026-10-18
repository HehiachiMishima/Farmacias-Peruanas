/**
 * Exporta datos tabulares a un archivo CSV compatible con Excel.
 * Usa BOM UTF-8 (para que los acentos se vean correctos) y `;` como
 * separador (Excel en configuración regional en español), con escapes
 * RFC 4180 para comillas, separador y saltos de línea.
 */
export function exportarCsv(
  nombreArchivo: string,
  cabeceras: string[],
  filas: (string | number | null | undefined)[][],
): void {
  const lineas = [cabeceras, ...filas].map((fila) => fila.map(celdaCsv).join(';'));
  const contenido = `\uFEFF${lineas.join('\r\n')}`;
  const blob = new Blob([contenido], { type: 'text/csv;charset=utf-8;' });
  descargar(blob, nombreArchivo.toLowerCase().endsWith('.csv') ? nombreArchivo : `${nombreArchivo}.csv`);
}

function celdaCsv(valor: string | number | null | undefined): string {
  const texto = valor === null || valor === undefined ? '' : String(valor);
  return `"${texto.replace(/"/g, '""')}"`;
}

function descargar(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  URL.revokeObjectURL(url);
}
