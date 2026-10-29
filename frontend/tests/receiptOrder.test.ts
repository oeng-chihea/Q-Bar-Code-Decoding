import test from 'node:test';
import assert from 'node:assert/strict';
import { newestReceiptFirst, rankedReceiptFilename } from '../src/features/reconciliation/utils/receiptOrder.ts';

test('orders receipts newest Create Date first, keeps same-day upload order and puts undated ones last', () => {
  const receipts = [
    { name: 'undated' },
    { name: 'older', date: '2026-10-20' },
    { name: 'newer', date: '2026-10-28' },
    { name: 'older-same-day', date: '2026-10-20' },
  ];

  const ordered = newestReceiptFirst(receipts, (r) => r.date);

  assert.deepEqual(ordered.map((r) => r.name), ['newer', 'older', 'older-same-day', 'undated']);
  assert.equal(receipts[0].name, 'undated', 'the input list is not reordered');
});

test('names downloaded receipts by rank and receipt date, like the backend ZIP', () => {
  assert.equal(rankedReceiptFilename(1, 4, '2026-10-28', 'photo_12.jpg'), '01_2026-10-28_photo_12.jpg');
  assert.equal(rankedReceiptFilename(4, 4, undefined, 'photo_5.jpg'), '04_photo_5.jpg');
  assert.equal(rankedReceiptFilename(7, 120, '2026-10-01', 'a.png'), '007_2026-10-01_a.png');
});
