import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  AlertCircle,
  AlertTriangle,
  Eye,
  Search,
  X,
  ScanLine,
  ImageOff,
  Download,
  Loader2,
} from 'lucide-react';
import type { BarcodeResult } from '@/features/reconciliation/model/types';
import { downloadUnmatchedImages } from '@/features/reconciliation/api/reconciliationApi';
import { useTranslation } from '@/shared/i18n/i18n';

interface ImageScanGridProps {
  scanResults: BarcodeResult[];
  matchedCodes: string[];
  imageFiles?: File[];
  reconciliationId?: string;
  downloadFileName?: string;
}

export const ImageScanGrid = ({
  scanResults,
  matchedCodes,
  imageFiles,
  reconciliationId,
  downloadFileName,
}: ImageScanGridProps) => {
  const { t } = useTranslation();
  const [searchQuery, setSearchQuery] = useState('');
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [selectedPreviewImage, setSelectedPreviewImage] = useState<{
    url?: string;
    filename: string;
    barcode?: string;
    isFailed: boolean;
  } | null>(null);

  // Close preview modal on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setSelectedPreviewImage(null);
      }
    };
    if (selectedPreviewImage) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [selectedPreviewImage]);

  const getFileForItem = (item: BarcodeResult, index: number): File | undefined => {
    if (!imageFiles || imageFiles.length === 0) return undefined;

    if (item.filename) {
      const match = imageFiles.find((f) => {
        if (f.name === item.filename) return true;
        if (f.name.toLowerCase().trim() === item.filename.toLowerCase().trim()) return true;
        const baseName = item.filename.split(/[/\\]/).pop();
        if (baseName && (f.name === baseName || f.name.toLowerCase().trim() === baseName.toLowerCase().trim())) {
          return true;
        }
        try {
          const decoded = decodeURIComponent(item.filename);
          if (f.name === decoded || f.name.toLowerCase().trim() === decoded.toLowerCase().trim()) {
            return true;
          }
        } catch {
          // ignore malformed URI components
        }
        return false;
      });
      if (match) return match;
    }

    // Fallback by 1:1 original position in scanResults
    const originalIndex = scanResults.indexOf(item);
    const targetIdx = originalIndex >= 0 ? originalIndex : index;
    if (targetIdx >= 0 && targetIdx < imageFiles.length) {
      return imageFiles[targetIdx];
    }

    return undefined;
  };

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

  // ONLY keep truly unmatched items (did not match any row in Excel or failed decodes)
  const unmatchedResults = scanResults.filter(isUnmatched);

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

  const filteredResults = unmatchedResults.filter((item) => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const codeMatch = item.decodedValue?.toLowerCase().includes(q);
      const fileMatch = item.filename?.toLowerCase().includes(q);
      const multiMatch = item.allExtractedValues?.some((v) =>
        v.toLowerCase().includes(q)
      );
      return codeMatch || fileMatch || multiMatch;
    }
    return true;
  });

  const handleDownloadUnmatched = async () => {
    if (!reconciliationId || isDownloading || unmatchedResults.length === 0) return;
    setIsDownloading(true);
    setDownloadError(null);
    try {
      const baseName = downloadFileName
        ? downloadFileName.replace(/(?:_highlighted)?\.(?:xlsx|xls|csv|png|jpg|jpeg|webp)$/i, '')
        : 'reconciliation';
      const zipFileName = `${baseName}_unmatched_images.zip`;
      await downloadUnmatchedImages(reconciliationId, zipFileName);
    } catch (error: unknown) {
      setDownloadError(error instanceof Error ? error.message : t('unmatched.downloadError'));
    } finally {
      setIsDownloading(false);
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
                {isDownloading ? t('unmatched.downloading') : t('unmatched.download')}
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
        {filteredResults.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-[#737887] bg-[#16171B] rounded-md border border-[#26272E]">
            {unmatchedResults.length === 0
              ? t('unmatched.none')
              : t('unmatched.noSearch')}
          </div>
        ) : (
          filteredResults.map((item, index) => {
            const barcodeDisplay = getDisplayBarcode(item);
            const isFailed = !item.success || !barcodeDisplay;

            const onCardClick = () => {
              const file = getFileForItem(item, index);

              if (file) {
                // 1. Read as permanent Data URL (completely immune to blob revocation / StrictMode)
                const reader = new FileReader();
                reader.onload = () => {
                  if (typeof reader.result === 'string') {
                    setSelectedPreviewImage({
                      url: reader.result,
                      filename: item.filename,
                      barcode: barcodeDisplay,
                      isFailed,
                    });
                  }
                };
                reader.onerror = () => {
                  const freshUrl = URL.createObjectURL(file);
                  setSelectedPreviewImage({
                    url: freshUrl,
                    filename: item.filename,
                    barcode: barcodeDisplay,
                    isFailed,
                  });
                };
                reader.readAsDataURL(file);

                // Immediate preview with a fresh Object URL
                const instantUrl = URL.createObjectURL(file);
                setSelectedPreviewImage({
                  url: instantUrl,
                  filename: item.filename,
                  barcode: barcodeDisplay,
                  isFailed,
                });
              } else {
                setSelectedPreviewImage({
                  url: item.previewUrl,
                  filename: item.filename,
                  barcode: barcodeDisplay,
                  isFailed,
                });
              }
            };

            return (
              <div
                key={index}
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
                    </span>

                    {isFailed ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-[#3D2115] text-[#F59E0B] border border-[#5C2B1D]">
                        <AlertTriangle className="w-2.5 h-2.5" />
                        {t('unmatched.noBarcodeTag')}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-[#461B21] text-[#FB7185] border border-[#5C1D24]">
                        <AlertCircle className="w-2.5 h-2.5" />
                        {t('unmatched.unmatchedTag')}
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
                </div>

                {/* Footer with Filename and Click-to-preview button */}
                <div className="text-[11px] text-[#737887] flex items-center justify-between pt-2.5 border-t border-[#26272E] mt-3">
                  <span className="truncate max-w-[110px]" title={item.filename}>
                    {item.filename}
                  </span>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onCardClick();
                    }}
                    className="inline-flex min-h-11 items-center gap-1 text-[10px] text-[#A0A5B5] group-hover:text-[#A0E3E2] hover:text-[#A0E3E2] transition cursor-pointer"
                    title={t('unmatched.preview')}
                  >
                    <Eye className="w-3 h-3" />
                    {t('unmatched.preview')}
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Click-to-Preview Modal Overlay rendered at document.body */}
      {typeof document !== 'undefined' &&
        selectedPreviewImage &&
        createPortal(
          <div
            className="fixed inset-0 z-[9999] overflow-y-auto bg-black/85 p-2 backdrop-blur-sm sm:flex sm:items-center sm:justify-center sm:p-4"
            onClick={() => setSelectedPreviewImage(null)}
          >
            <div
              className="mx-auto flex min-h-[calc(100dvh-1rem)] w-full max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-xl border border-[#3D404B] bg-[#1C1D22] shadow-2xl sm:min-h-0 sm:max-h-[90vh] sm:max-w-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Modal Header */}
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#26272E] bg-[#16171B] px-4 py-3.5 sm:px-5">
                <div className="flex min-w-0 items-center gap-2 overflow-hidden">
                  <Eye className="w-4 h-4 text-[#A0E3E2] shrink-0" />
                  <div className="truncate">
                    <h4 className="text-sm font-semibold text-white m-0">
                      {t('unmatched.previewTitle')}
                    </h4>
                    <p className="text-[11px] text-[#8E929E] m-0 truncate">
                      {selectedPreviewImage.filename}
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedPreviewImage(null)}
                  className="ml-3 flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-md text-[#8E929E] hover:bg-[#2B2D35] hover:text-white transition cursor-pointer"
                  title={t('unmatched.close')}
                  aria-label={t('unmatched.close')}
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Modal Image View */}
              <div className="min-h-0 max-h-[calc(100dvh-9rem)] flex-1 overflow-y-auto bg-[#121316] p-4 flex items-center justify-center sm:max-h-[60vh]">
                {selectedPreviewImage.url ? (
                  <img
                    src={selectedPreviewImage.url}
                    alt={selectedPreviewImage.filename}
                    className="max-h-[60vh] max-w-full object-contain rounded-lg border border-[#2B2D35] shadow-md"
                    onError={() => {
                      const currentFilename = selectedPreviewImage.filename;
                      const file = imageFiles?.find(
                        (f) =>
                          f.name === currentFilename ||
                          f.name.toLowerCase().trim() ===
                            currentFilename.toLowerCase().trim()
                      );
                      if (file) {
                        const reader = new FileReader();
                        reader.onload = () => {
                          if (typeof reader.result === 'string') {
                            setSelectedPreviewImage((prev) =>
                              prev
                                ? { ...prev, url: reader.result as string }
                                : null
                            );
                          }
                        };
                        reader.readAsDataURL(file);
                      }
                    }}
                  />
                ) : (
                  <div className="text-center py-10 px-4 space-y-2 text-[#8E929E]">
                    <ImageOff className="w-10 h-10 mx-auto opacity-50 text-[#8E929E]" />
                    <p className="text-xs m-0">
                      {selectedPreviewImage.filename}
                    </p>
                  </div>
                )}
              </div>

              {/* Modal Footer with Barcode Badge */}
              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#26272E] bg-[#16171B] px-4 py-3 sm:px-5">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <span className="text-xs text-[#8E929E]">បាកូដ:</span>
                  {selectedPreviewImage.barcode ? (
                    <span className="max-w-full break-all rounded border border-[#5C1D24] bg-[#461B21] px-2.5 py-0.5 font-mono text-sm font-bold text-[#FB7185]">
                      {selectedPreviewImage.barcode}
                    </span>
                  ) : (
                    <span className="text-xs text-[#F59E0B] bg-[#3D2115] px-2.5 py-0.5 rounded border border-[#5C2B1D]">
                      {t('unmatched.noBarcodeTag')}
                    </span>
                  )}
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedPreviewImage(null)}
                  className="min-h-11 shrink-0 rounded-md bg-[#24262E] px-3 py-1.5 text-xs font-medium text-[#D1D5DB] hover:bg-[#2D2F38] hover:text-white transition cursor-pointer"
                >
                  {t('unmatched.close')}
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </div>
  );
};
