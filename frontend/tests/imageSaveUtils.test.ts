import test from 'node:test';
import assert from 'node:assert/strict';
import {
  inferImageMimeType,
  sanitizeImageFilename,
  normalizeImageFile,
  canShareImages,
  saveOrShareImage,
  saveOrShareBatch,
} from '../src/features/reconciliation/utils/imageSaveUtils.ts';

test('inferImageMimeType detects correct MIME types from filenames or existing types', () => {
  assert.equal(inferImageMimeType('photo.png'), 'image/png');
  assert.equal(inferImageMimeType('photo.jpg'), 'image/jpeg');
  assert.equal(inferImageMimeType('photo.jpeg'), 'image/jpeg');
  assert.equal(inferImageMimeType('photo.webp'), 'image/webp');
  assert.equal(inferImageMimeType('photo.bin'), 'image/jpeg');
  assert.equal(inferImageMimeType('unknown', 'image/png'), 'image/png');
  assert.equal(inferImageMimeType('unknown', 'application/octet-stream'), 'image/jpeg');
});

test('sanitizeImageFilename replaces .bin with .jpg and sanitizes characters', () => {
  assert.equal(sanitizeImageFilename('my image.png'), 'my_image.png');
  assert.equal(sanitizeImageFilename('unmatched-1.bin'), 'unmatched-1.jpg');
  assert.equal(sanitizeImageFilename('path/to/my/photo.jpeg'), 'photo.jpeg');
  assert.equal(sanitizeImageFilename('no-extension'), 'no-extension.jpg');
  assert.equal(sanitizeImageFilename('', 3), 'unmatched-image-3.jpg');
});

test('normalizeImageFile converts blob or file to safe image File', () => {
  const blob = new Blob(['123'], { type: 'application/octet-stream' });
  const file = normalizeImageFile(blob, 'unmatched.bin', 1);

  assert.equal(file.name, 'unmatched.jpg');
  assert.equal(file.type, 'image/jpeg');
});

test('canShareImages safely checks navigator support', () => {
  assert.equal(canShareImages([]), false);
  const file = new File(['a'], 'a.jpg', { type: 'image/jpeg' });
  // in node environment without share
  assert.equal(canShareImages([file]), false);
});


test('saveOrShareImage uses Web Share API when canShare is supported', async () => {
  const originalDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  const sharedArgs: unknown[] = [];

  const fakeNavigator = {
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)',
    canShare: () => true,
    share: async (args: unknown) => {
      sharedArgs.push(args);
    },
  };
  Object.defineProperty(globalThis, 'navigator', {
    value: fakeNavigator,
    configurable: true,
    writable: true,
  });

  try {
    const file = new File(['abc'], 'test.png', { type: 'image/png' });
    const result = await saveOrShareImage(file, 'test.png');

    assert.equal(result.shared, true);
    assert.equal(result.downloaded, false);
    assert.equal(result.canceled, false);
    assert.equal(sharedArgs.length, 1);
  } finally {
    if (originalDescriptor) {
      Object.defineProperty(globalThis, 'navigator', originalDescriptor);
    } else {
      delete (globalThis as typeof globalThis & { navigator?: unknown }).navigator;
    }
  }
});

test('saveOrShareImage treats AbortError as cancellation without error or auto-download', async () => {
  const originalDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');

  const fakeNavigator = {
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)',
    canShare: () => true,
    share: async () => {
      const err = new Error('Share canceled');
      err.name = 'AbortError';
      throw err;
    },
  };
  Object.defineProperty(globalThis, 'navigator', {
    value: fakeNavigator,
    configurable: true,
    writable: true,
  });

  try {
    const file = new File(['abc'], 'test.png', { type: 'image/png' });
    const result = await saveOrShareImage(file, 'test.png');

    assert.equal(result.shared, false);
    assert.equal(result.downloaded, false);
    assert.equal(result.canceled, true);
  } finally {
    if (originalDescriptor) {
      Object.defineProperty(globalThis, 'navigator', originalDescriptor);
    } else {
      delete (globalThis as typeof globalThis & { navigator?: unknown }).navigator;
    }
  }
});

test('saveOrShareBatch shares multiple files when canShare is supported', async () => {
  const originalDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  const sharedArgs: unknown[] = [];
  let fallbackCalled = false;

  const fakeNavigator = {
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)',
    canShare: () => true,
    share: async (args: unknown) => {
      sharedArgs.push(args);
    },
  };
  Object.defineProperty(globalThis, 'navigator', {
    value: fakeNavigator,
    configurable: true,
    writable: true,
  });

  try {
    const f1 = new File(['1'], 'img1.jpg', { type: 'image/jpeg' });
    const f2 = new File(['2'], 'img2.jpg', { type: 'image/jpeg' });
    const result = await saveOrShareBatch([f1, f2], async () => {
      fallbackCalled = true;
    });

    assert.equal(result.shared, true);
    assert.equal(result.downloaded, false);
    assert.equal(fallbackCalled, false);
    assert.equal(sharedArgs.length, 1);
  } finally {
    if (originalDescriptor) {
      Object.defineProperty(globalThis, 'navigator', originalDescriptor);
    } else {
      delete (globalThis as typeof globalThis & { navigator?: unknown }).navigator;
    }
  }
});
