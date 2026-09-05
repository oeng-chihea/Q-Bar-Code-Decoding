import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_LANGUAGE, localizeError, translate } from '../src/shared/i18n/i18n.ts';

test('uses Khmer as the only supported interface language', () => {
  assert.equal(DEFAULT_LANGUAGE, 'km');
  assert.equal(translate('header.title'), 'កម្មវិធីផ្ទៀងផ្ទាត់បាកូដ Excel');
});

test('interpolates Khmer values and preserves language-neutral labels', () => {
  assert.equal(
    translate('preview.showing', { count: 3, matched: 1 }),
    'បង្ហាញ 3 ជួរ • 1 ជួរត្រូវគ្នាត្រូវបានបន្លិចជាពណ៌ក្រហម',
  );
});

test('localizes known backend errors in Khmer', () => {
  assert.equal(
    localizeError('Barcode images are not supported in the Excel section. Please upload barcode images in Step 2.'),
    'មិនអាចប្រើរូបភាពបាកូដជាតារាង Excel បានទេ។ សូមបញ្ចូលរូបភាពតារាង Excel នៅជំហាន 1 និងរូបភាពបាកូដនៅជំហាន 2។',
  );
  assert.equal(
    localizeError('Only images of an Excel table (.png, .jpg, .jpeg, .webp) are supported in the Excel upload.'),
    'ទម្រង់ឯកសារមិនគាំទ្រ។ សូមបញ្ចូលរូបភាពតារាង Excel (.png, .jpg, .jpeg, .webp) ប៉ុណ្ណោះ។',
  );
});

test('provides unmatched barcodes translation keys in Khmer', () => {
  assert.equal(translate('unmatched.title'), 'បាកូដដែលមិនត្រូវគ្នា');
  assert.equal(translate('unmatched.subtitle'), 'បង្ហាញបាកូដពីរូបភាពដែលមិនមាននៅក្នុងសៀវភៅបញ្ជី Excel');
  assert.equal(translate('unmatched.search'), 'ស្វែងរកបាកូដដែលមិនត្រូវគ្នា...');
  assert.equal(translate('unmatched.none'), 'បាកូដទាំងអស់ត្រូវបានផ្គូផ្គងជោគជ័យ (គ្មានបាកូដដែលនៅសល់)');
  assert.equal(translate('unmatched.preview'), 'ចុចដើម្បីមើលរូបភាព');
  assert.equal(translate('unmatched.previewTitle'), 'មើលរូបភាពបាកូដ');
});
