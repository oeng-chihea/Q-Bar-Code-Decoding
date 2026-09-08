import test from 'node:test';
import assert from 'node:assert/strict';
import {
  reconciliationDownloadPath,
  reconciliationResultPath,
  reconciliationStatusPath,
  reconciliationUnmatchedImageDownloadPath,
  reconciliationUnmatchedDownloadPath,
} from '../src/features/reconciliation/api/reconciliationApiPaths.ts';
import {
  downloadUnmatchedImage,
  downloadUnmatchedImages,
  waitForReconciliation,
} from '../src/features/reconciliation/api/reconciliationApi.ts';

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
  assert.equal(
    reconciliationUnmatchedDownloadPath(reconciliationId),
    '/api/v1/barcode-reconciliations/reconciliation%2Fwith%20spaces/download-unmatched',
  );
  assert.equal(
    reconciliationUnmatchedImageDownloadPath(reconciliationId, 2),
    '/api/v1/barcode-reconciliations/reconciliation%2Fwith%20spaces/download-unmatched/2',
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

test('downloads each unmatched raw image from the manifest', async () => {
  const originalFetch = globalThis.fetch;
  const originalWindow = (globalThis as typeof globalThis & { window?: unknown }).window;
  const originalDocument = (globalThis as typeof globalThis & { document?: unknown }).document;
  const requestedPaths: string[] = [];
  const downloadedNames: string[] = [];
  const revokedUrls: string[] = [];

  const links: Array<{ click: () => void }> = [];
  const fakeWindow = {
    URL: {
      createObjectURL: () => 'blob:unmatched-test',
      revokeObjectURL: (url: string) => revokedUrls.push(url),
    },
  };
  const fakeDocument = {
    body: {
      appendChild: () => undefined,
      removeChild: () => undefined,
    },
    createElement: () => {
      const link = {
        href: '',
        download: '',
        rel: '',
        click: () => downloadedNames.push(link.download),
      };
      links.push(link);
      return link;
    },
  };

  (globalThis as typeof globalThis & { window?: unknown }).window = fakeWindow;
  (globalThis as typeof globalThis & { document?: unknown }).document = fakeDocument;
  globalThis.fetch = async (input) => {
    const path = String(input);
    requestedPaths.push(path);
    if (path.endsWith('/download-unmatched')) {
      return new Response(JSON.stringify({
        images: [
          {
            imageIndex: 1,
            filename: 'item-2.png',
            contentType: 'image/png',
            size: 3,
            downloadUrl: '/api/v1/barcode-reconciliations/rec-1/download-unmatched/1',
          },
          {
            imageIndex: 4,
            filename: 'item-5.jpg',
            contentType: 'image/jpeg',
            size: 3,
            downloadUrl: '/api/v1/barcode-reconciliations/rec-1/download-unmatched/4',
          },
        ],
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    }
    return new Response(new Uint8Array([1, 2, 3]), {
      status: 200,
      headers: { 'content-type': 'image/octet-stream' },
    });
  };

  try {
    await downloadUnmatchedImages('rec-1');
    await downloadUnmatchedImage('rec-1', 9, 'single-image.webp');

    assert.deepEqual(requestedPaths, [
      '/api/v1/barcode-reconciliations/rec-1/download-unmatched',
      '/api/v1/barcode-reconciliations/rec-1/download-unmatched/1',
      '/api/v1/barcode-reconciliations/rec-1/download-unmatched/4',
      '/api/v1/barcode-reconciliations/rec-1/download-unmatched/9',
    ]);
    assert.deepEqual(downloadedNames, ['item-2.png', 'item-5.jpg', 'single-image.webp']);
    assert.equal(links.length, 3);
    assert.deepEqual(revokedUrls, []);
  } finally {
    globalThis.fetch = originalFetch;
    if (originalWindow === undefined) {
      delete (globalThis as typeof globalThis & { window?: unknown }).window;
    } else {
      (globalThis as typeof globalThis & { window?: unknown }).window = originalWindow;
    }
    if (originalDocument === undefined) {
      delete (globalThis as typeof globalThis & { document?: unknown }).document;
    } else {
      (globalThis as typeof globalThis & { document?: unknown }).document = originalDocument;
    }
  }
});
