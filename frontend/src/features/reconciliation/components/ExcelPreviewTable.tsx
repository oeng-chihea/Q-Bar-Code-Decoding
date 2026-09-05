import { useState } from 'react';
import {
  FileSpreadsheet,
  Download,
  Filter,
  CheckCircle2,
  Layers,
} from 'lucide-react';
import type { ExcelRowPreview } from '@/features/reconciliation/model/types';
import { downloadBase64Excel } from '@/features/reconciliation/api/reconciliationApi';
import { useTranslation } from '@/shared/i18n/i18n';

interface ExcelPreviewTableProps {
  columns: string[];
  previewRows: ExcelRowPreview[];
  matchedColumnName: string;
  matchedColumnConfidence?: number;
  activeSheetName?: string;
  totalRows: number;
  matchedCount: number;
  highlightedExcelBase64: string;
  downloadFileName: string;
}

export const ExcelPreviewTable = ({
  columns,
  previewRows,
  matchedColumnName,
  matchedColumnConfidence,
  activeSheetName,
  totalRows,
  matchedCount,
  highlightedExcelBase64,
  downloadFileName,
}: ExcelPreviewTableProps) => {
  const { t } = useTranslation();
  const [onlyMatched, setOnlyMatched] = useState(false);

  const displayRows = onlyMatched
    ? previewRows.filter((r) => r.matched)
    : previewRows;

  const handleDownload = () => {
    downloadBase64Excel(highlightedExcelBase64, downloadFileName);
  };

  return (
    <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-5 text-left space-y-4">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#26272E] pb-4">
        <div className="flex items-center gap-2">
          <FileSpreadsheet className="w-5 h-5 text-[#34D399]" />
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider m-0 flex items-center gap-2 flex-wrap">
              <span>{t('preview.title')}</span>
              {activeSheetName && (
                <span className="text-xs px-2 py-0.5 rounded bg-[#1A2333] text-[#818CF8] border border-[#27354E] font-medium flex items-center gap-1">
                  <Layers className="w-3 h-3" />
                  {t('preview.sheet', { name: activeSheetName })}
                </span>
              )}
              <span className="text-xs px-2 py-0.5 rounded bg-[#143827] text-[#34D399] border border-[#1E4D36] font-normal">
                {t('preview.target', { name: matchedColumnName })}
              </span>
              {matchedColumnConfidence !== undefined && matchedColumnConfidence > 0 && (
                <span className="text-xs px-2 py-0.5 rounded bg-[#1A2333] text-[#A0E3E2] border border-[#27354E] font-normal">
                  {t('preview.confidence', { rate: Math.round(matchedColumnConfidence * 100) })}
                </span>
              )}
            </h3>
            <p className="text-xs text-[#8E929E] m-0 mt-0.5">
              {t('preview.showing', { count: previewRows.length, matched: matchedCount })}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Toggle Filter */}
          <button
            onClick={() => setOnlyMatched(!onlyMatched)}
            className={`px-3 py-1.5 rounded-md border text-xs font-medium transition cursor-pointer flex items-center gap-1.5 ${
              onlyMatched
                ? 'bg-[#461B21] text-[#FB7185] border-[#5C2028]'
                : 'bg-[#16171B] text-[#8E929E] border-[#2B2D35] hover:text-[#F3F4F6]'
            }`}
          >
            <Filter className="w-3 h-3" />
            {onlyMatched ? t('preview.showMatched') : t('preview.showAll')}
          </button>

          {/* Download Action */}
          <button
            onClick={handleDownload}
            className="px-3.5 py-1.5 rounded-md border border-[#A0E3E2]/40 text-xs font-semibold text-[#0E1726] bg-[#A0E3E2] hover:bg-[#8EE0DF] transition shadow-sm flex items-center gap-1.5 cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            {t('preview.download')}
          </button>
        </div>
      </div>

      {/* Spreadsheet Table */}
      <div className="overflow-x-auto rounded-md border border-[#2B2D35] bg-[#16171B]">
        <table className="w-full text-xs text-left border-collapse">
          <thead>
            <tr className="bg-[#1C1D22] border-b border-[#2B2D35] text-[#8E929E]">
              <th className="py-2.5 px-3.5 font-semibold w-12 text-[#737887] border-r border-[#2B2D35]">
                #
              </th>
              <th className="py-2.5 px-3.5 font-semibold w-24 border-r border-[#2B2D35]">
                {t('preview.status')}
              </th>
              {columns.map((col, idx) => {
                const isTarget = col.toLowerCase() === matchedColumnName.toLowerCase();
                return (
                  <th
                    key={idx}
                    className={`py-2.5 px-4 font-semibold border-r border-[#2B2D35] whitespace-nowrap ${
                      isTarget ? 'text-[#A0E3E2] bg-[#162729]/50' : ''
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>{col}</span>
                      {isTarget && (
                        <span className="text-[10px] px-1.5 py-0.2 rounded bg-[#143827] text-[#34D399] border border-[#1E4D36]">
                          {t('preview.targetLabel')}
                        </span>
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-[#24252B]">
            {displayRows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length + 2}
                  className="py-8 text-center text-[#737887] text-xs"
                >
                  {t('preview.noRows')}
                </td>
              </tr>
            ) : (
              displayRows.map((row) => {
                return (
                  <tr
                    key={row.rowIndex}
                    className={`transition ${
                      row.matched
                        ? 'bg-[#331418]/60 text-[#FEE2E2] hover:bg-[#40181D] font-medium'
                        : 'hover:bg-[#1C1D22] text-[#D1D5DB]'
                    }`}
                  >
                    <td className="py-2 px-3.5 text-[#737887] font-mono border-r border-[#24252B]">
                      {row.rowIndex}
                    </td>
                    <td className="py-2 px-3.5 border-r border-[#24252B]">
                      {row.matched ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-[#143827] text-[#34D399] border border-[#1E4D36]">
                          <CheckCircle2 className="w-3 h-3" />
                          {t('preview.matchRed')}
                        </span>
                      ) : (
                        <span className="text-[10px] text-[#737887] font-normal">
                          &mdash;
                        </span>
                      )}
                    </td>
                    {columns.map((col, cIdx) => {
                      const val = row.cells[col] || '';
                      const isTarget = col.toLowerCase() === matchedColumnName.toLowerCase();
                      return (
                        <td
                          key={cIdx}
                          className={`py-2.5 px-4 border-r border-[#24252B] whitespace-nowrap font-mono ${
                            isTarget && row.matched
                              ? 'text-[#FCA5A5] font-bold bg-[#541920]/40'
                              : isTarget
                              ? 'text-[#F3F4F6]'
                              : ''
                          }`}
                        >
                          {val || <span className="text-[#5A5E6B] italic font-sans">null</span>}
                        </td>
                      );
                    })}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {totalRows > previewRows.length && (
        <div className="text-[11px] text-[#737887] text-center pt-2">
          {t('preview.previewing', { preview: previewRows.length, total: totalRows, matched: matchedCount })}
        </div>
      )}
    </div>
  );
};
