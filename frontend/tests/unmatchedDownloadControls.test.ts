import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

test('ImageScanGrid renders guarded unmatched download control and download error banner', () => {
  const componentPath = fileURLToPath(new URL(
    '../src/features/reconciliation/components/ImageScanGrid.tsx',
    import.meta.url,
  ));
  const component = readFileSync(componentPath, 'utf8');

  assert.equal((component.match(/disabled=\{isDownloading \|\| !reconciliationId\}/g) ?? []).length, 1);
  assert.equal((component.match(/\{downloadError && \(/g) ?? []).length, 1);
  assert.ok(component.includes("handleDownloadUnmatched"));
  assert.ok(component.includes("downloadUnmatchedImages"));
  assert.ok(component.includes("downloadUnmatchedImage"));
  assert.ok(component.includes("downloadingImageIndex"));
});
