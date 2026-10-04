import { useState } from 'react';
import type { UnmatchedWaybill } from '@/features/reconciliation/model/types';
import { WaybillStatusBadge } from '@/features/reconciliation/components/WaybillStatusBadge';
import {
  WAYBILL_TABLE_ID,
  countWaybills,
  filterWaybills,
  pickInitialFilter,
} from '@/features/reconciliation/utils/waybillTracking';
import type { WaybillFilter } from '@/features/reconciliation/utils/waybillTracking';
import { useTranslation } from '@/shared/i18n/i18n';

interface UnmatchedWaybillTableProps {
  waybills: UnmatchedWaybill[];
  overdueAfterDays: number;
  /** Filter to open with; defaults to this upload's waybills (or everything open when there are none). */
  initialFilter?: WaybillFilter;
}

export const UnmatchedWaybillTable = ({
  waybills,
  overdueAfterDays,
  initialFilter,
}: UnmatchedWaybillTableProps) => {
  const { t } = useTranslation();
  const counts = countWaybills(waybills);
  const [filter, setFilter] = useState<WaybillFilter>(() => initialFilter ?? pickInitialFilter(counts));
  const rows = filterWaybills(waybills, filter);

  const filters: { id: WaybillFilter; label: string }[] = [
    { id: 'thisUpload', label: t('waybill.filterThisUpload') },
    { id: 'allOpen', label: t('waybill.filterAllOpen') },
    { id: 'overdue', label: t('waybill.filterOverdue', { days: overdueAfterDays }) },
  ];

  return (
    <section
      id={WAYBILL_TABLE_ID}
      className="min-w-0 bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-4 sm:p-5 space-y-4 text-left"
    >
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <h3 className="break-words text-base font-bold text-white m-0">{t('waybill.title')}</h3>
          <p className="break-words text-xs text-[#8E929E] m-0 mt-1">
            {t('waybill.subtitle', { days: overdueAfterDays })}
          </p>
        </div>
        <div className="flex flex-wrap gap-2" role="group">
          {filters.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              onClick={() => setFilter(id)}
              aria-pressed={filter === id}
              className={`min-h-11 px-3 rounded-md border text-xs font-medium transition cursor-pointer ${
                filter === id
                  ? 'bg-[#1F3B3A] border-[#A0E3E2] text-[#A0E3E2]'
                  : 'bg-[#16171B] border-[#2B2D35] text-[#8E929E] hover:text-white'
              }`}
            >
              {label} <span className="font-bold">{counts[id]}</span>
            </button>
          ))}
        </div>
      </div>

      {rows.length === 0 ? (
        <p className="text-xs text-[#8E929E] italic m-0 py-4 text-center">{t('waybill.empty')}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[34rem] border-collapse text-sm">
            <thead>
              <tr className="text-left text-[11px] font-medium text-[#8E929E] border-b border-[#2B2D35]">
                <th className="py-2 pr-2 w-10">{t('waybill.colIndex')}</th>
                <th className="py-2 pr-3">{t('waybill.colWaybill')}</th>
                <th className="py-2 pr-3">{t('waybill.colStart')}</th>
                <th className="py-2 pr-3">{t('waybill.colDue')}</th>
                <th className="py-2">{t('waybill.colOver', { days: overdueAfterDays })}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((waybill, index) => {
                const isOverdue = waybill.status === 'OVERDUE';
                return (
                  <tr
                    key={waybill.waybillNo}
                    className={`border-b border-[#24252B] ${isOverdue ? 'bg-[#2A1519]' : ''}`}
                  >
                    <td className="py-2.5 pr-2 text-[#8E929E]">{index + 1}</td>
                    <td className="py-2.5 pr-3 font-mono font-semibold">
                      <span className={isOverdue ? 'text-[#FB7185]' : 'text-white'}>{waybill.waybillNo}</span>
                      {!waybill.inThisUpload && (
                        <span className="block text-[10px] font-sans font-normal text-[#737887]">
                          {t('waybill.earlierUpload')}
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 pr-3 font-mono text-[#F3F4F6]">{waybill.startDate}</td>
                    <td className={`py-2.5 pr-3 font-mono ${isOverdue ? 'text-[#FB7185]' : 'text-[#F3F4F6]'}`}>
                      {waybill.dueDate}
                    </td>
                    <td className="py-2.5">
                      <WaybillStatusBadge waybill={waybill} overdueAfterDays={overdueAfterDays} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};
