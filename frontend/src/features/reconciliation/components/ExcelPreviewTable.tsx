import { useState } from 'react';
import {
  FileSpreadsheet,
  Download,
  Eye,
  EyeOff,
} from 'lucide-react';
import type {
  BarcodeResult,
  ExcelRowPreview,
  UnmatchedWaybill,
} from '@/features/reconciliation/model/types';
import { downloadReconciliation } from '@/features/reconciliation/api/reconciliationApi';
import { WaybillStatusBadge } from '@/features/reconciliation/components/WaybillStatusBadge';
import { ReceiptPreviewModal } from '@/features/reconciliation/components/ReceiptPreviewModal';
import { useReceiptPreview } from '@/features/reconciliation/hooks/useReceiptPreview';
import { buildPreviewTableRows } from '@/features/reconciliation/utils/previewTableRows';
import type { ReceiptPhoto } from '@/features/reconciliation/utils/previewTableRows';
import { findReceiptFile } from '@/features/reconciliation/utils/receiptFiles';
import { WAYBILL_TABLE_ID } from '@/features/reconciliation/utils/waybillTracking';
import { useTranslation } from '@/shared/i18n/i18n';

interface ExcelPreviewTableProps {
  columns: string[];
  previewRows: ExcelRowPreview[];
  matchedColumnName: string;
  totalRows: number;
  matchedCount: number;
  reconciliationId: string;
  downloadFileName: string;
  scanResults: BarcodeResult[];
  imageFiles: File[];
  unmatchedWaybills: UnmatchedWaybill[];
  overdueAfterDays: number;
}

export const ExcelPreviewTable = ({
  columns,
  previewRows,
  matchedColumnName,
  totalRows,
  matchedCount,
  reconciliationId,
  downloadFileName,
  scanResults,
  imageFiles,
  unmatchedWaybills,
  overdueAfterDays,
}: ExcelPreviewTableProps) => {
  const { t } = useTranslation();
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const { preview, setPreview, openPreview } = useReceiptPreview();

  const showTracking = unmatchedWaybills.length > 0;
  const tableRows = buildPreviewTableRows(previewRows, unmatchedWaybills, scanResults);
  const fixedColumnCount = showTracking ? 4 : 2;

  const handleDownload = async () => {
    if (!reconciliationId || isDownloading) return;
    setIsDownloading(true);
    setDownloadError(null);
    try {
      await downloadReconciliation(reconciliationId, downloadFileName);
    } catch (error: unknown) {
      setDownloadError(error instanceof Error ? error.message : 'Download failed');
    } finally {
      setIsDownloading(false);
    }
  };

  const openPhoto = ({ item, imageIndex }: ReceiptPhoto, barcode: string) => {
    openPreview({
      url: item.previewUrl,
      filename: item.filename,
      barcode,
      isFailed: false,
      item,
      imageIndex,
      file: findReceiptFile(item, imageIndex, scanResults, imageFiles),
    });
  };

  const renderViewButton = (photos: ReceiptPhoto[], barcode: string) => {
    const [mainPhoto, ...repeatPhotos] = photos;
    if (!mainPhoto) {
      return (
        <span
          className="inline-flex min-h-11 min-w-11 items-center justify-center text-[#4A4D57]"
          title={t('preview.noReceipt')}
          aria-label={t('preview.noReceipt')}
        >
          <EyeOff className="w-3.5 h-3.5" />
        </span>
      );
    }

    return (
      <div className="inline-flex items-center justify-center">
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            openPhoto(mainPhoto, barcode);
          }}
          className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-md text-[#A0A5B5] hover:bg-[#2B2D35] hover:text-[#A0E3E2] transition cursor-pointer"
          title={t('preview.viewReceipt')}
          aria-label={t('preview.viewReceipt')}
        >
          <Eye className="w-3.5 h-3.5" />
        </button>
        {repeatPhotos.map((photo, idx) => (
          <button
            key={photo.imageIndex}
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              openPhoto(photo, barcode);
            }}
            className="inline-flex min-h-11 items-center justify-center rounded-md px-1.5 text-[10px] font-semibold text-[#A0A5B5] hover:bg-[#2B2D35] hover:text-[#A0E3E2] transition cursor-pointer"
            title={t('preview.repeatPhoto', { filename: photo.item.filename })}
            aria-label={t('preview.repeatPhoto', { filename: photo.item.filename })}
          >
            +{idx + 1}
          </button>
        ))}
      </div>
    );
  };

  const emptyCell = <span className="text-[10px] text-[#737887] font-normal">&mdash;</span>;

  return (
    <div
      id={WAYBILL_TABLE_ID}
      className="min-w-0 bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-4 sm:p-5 text-left space-y-4"
    >
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#26272E] pb-4">
        <div className="flex min-w-0 items-start gap-2">
          <FileSpreadsheet className="w-5 h-5 text-[#34D399]" />
          <div className="min-w-0">
            <h3 className="break-words text-sm font-bold text-white uppercase tracking-wider m-0 flex items-center gap-2 flex-wrap">
              <span>{t('preview.title')}</span>
            </h3>
            <p className="break-words text-xs text-[#8E929E] m-0 mt-0.5">
              {t('preview.showing', { count: previewRows.length, matched: matchedCount })}
              {showTracking && ` · ${t('preview.unmatchedWaybills', { count: unmatchedWaybills.length })}`}
            </p>
          </div>
        </div>

        <div className="flex w-full items-stretch gap-2 sm:w-auto">
          {/* Download Action */}
          <button
            type="button"
            onClick={() => void handleDownload()}
            className="min-h-11 flex-1 px-3.5 py-1.5 rounded-md border border-[#A0E3E2]/40 text-xs font-semibold text-[#0E1726] bg-[#A0E3E2] hover:bg-[#8EE0DF] transition shadow-sm flex items-center justify-center gap-1.5 cursor-pointer sm:flex-none"
            disabled={isDownloading}
          >
            <Download className="w-3.5 h-3.5" />
            {t('preview.download')}
          </button>
        </div>
      </div>
      {downloadError && (
        <p className="text-xs text-[#FCA5A5] m-0">{downloadError}</p>
      )}

      {/* Spreadsheet Table */}
      <div className="min-w-0 overflow-x-auto rounded-md border border-[#2B2D35] bg-[#16171B]">
        <table className="min-w-[640px] w-full text-xs text-left border-collapse">
          <thead>
            <tr className="bg-[#1C1D22] border-b border-[#2B2D35] text-[#8E929E]">
              <th className="py-2.5 px-3.5 font-semibold w-12 text-[#737887] border-r border-[#2B2D35]">
                #
              </th>
              <th className="py-2.5 px-2 font-semibold w-16 text-center border-r border-[#2B2D35]">
                {t('preview.colView')}
              </th>
              {showTracking && (
                <>
                  <th className="py-2.5 px-3.5 font-semibold border-r border-[#2B2D35] whitespace-nowrap">
                    {t('preview.colOver', { days: overdueAfterDays })}
                  </th>
                  <th className="py-2.5 px-3.5 font-semibold border-r border-[#2B2D35] whitespace-nowrap">
                    {t('preview.colUnmatchedWaybill')}
                  </th>
                </>
              )}
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
            {tableRows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length + fixedColumnCount}
                  className="py-8 text-center text-[#737887] text-xs"
                >
                  {t('preview.noRows')}
                </td>
              </tr>
            ) : (
              tableRows.map((tableRow) => {
                if (tableRow.kind === 'waybill') {
                  const { waybill } = tableRow;
                  const isOverdue = waybill.status === 'OVERDUE';
                  return (
                    <tr
                      key={tableRow.key}
                      className={`transition text-[#D1D5DB] ${
                        isOverdue ? 'bg-[#2A1519] hover:bg-[#33191E]' : 'hover:bg-[#1C1D22]'
                      }`}
                    >
                      <td className="py-2 px-3.5 border-r border-[#24252B]" />
                      <td className="py-0.5 px-2 text-center border-r border-[#24252B]">
                        {renderViewButton(tableRow.photos, waybill.waybillNo)}
                      </td>
                      <td
                        className="py-2 px-3.5 border-r border-[#24252B]"
                        title={t('preview.waybillDates', { start: waybill.startDate, due: waybill.dueDate })}
                      >
                        <WaybillStatusBadge waybill={waybill} overdueAfterDays={overdueAfterDays} />
                      </td>
                      <td className="py-2 px-3.5 border-r border-[#24252B] whitespace-nowrap font-mono font-semibold">
                        <span className={isOverdue ? 'text-[#FB7185]' : 'text-white'}>{waybill.waybillNo}</span>
                        {!waybill.inThisUpload && (
                          <span className="block text-[10px] font-sans font-normal text-[#737887]">
                            {t('waybill.earlierUpload')}
                          </span>
                        )}
                      </td>
                      {columns.map((_, cIdx) => (
                        <td key={cIdx} className="py-2.5 px-4 border-r border-[#24252B]" />
                      ))}
                    </tr>
                  );
                }

                const { row } = tableRow;
                return (
                  <tr
                    key={tableRow.key}
                    className={`transition ${
                      row.matched
                        ? 'bg-[#331418]/60 text-[#FEE2E2] hover:bg-[#40181D] font-medium'
                        : 'hover:bg-[#1C1D22] text-[#D1D5DB]'
                    }`}
                  >
                    <td className="py-2 px-3.5 text-[#737887] font-mono border-r border-[#24252B]">
                      {row.rowIndex}
                    </td>
                    <td className="py-0.5 px-2 text-center border-r border-[#24252B]">
                      {renderViewButton(tableRow.photos, row.barcodeValue)}
                    </td>
                    {showTracking && (
                      <>
                        <td className="py-2 px-3.5 border-r border-[#24252B]">{emptyCell}</td>
                        <td className="py-2 px-3.5 border-r border-[#24252B]">{emptyCell}</td>
                      </>
                    )}
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

      <ReceiptPreviewModal
        preview={preview}
        setPreview={setPreview}
        imageFiles={imageFiles}
        reconciliationId={reconciliationId}
        onSaveError={setDownloadError}
      />
    </div>
  );
};
