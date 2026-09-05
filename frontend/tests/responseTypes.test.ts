import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

test('models native and table-image Excel source types', () => {
  const typePath = fileURLToPath(new URL(
    '../src/features/reconciliation/model/types.ts',
    import.meta.url,
  ));
  const types = readFileSync(typePath, 'utf8');

  assert.match(types, /excelSourceType\?: 'EXCEL_FILE' \| 'EXCEL_TABLE_IMAGE';/);
});
