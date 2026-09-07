import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

test('models the active table-image Excel source type', () => {
  const typePath = fileURLToPath(new URL(
    '../src/features/reconciliation/model/types.ts',
    import.meta.url,
  ));
  const types = readFileSync(typePath, 'utf8');

  assert.match(types, /excelSourceType\?: 'EXCEL_TABLE_IMAGE';/);
  assert.doesNotMatch(types, /'EXCEL_FILE'/);
});

test('models the active Gemini barcode decoder response type', () => {
  const typePath = fileURLToPath(new URL(
    '../src/features/reconciliation/model/types.ts',
    import.meta.url,
  ));
  const types = readFileSync(typePath, 'utf8');

  assert.match(types, /decoderType: 'ZXING' \| 'GEMINI_AI' \| 'FAILED';/);
  assert.doesNotMatch(types, /'OLLAMA_AI'/);
});
