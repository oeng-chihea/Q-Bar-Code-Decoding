import test from 'node:test';
import assert from 'node:assert/strict';
import {
  reconciliationDownloadPath,
  reconciliationResultPath,
  reconciliationStatusPath,
} from '../src/features/reconciliation/api/reconciliationApiPaths.ts';
import { waitForReconciliation } from '../src/features/reconciliation/api/reconciliationApi.ts';

test('builds encoded reconciliation endpoint paths', () => {
  const reconciliationId = 'reconciliation/with spaces';

  assert.equal(
    reconciliationStatusPath(reconciliationId),
    '/api/v1/barcode-reconciliations/reconciliation%2Fwith%20spaces',
  );
  assert.equal(
    reconciliationResultPath(reconciliationId),
    '/api/v1/barcode-reconciliations/reconciliation%2Fwith%20spaces/result',
  );
  assert.equal(
    reconciliationDownloadPath(reconciliationId),
    '/api/v1/barcode-reconciliations/reconciliation%2Fwith%20spaces/download',
  );
});

test('polls until a reconciliation completes', async () => {
  const originalFetch = globalThis.fetch;
  const requestedPaths: string[] = [];
  let requestCount = 0;

  globalThis.fetch = async (input) => {
    requestedPaths.push(String(input));
    requestCount += 1;
    const status = requestCount === 1 ? 'PROCESSING' : 'COMPLETED';
    return new Response(JSON.stringify({
      reconciliationId: 'reconciliation-1',
      status,
      stage: status === 'PROCESSING' ? 'Running reconciliation pipeline' : 'Completed',
      resultAvailable: status === 'COMPLETED',
    }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    });
  };

  try {
    const result = await waitForReconciliation('reconciliation-1', {
      intervalMs: 0,
      timeoutMs: 100,
    });

    assert.equal(result.status, 'COMPLETED');
    assert.deepEqual(requestedPaths, [
      '/api/v1/barcode-reconciliations/reconciliation-1',
      '/api/v1/barcode-reconciliations/reconciliation-1',
    ]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
