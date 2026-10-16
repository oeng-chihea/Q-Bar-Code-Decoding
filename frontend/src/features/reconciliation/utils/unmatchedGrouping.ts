import type { BarcodeResult } from '../model/types';

export interface UnmatchedPhoto {
  item: BarcodeResult;
  /** Position in the original scanResults; the backend download endpoints use this index. */
  sourceIndex: number;
}

export interface UnmatchedGroup {
  key: string;
  barcode?: string;
  photos: UnmatchedPhoto[];
}

/**
 * One group per barcode (ignoring case and surrounding spaces).
 * Photos without a readable barcode are never merged, since there is nothing to group them by.
 */
export function groupUnmatchedByBarcode(
  scanResults: BarcodeResult[],
  isUnmatched: (item: BarcodeResult) => boolean,
  getBarcode: (item: BarcodeResult) => string | undefined,
): UnmatchedGroup[] {
  const groups = new Map<string, UnmatchedGroup>();

  scanResults.forEach((item, sourceIndex) => {
    if (!isUnmatched(item)) return;

    const barcode = getBarcode(item)?.trim() || undefined;
    const key = barcode ? `code:${barcode.toLowerCase()}` : `photo:${sourceIndex}`;
    const group = groups.get(key) ?? { key, barcode, photos: [] };
    group.photos.push({ item, sourceIndex });
    groups.set(key, group);
  });

  return [...groups.values()];
}
