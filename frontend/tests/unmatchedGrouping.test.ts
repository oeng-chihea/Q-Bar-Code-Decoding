import test from 'node:test';
import assert from 'node:assert/strict';
import { groupUnmatchedByBarcode } from '../src/features/reconciliation/utils/unmatchedGrouping.ts';
import type { BarcodeResult } from '../src/features/reconciliation/model/types.ts';

const photo = (filename: string, decodedValue?: string, matched = false): BarcodeResult => ({
  filename,
  decodedValue,
  success: Boolean(decodedValue),
  decoderType: decodedValue ? 'ZXING' : 'FAILED',
  matched,
});

const unmatchedOnly = (item: BarcodeResult) => !item.matched;
const code = (item: BarcodeResult) => item.decodedValue;

test('merges photos with the same barcode, ignoring case and spaces', () => {
  const groups = groupUnmatchedByBarcode(
    [photo('a.jpg', 'J01415229036'), photo('b.jpg', ' j01415229036 ')],
    unmatchedOnly,
    code,
  );
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].photos.map((p) => p.sourceIndex), [0, 1]);
});

test('keeps the original scanResults index even when matched photos sit in between', () => {
  const groups = groupUnmatchedByBarcode(
    [photo('a.jpg', 'J1'), photo('b.jpg', 'J2', true), photo('c.jpg', 'J1')],
    unmatchedOnly,
    code,
  );
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].photos.map((p) => p.sourceIndex), [0, 2]);
});

test('keeps photos without a barcode separate', () => {
  const groups = groupUnmatchedByBarcode([photo('a.jpg'), photo('b.jpg')], unmatchedOnly, code);
  assert.equal(groups.length, 2);
  assert.ok(groups.every((g) => g.barcode === undefined));
});

test('different barcodes stay in different groups, in first-seen order', () => {
  const groups = groupUnmatchedByBarcode(
    [photo('a.jpg', 'J2'), photo('b.jpg', 'J1'), photo('c.jpg', 'J2')],
    unmatchedOnly,
    code,
  );
  assert.deepEqual(groups.map((g) => g.barcode), ['J2', 'J1']);
});
