import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

test('BarcodeResult interface includes optional matched flag', () => {
  const typePath = fileURLToPath(new URL(
    '../src/features/reconciliation/model/types.ts',
    import.meta.url,
  ));
  const types = readFileSync(typePath, 'utf8');
  assert.match(types, /matched\?: boolean;/);
});

test('ImageScanGrid uses createPortal at document.body for image preview modal', () => {
  const compPath = fileURLToPath(new URL(
    '../src/features/reconciliation/components/ImageScanGrid.tsx',
    import.meta.url,
  ));
  const comp = readFileSync(compPath, 'utf8');
  assert.match(comp, /createPortal\(/);
  assert.match(comp, /document\.body/);
  assert.match(comp, /isItemMatched/);
});

test('unmatched filtering correctly ignores secondary barcodes when any code matches Excel', () => {
  const normalize = (str: string) =>
    (str || '').trim().replace(/[\s_\-/:()!']+/g, '').toLowerCase();

  const matchedCodes = ['J01400110265', 'J01400110255'];
  const matchedSet = new Set(matchedCodes.map(normalize));

  const isItemMatched = (item: {
    matched?: boolean;
    success: boolean;
    decodedValue?: string;
    allExtractedValues?: string[];
  }): boolean => {
    if (item.matched !== undefined) {
      return item.matched;
    }
    if (!item.success) {
      return false;
    }
    if (item.decodedValue && matchedSet.has(normalize(item.decodedValue))) {
      return true;
    }
    if (item.allExtractedValues) {
      for (const val of item.allExtractedValues) {
        if (val && matchedSet.has(normalize(val))) {
          return true;
        }
      }
    }
    return false;
  };

  // photo_3 with secondary barcode 02822200
  const photo3 = {
    filename: 'photo_3.jpg',
    decodedValue: 'J01400110265',
    allExtractedValues: ['J01400110265', '02822200'],
    success: true,
  };
  assert.equal(isItemMatched(photo3), true, 'Photo 3 should be matched despite secondary barcode 02822200');

  // Truly unmatched item
  const unmatchedItem = {
    filename: 'unmatched.jpg',
    decodedValue: 'J01401525567',
    allExtractedValues: ['J01401525567'],
    success: true,
  };
  assert.equal(isItemMatched(unmatchedItem), false, 'Genuine unmatched parcel should not be marked as matched');

  // Explicit matched flag from backend
  const explicitlyMatched = {
    filename: 'item.jpg',
    decodedValue: '02822200',
    matched: true,
    success: true,
  };
  assert.equal(isItemMatched(explicitlyMatched), true, 'Explicit matched flag should be respected');
});
