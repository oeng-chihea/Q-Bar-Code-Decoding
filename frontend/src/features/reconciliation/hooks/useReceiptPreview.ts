import { useState, useCallback } from 'react';
import type { BarcodeResult } from '../model/types';

export interface ReceiptPreview {
  url?: string;
  filename: string;
  barcode?: string;
  isFailed: boolean;
  item?: BarcodeResult;
  imageIndex?: number;
  file?: File;
}

export function useReceiptPreview() {
  const [preview, setPreview] = useState<ReceiptPreview | null>(null);

  const openPreview = useCallback((next: ReceiptPreview) => {
    const { file } = next;
    if (!file) {
      setPreview(next);
      return;
    }

    // 1. Read as permanent Data URL (completely immune to blob revocation / StrictMode)
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setPreview({ ...next, url: reader.result });
      }
    };
    reader.onerror = () => {
      setPreview({ ...next, url: URL.createObjectURL(file) });
    };
    reader.readAsDataURL(file);

    // Immediate preview with a fresh Object URL
    setPreview({ ...next, url: URL.createObjectURL(file) });
  }, []);

  return { preview, setPreview, openPreview };
}
