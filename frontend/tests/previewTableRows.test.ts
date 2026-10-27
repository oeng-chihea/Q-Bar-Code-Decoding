import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  buildPreviewTableRows,
  findDuplicateSource,
} from '../src/features/reconciliation/utils/previewTableRows.ts';

type Args = Parameters<typeof buildPreviewTableRows>;
type Row = Args[0][number];
type Waybill = Args[1][number];
type Scan = Args[2][number];

const row = (rowIndex: number, barcodeValue: string, matched: boolean): Row => ({
  rowIndex,
  cells: { Barcode: barcodeValue },
  barcodeValue,
  matched,
});

const waybill = (waybillNo: string, overrides: Partial<Waybill> = {}): Waybill => ({
  waybillNo,
  startDate: '2026-10-20',
  dueDate: '2026-10-26',
  daysOpen: 2,
  daysLeft: 4,
  overdueDays: 0,
  status: 'ON_TRACK',
  inThisUpload: true,
  ...overrides,
});

const scan = (filename: string, decodedValue: string, overrides: Partial<Scan> = {}): Scan => ({
  filename,
  decodedValue,
  decoderType: 'ZXING',
  success: true,
  ...overrides,
});

test('lists Excel rows first, then one row per unmatched waybill', () => {
  const rows = buildPreviewTableRows(
    [row(2, 'A-1', true), row(3, 'A-2', false)],
    [waybill('W-1'), waybill('W-2', { status: 'OVERDUE', inThisUpload: false })],
    [],
  );

  assert.deepEqual(rows.map((r) => r.key), ['row:2', 'row:3', 'waybill:W-1', 'waybill:W-2']);
  assert.deepEqual(rows.map((r) => r.kind), ['excel', 'excel', 'waybill', 'waybill']);
});

test('links a matched Excel row to its receipt, ignoring spacing and case in the code', () => {
  const scans = [scan('other.jpg', 'ZZZ'), scan('receipt.jpg', 'j 0140-0110265', { matched: true })];
  const [matched, unmatched] = buildPreviewTableRows(
    [row(2, 'J01400110265', true), row(3, 'J01400110266', false)],
    [],
    scans,
  );

  assert.deepEqual(matched.photos.map((p) => [p.item.filename, p.imageIndex]), [['receipt.jpg', 1]]);
  assert.deepEqual(unmatched.photos, [], 'an Excel row without a match has no receipt');
});

test('lists a duplicate photo on its matched row, after the photo that claimed the row', () => {
  const scans = [
    scan('dup.jpg', 'X-9', { matched: false, duplicate: true }),
    scan('first.jpg', '02822200', { matched: true, allExtractedValues: ['02822200', 'X-9'] }),
  ];
  const [excel] = buildPreviewTableRows([row(5, 'X-9', true)], [], scans);

  assert.deepEqual(excel.photos.map((p) => p.item.filename), ['first.jpg', 'dup.jpg']);
});

test('links an unmatched waybill to its unmatched photo, and leaves earlier-upload waybills without one', () => {
  const scans = [
    scan('matched.jpg', 'W-1', { matched: true }),
    scan('unmatched.jpg', 'w-1', { matched: false }),
    scan('dup.jpg', 'W-1', { matched: false, duplicate: true }),
    scan('failed.jpg', 'W-2', { success: false }),
  ];
  const rows = buildPreviewTableRows([], [waybill('W-1'), waybill('W-2')], scans);

  assert.deepEqual(rows[0].photos.map((p) => [p.item.filename, p.imageIndex]), [['unmatched.jpg', 1]]);
  assert.deepEqual(rows[1].photos, [], 'a failed scan is never linked');
});

test('the spreadsheet table has View and tracking columns and no Status column or matched-only toggle', () => {
  const source = readFileSync(
    fileURLToPath(new URL('../src/features/reconciliation/components/ExcelPreviewTable.tsx', import.meta.url)),
    'utf8',
  );

  assert.match(source, /t\('preview\.colView'\)/);
  assert.match(source, /t\('preview\.colOver', \{ days: overdueAfterDays \}\)/);
  assert.match(source, /t\('preview\.colUnmatchedWaybill'\)/);
  assert.match(source, /<WaybillStatusBadge/);
  assert.match(source, /<ReceiptPreviewModal/);
  assert.match(source, /id=\{WAYBILL_TABLE_ID\}/);
  assert.doesNotMatch(source, /preview\.(status|matchRed|showMatched|showAll)'/);
  assert.doesNotMatch(source, /onlyMatched/);
});

test('names the photo and Excel row a duplicate repeats', () => {
  const original = scan('photo_7.jpg', 'J01419454153', { matched: true });
  const duplicate = scan('photo_16.jpg', 'j0141 9454153', { matched: false, duplicate: true });
  const rows = [row(3, 'J01419454000', false), row(4, 'J01419454153', true)];

  const source = findDuplicateSource(duplicate, [duplicate, scan('x.jpg', 'ZZ'), original], rows);
  assert.equal(source.original, original);
  assert.equal(source.rowIndex, 4);

  assert.deepEqual(findDuplicateSource(scan('lost.jpg', 'NOPE', { duplicate: true }), [original], rows), {
    original: undefined,
    rowIndex: undefined,
  });
});

test('duplicate cards stay in the unmatched section with a notice naming the original photo', () => {
  const grid = readFileSync(
    fileURLToPath(new URL('../src/features/reconciliation/components/ImageScanGrid.tsx', import.meta.url)),
    'utf8',
  );

  assert.match(grid, /return !isItemMatched\(result\);/);
  assert.match(grid, /t\('unmatched\.duplicateTag'\)/);
  assert.match(grid, /t\('unmatched\.duplicateOf', \{ filename: duplicateSource\.original\.filename \}\)/);
});
