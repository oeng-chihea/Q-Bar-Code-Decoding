import type { UnmatchedWaybill } from '@/features/reconciliation/model/types';
import { useTranslation } from '@/shared/i18n/i18n';

const STATUS_STYLES: Record<UnmatchedWaybill['status'], string> = {
  NEW: 'bg-[#1F3B3A] text-[#A0E3E2] border-[#2B5755]',
  ON_TRACK: 'bg-[#143827] text-[#34D399] border-[#1E4D36]',
  DUE_TODAY: 'bg-[#3B2A0B] text-[#FBBF24] border-[#5C4310]',
  OVERDUE: 'bg-[#461B21] text-[#FB7185] border-[#5C1D24]',
};

interface WaybillStatusBadgeProps {
  waybill: UnmatchedWaybill;
  overdueAfterDays: number;
}

export const WaybillStatusBadge = ({ waybill, overdueAfterDays }: WaybillStatusBadgeProps) => {
  const { t } = useTranslation();

  const label = {
    NEW: t('waybill.statusNew'),
    ON_TRACK: t('waybill.statusOnTrack', { days: waybill.daysLeft }),
    DUE_TODAY: t('waybill.statusDueToday'),
    OVERDUE: t('waybill.statusOverdue', { limit: overdueAfterDays, days: waybill.overdueDays }),
  }[waybill.status];

  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-semibold ${STATUS_STYLES[waybill.status]}`}
    >
      {label}
    </span>
  );
};
