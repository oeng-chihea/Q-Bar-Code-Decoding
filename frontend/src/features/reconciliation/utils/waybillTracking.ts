import type { UnmatchedWaybill } from '../model/types';

export const WAYBILL_TABLE_ID = 'unmatched-waybill-tracking';

export type WaybillFilter = 'thisUpload' | 'allOpen' | 'overdue';

export type WaybillCounts = Record<WaybillFilter, number>;

export function filterWaybills(waybills: UnmatchedWaybill[], filter: WaybillFilter): UnmatchedWaybill[] {
  switch (filter) {
    case 'thisUpload':
      return waybills.filter((waybill) => waybill.inThisUpload);
    case 'overdue':
      return waybills.filter((waybill) => waybill.status === 'OVERDUE');
    case 'allOpen':
      return waybills;
  }
}

export function countWaybills(waybills: UnmatchedWaybill[]): WaybillCounts {
  return {
    thisUpload: filterWaybills(waybills, 'thisUpload').length,
    allOpen: waybills.length,
    overdue: filterWaybills(waybills, 'overdue').length,
  };
}

/** Start on this upload's waybills; fall back to everything open when this upload has none. */
export function pickInitialFilter(counts: WaybillCounts): WaybillFilter {
  return counts.thisUpload > 0 ? 'thisUpload' : 'allOpen';
}

/** Finds the tracked waybill for a scanned code, ignoring case and surrounding spaces. */
export function findWaybill(waybills: UnmatchedWaybill[], code: string | undefined): UnmatchedWaybill | undefined {
  const wanted = code?.trim().toLowerCase();
  if (!wanted) return undefined;
  return waybills.find((waybill) => waybill.waybillNo.trim().toLowerCase() === wanted);
}
