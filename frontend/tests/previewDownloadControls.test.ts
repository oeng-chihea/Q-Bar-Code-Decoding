import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

test('preview renders one guarded download control and one download error', () => {
  const componentPath = fileURLToPath(new URL(
    '../src/features/reconciliation/components/ExcelPreviewTable.tsx',
    import.meta.url,
  ));
  const component = readFileSync(componentPath, 'utf8');

  assert.equal((component.match(/disabled=\{isDownloading\}/g) ?? []).length, 1);
  assert.equal((component.match(/\{downloadError && \(/g) ?? []).length, 1);
});

