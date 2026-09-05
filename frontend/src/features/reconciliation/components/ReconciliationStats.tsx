import {
  CheckCircle2,
  FileSpreadsheet,
  Zap,
  CheckCheck,
  ScanLine,
} from 'lucide-react';
import type { ReconciliationResponse } from '@/features/reconciliation/model/types';
import { useTranslation } from '@/shared/i18n/i18n';

interface ReconciliationStatsProps {
  results: ReconciliationResponse;
}

export const ReconciliationStats = ({ results }: ReconciliationStatsProps) => {
  const { t } = useTranslation();
  const matchRate =
    results.excelTotalRows > 0
      ? Math.round((results.matchedRowsCount / results.excelTotalRows) * 100)
      : 0;

  return (
    <div className="space-y-4 text-left">
      {/* Top Banner */}
      <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-5 flex items-center gap-3.5 shadow-sm">
        <div className="p-2.5 rounded-md bg-[#143827] text-[#34D399] border border-[#1E4D36]">
          <CheckCheck className="w-5 h-5" />
        </div>
        <div>
          <h2 className="text-base font-bold text-white m-0">
            {t('results.completed')}
          </h2>
          <p className="text-xs text-[#8E929E] m-0 mt-0.5">
            {t('results.completedDescription')}
          </p>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {/* Card 1: Images Scanned */}
        <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-md p-4">
          <div className="flex items-center justify-between text-[#8E929E] text-xs mb-2">
            <span>{t('results.imagesUploaded')}</span>
            <ScanLine className="w-4 h-4 text-[#A0E3E2]" />
          </div>
          <div className="text-2xl font-bold text-white">
            {results.decodedImagesCount}
            <span className="text-sm font-normal text-[#8E929E]">/{results.totalImages}</span>
          </div>
          <div className="text-[11px] text-[#A0E3E2] mt-1 font-medium">
            {t('results.scannedProcessed')}
          </div>
        </div>

        {/* Card 2: Excel Rows */}
        <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-md p-4">
          <div className="flex items-center justify-between text-[#8E929E] text-xs mb-2">
            <span>{t('results.catalogRows')}</span>
            <FileSpreadsheet className="w-4 h-4 text-[#34D399]" />
          </div>
          <div className="text-2xl font-bold text-white">{results.excelTotalRows}</div>
          <div className="text-[11px] text-[#8E929E] mt-1">
            {results.activeSheetName ? (
              <span>{t('results.sheet')}: <strong className="text-[#F3F4F6]">{results.activeSheetName}</strong></span>
            ) : (
              <span>{t('results.target')}: <strong className="text-[#F3F4F6]">{results.matchedColumnName}</strong></span>
            )}
          </div>
        </div>

        {/* Card 3: Matched & Highlighted in Red */}
        <div className="bg-[#1C1D22] border border-[#461B21] rounded-md p-4 bg-[#23171B]/50">
          <div className="flex items-center justify-between text-[#FB7185] text-xs mb-2">
            <span>{t('results.highlightedRed')}</span>
            <CheckCircle2 className="w-4 h-4 text-[#FB7185]" />
          </div>
          <div className="text-2xl font-bold text-[#FB7185]">{results.matchedRowsCount}</div>
          <div className="text-[11px] text-[#FCA5A5] mt-1 font-medium">
            {t('results.matchRate', { rate: matchRate })}
          </div>
        </div>

        {/* Card 4: Duration */}
        <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-md p-4">
          <div className="flex items-center justify-between text-[#8E929E] text-xs mb-2">
            <span>{t('results.processingTime')}</span>
            <Zap className="w-4 h-4 text-[#A0E3E2]" />
          </div>
          <div className="text-2xl font-bold text-[#A0E3E2]">
            {results.executionTimeMs}
            <span className="text-sm font-normal text-[#8E929E]"> ms</span>
          </div>
          <div className="text-[11px] text-[#8E929E] mt-1">
            {t('results.fast')}
          </div>
        </div>
      </div>
    </div>
  );
};
