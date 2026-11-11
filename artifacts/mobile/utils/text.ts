/** Lower-case and strip accents, so "developpe" matches "Développé". */
export function normalizeSearch(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
}
