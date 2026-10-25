import { AlertTriangle } from 'lucide-react';
import type { BarcodeResult } from '@/features/reconciliation/model/types';
import { useTranslation } from '@/shared/i18n/i18n';

interface ReceiptDateAlertProps {
  scanResults: BarcodeResult[];
}

export const ReceiptDateAlert = ({ scanResults }: ReceiptDateAlertProps) => {
  const { t } = useTranslation();

  const problems = scanResults.flatMap((item) =>
    item.success && item.createDateProblem ? [{ ...item, problem: item.createDateProblem }] : [],
  );
  if (problems.length === 0) return null;

  return (
    <div
      role="alert"
      className="min-w-0 bg-[#3A2A10]/60 border border-[#FBBF24]/40 rounded-lg p-3.5 sm:p-4 flex gap-3 text-left"
    >
      <AlertTriangle className="w-5 h-5 text-[#FBBF24] shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="break-words text-sm font-semibold text-[#FCD34D] m-0">
          {t('receiptDate.alertTitle', { count: problems.length })}
        </p>
        <p className="break-words text-xs text-[#FCD34D]/80 m-0 mt-0.5">{t('receiptDate.alertDescription')}</p>
        <ul className="m-0 mt-2 pl-4 space-y-1 text-xs text-[#FDE68A]">
          {problems.map((item, index) => (
            <li key={`${item.filename}-${index}`} className="break-words">
              <span className="font-mono">{item.filename}</span>
              {' — '}
              {t(`receiptDate.problem.${item.problem}` as const,{ text: item.createDateText ?? '' })}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
};
