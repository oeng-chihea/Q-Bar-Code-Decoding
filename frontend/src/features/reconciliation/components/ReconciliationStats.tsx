import {

  FileSpreadsheet,
  CheckCheck,
  ScanLine,
  Check,
} from 'lucide-react';
import type { ReconciliationResponse } from '@/features/reconciliation/model/types';
import { useTranslation } from '@/shared/i18n/i18n';

interface ReconciliationStatsProps {
  results: ReconciliationResponse;
}

export const ReconciliationStats = ({ results }: ReconciliationStatsProps) => {
  const { t } = useTranslation();


  return (
    <div className="min-w-0 space-y-4 text-left">
      {/* Top Banner */}
      <div className="min-w-0 bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-4 sm:p-5 flex items-start sm:items-center gap-3.5 shadow-sm">
        <div className="shrink-0 p-2.5 rounded-md bg-[#143827] text-[#34D399] border border-[#1E4D36]">
          <CheckCheck className="w-5 h-5" />
        </div>
        <div className="min-w-0">
          <h2 className="text-base font-bold text-white m-0">
            {t('results.completed')}
          </h2>
          <p className="break-words text-xs text-[#8E929E] m-0 mt-0.5">
            {t('results.completedDescription')}
          </p>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {/* Card 1: Images Scanned */}
        <div className="min-w-0 bg-[#1C1D22] border border-[#2B2D35] rounded-md p-3 sm:p-4">
          <div className="flex items-center justify-between text-[#8E929E] text-xs mb-2">
            <span className="min-w-0 break-words">{t('results.imagesUploaded')}</span>
            <ScanLine className="w-4 h-4 text-[#A0E3E2]" />
          </div>
          <div className="text-2xl font-bold text-white">
            {results.decodedImagesCount}
            <span className="text-sm font-normal text-[#8E929E]">/{results.totalImages}</span>
          </div>
          <div className="min-w-0 break-words text-[11px] text-[#A0E3E2] mt-1 font-medium">
            {t('results.scannedProcessed')}
          </div>
        </div>

        {/* Card 2: Excel Rows */}
        <div className="min-w-0 bg-[#1C1D22] border border-[#2B2D35] rounded-md p-3 sm:p-4">
          <div className="flex items-center justify-between text-[#8E929E] text-xs mb-2">
            <span className="min-w-0 break-words">{t('results.catalogRows')}</span>
            <FileSpreadsheet className="w-4 h-4 text-[#34D399]" />
          </div>
          <div className="text-2xl font-bold text-white">{results.excelTotalRows}</div>
          <div className="min-w-0 break-words text-[11px] text-[#8E929E] mt-1">
            {t('results.rows')}
          </div>
        </div>

        {/* Card 3: matches */}
        <div className=" border border-[#2B2D35] rounded-md p-3 sm:p-4 transition bg-[#0F3324] hover:bg-[#14402D]">
          <div className="flex items-center justify-between text-[#8E929E] text-xs mb-2">
            <span className="min-w-0 break-words">{t('matches.image')}</span>
            <Check className="w-4 h-4 text-[#34D399]" />
          </div>
          <div className="text-2xl font-bold text-[#A0E3E2]">
            {results.matchedRowsCount}
            <span className="text-sm font-normal text-[#8E929E]">/{results.totalImages}</span>
          </div>
          <div className="min-w-0 break-words text-[11px] text-[#8E929E] mt-1">
            {t('results.match')}
          </div>
        </div>
      </div>
    </div>
  );
};
