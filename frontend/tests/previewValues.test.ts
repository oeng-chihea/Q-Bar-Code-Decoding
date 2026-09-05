import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

test('preview renders backend cell values without outlet localization', () => {
  const componentPath = fileURLToPath(new URL(
    '../src/features/reconciliation/components/ExcelPreviewTable.tsx',
    import.meta.url,
  ));
  const source = readFileSync(componentPath, 'utf8');

  assert.match(source, /const val = row\.cells\[col\] \|\| '';/);
  assert.doesNotMatch(source, /localizePreviewCellValue|previewLocalization/);
});

test('preview exposes schema confidence without changing backend cell text', () => {
  const componentPath = fileURLToPath(new URL(
    '../src/features/reconciliation/components/ExcelPreviewTable.tsx',
    import.meta.url,
  ));
  const typePath = fileURLToPath(new URL(
    '../src/features/reconciliation/model/types.ts',
    import.meta.url,
  ));
  const component = readFileSync(componentPath, 'utf8');
  const types = readFileSync(typePath, 'utf8');

  assert.match(types, /matchedColumnConfidence\?: number;/);
  assert.match(component, /matchedColumnConfidence/);
});
