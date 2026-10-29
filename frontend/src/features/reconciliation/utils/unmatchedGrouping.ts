import type { BarcodeResult } from '../model/types';
import { newestReceiptFirst } from './receiptOrder.ts';

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
 * One group per barcode (ignoring case and surrounding spaces), newest receipt date first.
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

  return newestReceiptFirst([...groups.values()], newestCreateDate);
}

function newestCreateDate(group: UnmatchedGroup): string | undefined {
  return newestReceiptFirst(group.photos, ({ item }) => item.createDate)[0]?.item.createDate;
}
