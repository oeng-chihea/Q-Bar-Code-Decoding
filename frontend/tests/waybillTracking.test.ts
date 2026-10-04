import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  countWaybills,
  filterWaybills,
  findWaybill,
  pickInitialFilter,
} from '../src/features/reconciliation/utils/waybillTracking.ts';
import { translate } from '../src/shared/i18n/i18n.ts';

type Waybill = Parameters<typeof filterWaybills>[0][number];

const waybill = (overrides: Partial<Waybill>): Waybill => ({
  waybillNo: 'J0001',
  startDate: '2026-10-05',
  dueDate: '2026-10-11',
  daysOpen: 0,
  daysLeft: 6,
  overdueDays: 0,
  status: 'NEW',
  inThisUpload: true,
  ...overrides,
});

const waybills = [
  waybill({ waybillNo: 'NEW-1' }),
  waybill({ waybillNo: 'MID-1', status: 'ON_TRACK', daysOpen: 3, daysLeft: 3, inThisUpload: false }),
  waybill({ waybillNo: 'OLD-1', status: 'OVERDUE', daysOpen: 9, daysLeft: 0, overdueDays: 3, inThisUpload: false }),
  waybill({ waybillNo: 'OLD-2', status: 'OVERDUE', daysOpen: 7, daysLeft: 0, overdueDays: 1, inThisUpload: true }),
];

test('filters waybills by this upload, everything open, and overdue only', () => {
  assert.deepEqual(filterWaybills(waybills, 'thisUpload').map((w) => w.waybillNo), ['NEW-1', 'OLD-2']);
  assert.deepEqual(filterWaybills(waybills, 'overdue').map((w) => w.waybillNo), ['OLD-1', 'OLD-2']);
  assert.equal(filterWaybills(waybills, 'allOpen').length, 4);
});

test('counts waybills for every filter', () => {
  assert.deepEqual(countWaybills(waybills), { thisUpload: 2, allOpen: 4, overdue: 2 });
  assert.deepEqual(countWaybills([]), { thisUpload: 0, allOpen: 0, overdue: 0 });
});

test('opens on this upload, or on everything open when this upload has no unmatched waybills', () => {
  assert.equal(pickInitialFilter({ thisUpload: 2, allOpen: 4, overdue: 2 }), 'thisUpload');
  assert.equal(pickInitialFilter({ thisUpload: 0, allOpen: 3, overdue: 1 }), 'allOpen');
});

test('finds a tracked waybill for a scanned code ignoring case and spaces', () => {
  assert.equal(findWaybill(waybills, '  old-1 ')?.waybillNo, 'OLD-1');
  assert.equal(findWaybill(waybills, 'UNKNOWN'), undefined);
  assert.equal(findWaybill(waybills, undefined), undefined);
  assert.equal(findWaybill(waybills, '   '), undefined);
});

test('translates the waybill tracking labels in Khmer with the configured day limit', () => {
  assert.equal(
    translate('waybill.alertTitle', { count: 3, days: 6 }),
    'មាន 3 លេខវ៉ាយប៊ីលមិនត្រូវគ្នាលើស 6 ថ្ងៃ',
  );
  assert.equal(translate('waybill.statusOnTrack', { days: 2 }), 'នៅសល់ 2 ថ្ងៃ');
  assert.equal(translate('waybill.statusOverdue', { limit: 6, days: 7 }), 'លើស 6 ថ្ងៃ (+7)');
  assert.equal(translate('waybill.colOver', { days: 6 }), 'លើស 6 ថ្ងៃ');
});

test('models optional tracking fields so older backends without a database still work', () => {
  const types = readFileSync(
    fileURLToPath(new URL('../src/features/reconciliation/model/types.ts', import.meta.url)),
    'utf8',
  );
  assert.match(types, /export type WaybillStatus = 'NEW' \| 'ON_TRACK' \| 'DUE_TODAY' \| 'OVERDUE';/);
  assert.match(types, /trackingAvailable\?: boolean;/);
  assert.match(types, /unmatchedWaybills\?: UnmatchedWaybill\[\];/);
});

test('shows tracking only when the backend reports it is available and has waybills', () => {
  const app = readFileSync(fileURLToPath(new URL('../src/app/App.tsx', import.meta.url)), 'utf8');
  assert.match(app, /results\?\.trackingAvailable === true && waybills\.length > 0/);
  assert.match(app, /<OverdueWaybillAlert/);
  assert.match(app, /<UnmatchedWaybillTable/);
});
