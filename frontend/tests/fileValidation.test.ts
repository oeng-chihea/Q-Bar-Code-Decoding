import test from 'node:test';
import assert from 'node:assert/strict';
import { validateExcelUpload } from '../src/features/reconciliation/model/fileValidation.ts';

test('rejects native spreadsheet files (.xlsx, .xls, .csv) for the Excel upload', () => {
  for (const [name, type] of [
    ['inventory.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
    ['inventory.xls', 'application/vnd.ms-excel'],
    ['inventory.csv', 'text/csv'],
  ] as const) {
    const result = validateExcelUpload(new File(['spreadsheet'], name, { type }));

    assert.equal(result.accepted, false);
    assert.equal(result.kind, 'unsupported');
    assert.equal(result.errorKey, 'errors.unsupported');
  }
});

test('accepts table image files for the Excel upload', () => {
  for (const [name, type] of [
    ['inventory.png', 'image/png'],
    ['inventory.jpg', 'image/jpeg'],
    ['inventory.jpeg', 'image/jpeg'],
    ['inventory.webp', 'image/webp'],
  ] as const) {
    const result = validateExcelUpload(new File(['image'], name, { type }));

    assert.equal(result.accepted, true);
    assert.equal(result.kind, 'table-image');
  }
});

test('accepts an image MIME type when the filename looks like Excel', () => {
  const result = validateExcelUpload(new File(['image'], 'inventory.xlsx', { type: 'image/png' }));

  assert.equal(result.accepted, true);
  assert.equal(result.kind, 'table-image');
});

test('rejects unsupported files before upload', () => {
  const result = validateExcelUpload(new File(['text'], 'inventory.txt', { type: 'text/plain' }));

  assert.equal(result.accepted, false);
  assert.equal(result.kind, 'unsupported');
});
