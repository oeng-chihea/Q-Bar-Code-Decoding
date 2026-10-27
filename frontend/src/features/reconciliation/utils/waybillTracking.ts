import type { UnmatchedWaybill } from '../model/types';

export const WAYBILL_TABLE_ID = 'unmatched-waybill-tracking';

/** Finds the tracked waybill for a scanned code, ignoring case and surrounding spaces. */
export function findWaybill(waybills: UnmatchedWaybill[], code: string | undefined): UnmatchedWaybill | undefined {
  const wanted = code?.trim().toLowerCase();
  if (!wanted) return undefined;
  return waybills.find((waybill) => waybill.waybillNo.trim().toLowerCase() === wanted);
}
