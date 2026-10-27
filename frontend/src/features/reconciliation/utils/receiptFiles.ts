import type { BarcodeResult } from '../model/types';

export function findReceiptFile(
  item: BarcodeResult,
  index: number,
  scanResults: BarcodeResult[],
  imageFiles?: File[],
): File | undefined {
  if (!imageFiles || imageFiles.length === 0) return undefined;

  if (item.filename) {
    const match = imageFiles.find((f) => {
      if (f.name === item.filename) return true;
      if (f.name.toLowerCase().trim() === item.filename.toLowerCase().trim()) return true;
      const baseName = item.filename.split(/[/\\]/).pop();
      if (baseName && (f.name === baseName || f.name.toLowerCase().trim() === baseName.toLowerCase().trim())) {
        return true;
      }
      try {
        const decoded = decodeURIComponent(item.filename);
        if (f.name === decoded || f.name.toLowerCase().trim() === decoded.toLowerCase().trim()) {
          return true;
        }
      } catch {
        // ignore malformed URI components
      }
      return false;
    });
    if (match) return match;
  }

  // Fallback by 1:1 original position in scanResults
  const originalIndex = scanResults.indexOf(item);
  const targetIdx = originalIndex >= 0 ? originalIndex : index;
  if (targetIdx >= 0 && targetIdx < imageFiles.length) {
    return imageFiles[targetIdx];
  }

  return undefined;
}
