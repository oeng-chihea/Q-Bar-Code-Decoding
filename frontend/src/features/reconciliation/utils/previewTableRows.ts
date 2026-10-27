import type { BarcodeResult, ExcelRowPreview, UnmatchedWaybill } from '../model/types';

export interface ReceiptPhoto {
  item: BarcodeResult;
  imageIndex: number;
}

export type PreviewTableRow =
  | { kind: 'excel'; key: string; row: ExcelRowPreview; photos: ReceiptPhoto[] }
  | { kind: 'waybill'; key: string; waybill: UnmatchedWaybill; photos: ReceiptPhoto[] };

const normalizeCode = (value: string | undefined) =>
  (value || '').trim().replace(/[\s_\-/:()!']+/g, '').toLowerCase();

const scannedCodes = (item: BarcodeResult) =>
  [item.decodedValue, ...(item.allExtractedValues ?? [])].map(normalizeCode).filter(Boolean);

function findPhotos(
  scanResults: BarcodeResult[],
  code: string | undefined,
  accept: (item: BarcodeResult) => boolean,
): ReceiptPhoto[] {
  const wanted = normalizeCode(code);
  if (!wanted) return [];
  const photos = scanResults
    .map((item, imageIndex) => ({ item, imageIndex }))
    .filter(({ item }) => item.success && accept(item) && scannedCodes(item).includes(wanted));
  return [
    ...photos.filter(({ item }) => !item.duplicate),
    ...photos.filter(({ item }) => item.duplicate),
  ];
}

export function findDuplicateSource(
  duplicate: BarcodeResult,
  scanResults: BarcodeResult[],
  previewRows: ExcelRowPreview[],
): { original?: BarcodeResult; rowIndex?: number } {
  const code = normalizeCode(duplicate.decodedValue);
  if (!code) return {};
  const original = scanResults.find(
    (item) => item !== duplicate && item.matched === true && scannedCodes(item).includes(code),
  );
  const row = previewRows.find((r) => r.matched && normalizeCode(r.barcodeValue) === code);
  return { original, rowIndex: row?.rowIndex };
}

export function buildPreviewTableRows(
  previewRows: ExcelRowPreview[],
  unmatchedWaybills: UnmatchedWaybill[],
  scanResults: BarcodeResult[],
): PreviewTableRow[] {
  const excelRows: PreviewTableRow[] = previewRows.map((row) => ({
    kind: 'excel',
    key: `row:${row.rowIndex}`,
    row,
    photos: row.matched
      ? findPhotos(scanResults, row.barcodeValue, (item) => item.matched !== false || item.duplicate === true)
      : [],
  }));

  const waybillRows: PreviewTableRow[] = unmatchedWaybills.map((waybill) => ({
    kind: 'waybill',
    key: `waybill:${waybill.waybillNo}`,
    waybill,
    photos: findPhotos(scanResults, waybill.waybillNo, (item) => item.matched !== true && !item.duplicate),
  }));

  return [...excelRows, ...waybillRows];
}

/** The customer's name from the row's receipt photo, or for an unmatched serial number from its saved receipt. */
export function customerNameOf(tableRow: PreviewTableRow): string | undefined {
  const fromPhoto = tableRow.photos.find(({ item }) => item.customerName)?.item.customerName;
  if (fromPhoto) return fromPhoto;
  return tableRow.kind === 'waybill' ? tableRow.waybill.customerName || undefined : undefined;
}
