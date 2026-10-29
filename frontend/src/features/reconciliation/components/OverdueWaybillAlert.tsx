import { AlertTriangle } from 'lucide-react';
import { useTranslation } from '@/shared/i18n/i18n';

interface OverdueWaybillAlertProps {
  overdueCount: number;
  overdueAfterDays: number;
  onView: () => void;
}

export const OverdueWaybillAlert = ({ overdueCount, overdueAfterDays, onView }: OverdueWaybillAlertProps) => {
  const { t } = useTranslation();

  if (overdueCount <= 0) return null;

  return (
    <div
      role="alert"
      className="min-w-0 bg-[#461B21]/60 border border-[#FB7185]/40 rounded-lg p-3.5 sm:p-4 flex flex-col gap-3 sm:flex-row sm:items-center text-left"
    >
      <AlertTriangle className="w-5 h-5 text-[#FB7185] shrink-0" />
      <p className="min-w-0 flex-1 break-words text-sm font-semibold text-[#FCA5A5] m-0">
        {t('waybill.alertTitle', { count: overdueCount, days: overdueAfterDays })}
      </p>
      <button
        type="button"
        onClick={onView}
        className="min-h-11 shrink-0 px-3.5 rounded-md border border-[#FB7185]/40 bg-[#5C2028] hover:bg-[#6E2530] text-xs font-semibold text-white transition cursor-pointer"
      >
        {t('waybill.alertView')}
      </button>
    </div>
  );
};
