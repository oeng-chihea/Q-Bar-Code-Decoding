import { useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  Search,
  X,
  ScanLine,
  Copy,
  Download,
  Loader2,
} from 'lucide-react';
import type { BarcodeResult, ExcelRowPreview, UnmatchedWaybill } from '@/features/reconciliation/model/types';
import { WaybillStatusBadge } from '@/features/reconciliation/components/WaybillStatusBadge';
import { ReceiptPreviewModal } from '@/features/reconciliation/components/ReceiptPreviewModal';
import { useReceiptPreview } from '@/features/reconciliation/hooks/useReceiptPreview';
import { findWaybill } from '@/features/reconciliation/utils/waybillTracking';
import { groupUnmatchedByBarcode } from '@/features/reconciliation/utils/unmatchedGrouping';
import { findReceiptFile } from '@/features/reconciliation/utils/receiptFiles';
import { newestReceiptFirst, rankedReceiptFilename } from '@/features/reconciliation/utils/receiptOrder';
import { findDuplicateSource } from '@/features/reconciliation/utils/previewTableRows';
import {
  downloadUnmatchedImage,
  downloadUnmatchedImages,
} from '@/features/reconciliation/api/reconciliationApi';
import { useTranslation } from '@/shared/i18n/i18n';

interface ImageScanGridProps {
  scanResults: BarcodeResult[];
  matchedCodes: string[];
  imageFiles?: File[];
  previewRows?: ExcelRowPreview[];
  reconciliationId?: string;
  unmatchedWaybills?: UnmatchedWaybill[];
  overdueAfterDays?: number;
}

export const ImageScanGrid = ({
  scanResults,
  matchedCodes,
  imageFiles,
  previewRows = [],
  reconciliationId,
  unmatchedWaybills = [],
  overdueAfterDays = 0,
}: ImageScanGridProps) => {
  const { t } = useTranslation();
  const [searchQuery, setSearchQuery] = useState('');
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadingImageIndex, setDownloadingImageIndex] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const { preview, setPreview, openPreview } = useReceiptPreview();

  const getFileForItem = (item: BarcodeResult, index: number): File | undefined =>
    findReceiptFile(item, index, scanResults, imageFiles);

  const normalize = (str: string) =>
    (str || '').trim().replace(/[\s_\-/:()!']+/g, '').toLowerCase();

  const matchedSet = new Set((matchedCodes || []).map(normalize));

  // Determine if a scan result matched an Excel row
  const isItemMatched = (item: BarcodeResult): boolean => {
    if (item.matched !== undefined) {
      return item.matched;
    }
    if (!item.success) {
      return false;
    }
    if (item.decodedValue && matchedSet.has(normalize(item.decodedValue))) {
      return true;
    }
    if (item.allExtractedValues) {
      for (const val of item.allExtractedValues) {
        if (val && matchedSet.has(normalize(val))) {
          return true;
        }
      }
    }
    return false;
  };

  const isUnmatched = (result: BarcodeResult): boolean => {
    return !isItemMatched(result);
  };

  const getDisplayBarcode = (item: BarcodeResult): string | undefined => {
    if (item.decodedValue && item.decodedValue.trim() !== '') {
      return item.decodedValue.trim();
    }
    if (item.allExtractedValues && item.allExtractedValues.length > 0) {
      const firstValid = item.allExtractedValues.find(
        (v) => v && v.trim() !== ''
      );
      if (firstValid) return firstValid.trim();
    }
    return undefined;
  };

  // ONLY keep truly unmatched items (did not match any row in Excel or failed decodes).
  // "Download all" still covers every photo; the cards below show each waybill once.
  const unmatchedResults = scanResults.filter(isUnmatched);
  const unmatchedGroups = groupUnmatchedByBarcode(scanResults, isUnmatched, getDisplayBarcode);
  const uniqueWaybillCount = unmatchedGroups.filter(
    (group) => group.barcode && !group.photos.every(({ item }) => item.duplicate),
  ).length;

  const filteredGroups = unmatchedGroups.filter(({ photos }) => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return true;
    return photos.some(({ item }) =>
      item.decodedValue?.toLowerCase().includes(q) ||
      item.filename?.toLowerCase().includes(q) ||
      item.allExtractedValues?.some((v) => v.toLowerCase().includes(q))
    );
  });

  const handleDownloadUnmatched = async () => {
    if (!reconciliationId || isDownloading || unmatchedResults.length === 0) return;
    setIsDownloading(true);
    setDownloadError(null);
    try {
      // Same newest-receipt-first order and ranked names as the backend ZIP
      const localUnmatchedFiles: File[] = [];
      if (imageFiles && imageFiles.length > 0) {
        const ordered = newestReceiptFirst(unmatchedResults, (item) => item.createDate);
        ordered.forEach((item, i) => {
          const file = getFileForItem(item, i);
          if (!file) return;
          const name = rankedReceiptFilename(i + 1, ordered.length, item.createDate, file.name);
          localUnmatchedFiles.push(new File([file], name, { type: file.type, lastModified: file.lastModified }));
        });
      }

      const hasAllLocalFiles = localUnmatchedFiles.length === unmatchedResults.length;
      await downloadUnmatchedImages(reconciliationId, {
        localFiles: hasAllLocalFiles ? localUnmatchedFiles : undefined,
        preferZip: true,
      });
    } catch (error: unknown) {
      setDownloadError(error instanceof Error ? error.message : t('unmatched.downloadError'));
    } finally {
      setIsDownloading(false);
    }
  };

  const handleDownloadImage = async (
    item: BarcodeResult,
    imageIndex: number,
    localFile?: File,
  ) => {
    if (
      !reconciliationId ||
      isDownloading ||
      downloadingImageIndex !== null
    ) return;

    setDownloadingImageIndex(imageIndex);
    setDownloadError(null);
    try {
      const resolvedFile = localFile || getFileForItem(item, imageIndex);
      await downloadUnmatchedImage(reconciliationId, imageIndex, item.filename, resolvedFile);
    } catch (error: unknown) {
      setDownloadError(error instanceof Error ? error.message : t('unmatched.downloadError'));
    } finally {
      setDownloadingImageIndex(null);
    }
  };

  return (
    <div className="min-w-0 bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-4 sm:p-5 text-left space-y-4">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#26272E] pb-4">
        <div className="flex min-w-0 items-start gap-2">
          <ScanLine className="w-5 h-5 shrink-0 text-[#FB7185]" />
          <div className="min-w-0">
            <h3 className="break-words text-sm font-bold text-white uppercase tracking-wider m-0">
              {t('unmatched.title')}
            </h3>
            <p className="break-words text-xs text-[#8E929E] m-0 mt-0.5">
              {t('unmatched.subtitle')}
              {uniqueWaybillCount > 0 && ` · ${t('unmatched.uniqueCount', { count: uniqueWaybillCount })}`}
            </p>
          </div>
        </div>

        {/* Right Action Controls */}
        <div className="flex w-full flex-col gap-2 sm:flex-row sm:w-auto sm:items-center">
          {/* Download Unmatched Images */}
          {unmatchedResults.length > 0 && (
            <button
              type="button"
              onClick={() => void handleDownloadUnmatched()}
              disabled={isDownloading || !reconciliationId}
              className="min-h-11 w-full px-3.5 py-1.5 rounded-md border border-[#FB7185]/40 text-xs font-semibold text-white bg-[#461B21] hover:bg-[#5C2028] transition shadow-sm flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap sm:w-auto"
            >
              {isDownloading ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin text-[#FB7185]" />
              ) : (
                <Download className="w-3.5 h-3.5 text-[#FB7185]" />
              )}
              <span>
                {isDownloading ? t('unmatched.downloadingAll') : t('unmatched.downloadAllRaw')}
              </span>
              <span className="px-1.5 py-0.5 rounded-full bg-[#FB7185]/20 text-[#FB7185] text-[10px] font-bold">
                {unmatchedResults.length}
              </span>
            </button>
          )}

          {/* Search */}
          {unmatchedResults.length > 0 && (
            <div className="relative w-full sm:w-56">
              <Search className="w-3.5 h-3.5 text-[#737887] absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={t('unmatched.search')}
                className="min-h-11 w-full pl-8 pr-3 py-1.5 rounded-md bg-[#16171B] border border-[#2B2D35] text-xs text-[#F3F4F6] placeholder-[#737887] focus:outline-none focus:border-[#FB7185]"
              />
            </div>
          )}
        </div>
      </div>

      {downloadError && (
        <div className="bg-[#461B21]/60 border border-[#FB7185]/40 rounded-md p-2.5 flex items-center justify-between gap-2 text-[#FCA5A5] text-xs animate-in fade-in">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-[#FB7185] shrink-0" />
            <span>{downloadError}</span>
          </div>
          <button
            onClick={() => setDownloadError(null)}
            type="button"
            className="min-h-11 min-w-11 flex items-center justify-center text-[#8E929E] hover:text-white cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Grid Cards */}
      <div className="min-w-0 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-3.5 max-h-[30rem] overflow-y-auto pr-1">
        {filteredGroups.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-[#737887] bg-[#16171B] rounded-md border border-[#26272E]">
            {unmatchedResults.length === 0
              ? t('unmatched.none')
              : t('unmatched.noSearch')}
          </div>
        ) : (
          filteredGroups.map((group, index) => {
            // The first photo of a waybill is the card's main photo; the rest are listed in the footer
            const { item, sourceIndex: imageIndex } = group.photos[0];
            const extraPhotos = group.photos.slice(1);
            const barcodeDisplay = group.barcode;
            const isFailed = !item.success || !barcodeDisplay;
            const trackedWaybill = findWaybill(unmatchedWaybills, barcodeDisplay);
            const duplicateSource = item.duplicate
              ? findDuplicateSource(item, scanResults, previewRows)
              : undefined;
            const isImageDownloading = downloadingImageIndex === imageIndex;

            const onCardClick = () => {
              openPreview({
                url: item.previewUrl,
                filename: item.filename,
                barcode: barcodeDisplay,
                isFailed,
                item,
                imageIndex,
                file: getFileForItem(item, index),
              });
            };

            return (
              <div
                key={group.key}
                onClick={onCardClick}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onCardClick();
                  }
                }}
                role="button"
                tabIndex={0}
                className={`min-w-0 min-h-11 p-4 rounded-md border shadow-sm flex flex-col justify-between transition cursor-pointer group focus-visible:outline-2 focus-visible:outline-[#A0E3E2] ${
                  isFailed
                    ? 'bg-[#291B17]/40 border-[#5C2B1D] hover:border-[#F59E0B]/80 hover:bg-[#331F19]/50'
                    : 'bg-[#23171A]/40 border-[#5C1D24] hover:border-[#FB7185]/80 hover:bg-[#2F191E]/50'
                }`}
                title={t('unmatched.preview')}
              >
                <div>
                  {/* Top Bar with Tag */}
                  <div className="flex items-center justify-between gap-1 mb-2.5">
                    <span className="text-[11px] font-medium text-[#8E929E] truncate">
                      {t('unmatched.image', { number: index + 1 })}
                      {extraPhotos.length > 0 &&
                        ` · ${t('unmatched.photoCount', { count: group.photos.length })}`}
                    </span>

                    {isFailed ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-[#3D2115] text-[#F59E0B] border border-[#5C2B1D]">
                        <AlertTriangle className="w-2.5 h-2.5" />
                        {t('unmatched.noBarcodeTag')}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-[#461B21] text-[#FB7185] border border-[#5C1D24]">
                        <AlertCircle className="w-2.5 h-2.5" />
                        {item.duplicate ? t('unmatched.duplicateTag') : t('unmatched.unmatchedTag')}
                      </span>
                    )}
                  </div>

                  {/* Barcode Number Display */}
                  <div className="min-w-0 space-y-1.5 mb-1">
                    {barcodeDisplay ? (
                      <div
                        className="font-mono text-base font-bold text-white tracking-wide truncate"
                        title={barcodeDisplay}
                      >
                        {barcodeDisplay}
                      </div>
                    ) : (
                      <div className="text-xs text-[#F59E0B]/90 italic truncate">
                        {item.errorMessage || t('unmatched.noBarcodeTag')}
                      </div>
                    )}
                  </div>
                  {duplicateSource && (
                    <div className="mt-2 flex min-w-0 items-start gap-1.5 rounded border border-[#5C1D24] bg-[#461B21]/50 px-2 py-1.5 text-[11px] text-[#FCA5A5]">
                      <Copy className="w-3 h-3 shrink-0 mt-0.5" />
                      <span className="min-w-0 break-words">
                        {duplicateSource.original
                          ? t('unmatched.duplicateOf', { filename: duplicateSource.original.filename })
                          : t('unmatched.duplicateOfUnknown')}
                        {duplicateSource.rowIndex !== undefined &&
                          ` · ${t('unmatched.duplicateRow', { row: duplicateSource.rowIndex })}`}
                      </span>
                    </div>
                  )}
                  {trackedWaybill && (
                    <div className="mt-2">
                      <WaybillStatusBadge waybill={trackedWaybill} overdueAfterDays={overdueAfterDays} />
                    </div>
                  )}
                </div>

                <div className="mt-3 border-t border-[#26272E] pt-2.5">
                <div className="flex min-w-0 items-center justify-between gap-2">
                  <span className="min-w-0 flex-1 truncate text-[11px] text-[#737887]" title={item.filename}>
                    {item.filename}
                  </span>
                  <div className="flex shrink-0 items-center gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        const localFile = getFileForItem(item, index);
                        void handleDownloadImage(item, imageIndex, localFile);
                      }}
                      disabled={!reconciliationId || isDownloading || downloadingImageIndex !== null}
                      className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-md text-[#A0A5B5] hover:bg-[#2B2D35] hover:text-[#A0E3E2] transition cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
                      title={t('unmatched.downloadRaw')}
                      aria-label={t('unmatched.downloadRaw')}
                    >
                      {isImageDownloading ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Download className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                </div>

                {/* Other photos of the same waybill */}
                {extraPhotos.map(({ item: extra, sourceIndex: extraIndex }) => (
                  <div
                    key={extraIndex}
                    className="flex min-w-0 items-center justify-between gap-2 border-t border-[#26272E]/60"
                  >
                    <span className="min-w-0 flex-1 truncate text-[11px] text-[#737887]" title={extra.filename}>
                      {extra.filename}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        void handleDownloadImage(extra, extraIndex, getFileForItem(extra, extraIndex));
                      }}
                      disabled={!reconciliationId || isDownloading || downloadingImageIndex !== null}
                      className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-md text-[#A0A5B5] hover:bg-[#2B2D35] hover:text-[#A0E3E2] transition cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
                      title={t('unmatched.downloadRaw')}
                      aria-label={t('unmatched.downloadRaw')}
                    >
                      {downloadingImageIndex === extraIndex ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <Download className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                ))}
                </div>
              </div>
            );
          })
        )}
      </div>

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
