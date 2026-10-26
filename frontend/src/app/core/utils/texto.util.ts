/** Texto comparable: minúsculas y sin tildes («José» → «jose»). */
export function normalizarTexto(texto: string | null | undefined): string {
  return (texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/** true si el término está vacío o aparece en algún campo, sin distinguir mayúsculas ni tildes. */
export function contieneTexto(
  campos: ReadonlyArray<string | null | undefined>,
  termino: string,
): boolean {
  const buscado = normalizarTexto(termino.trim());
  return !buscado || campos.some((campo) => normalizarTexto(campo).includes(buscado));
}
