/**
 * Newest receipt CREATED date (yyyy-MM-dd) first; receipts without a date go last.
 * The sort is stable, so receipts from the same day keep their upload order.
 * Mirrors the order the backend uses for the unmatched downloads.
 */
export function newestReceiptFirst<T>(items: T[], createDateOf: (item: T) => string | undefined): T[] {
  return [...items].sort((a, b) => {
    const dateA = createDateOf(a) || '';
    const dateB = createDateOf(b) || '';
    if (dateA === dateB) return 0;
    if (!dateA) return 1;
    if (!dateB) return -1;
    return dateA < dateB ? 1 : -1;
  });
}

/** e.g. "03_2026-10-28_photo_12.jpg", or "07_photo_5.jpg" when the receipt date is unknown; same as the backend ZIP. */
export function rankedReceiptFilename(rank: number, total: number, createDate: string | undefined, filename: string): string {
  const prefix = `${String(rank).padStart(Math.max(2, String(total).length), '0')}_`;
  return createDate ? `${prefix}${createDate}_${filename}` : `${prefix}${filename}`;
}
