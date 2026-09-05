import { useState, useMemo, useEffect } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  Eye,
  Search,
  X,
  ScanLine,
} from 'lucide-react';
import type { BarcodeResult } from '@/features/reconciliation/model/types';
import { useTranslation } from '@/shared/i18n/i18n';

interface ImageScanGridProps {
  scanResults: BarcodeResult[];
  matchedCodes: string[];
  imageFiles?: File[];
}

export const ImageScanGrid = ({
  scanResults,
  matchedCodes,
  imageFiles,
}: ImageScanGridProps) => {
  const { t } = useTranslation();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPreviewImage, setSelectedPreviewImage] = useState<{
    url: string;
    filename: string;
    barcode?: string;
    isFailed: boolean;
  } | null>(null);

  // Map each file.name to an object URL for instant previewing
  const fileUrlMap = useMemo(() => {
    const map = new Map<string, string>();
    if (imageFiles) {
      for (const file of imageFiles) {
        map.set(file.name, URL.createObjectURL(file));
      }
    }
    return map;
  }, [imageFiles]);

  useEffect(() => {
    return () => {
      fileUrlMap.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [fileUrlMap]);

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

  const normalize = (str: string) =>
    (str || '').trim().replace(/[\s_\-/:()!']+/g, '').toLowerCase();

  const matchedSet = new Set((matchedCodes || []).map(normalize));

  // Extract all unmatched barcodes for this item
  const getUnmatchedBarcodesForItem = (item: BarcodeResult): string[] => {
    if (!item.success) return [];
    const candidates = new Set<string>();
    if (item.decodedValue && item.decodedValue.trim() !== '') {
      candidates.add(item.decodedValue.trim());
    }
    if (item.allExtractedValues) {
      for (const val of item.allExtractedValues) {
        if (val && val.trim() !== '') {
          candidates.add(val.trim());
        }
      }
    }
    const unmatchedList: string[] = [];
    for (const c of candidates) {
      if (!matchedSet.has(normalize(c))) {
        unmatchedList.push(c);
      }
    }
    return unmatchedList;
  };

  const isUnmatched = (result: BarcodeResult): boolean => {
    if (!result.success) return true;
    const unmatchedCodes = getUnmatchedBarcodesForItem(result);
    if (unmatchedCodes.length > 0) return true;
    const hasAnyCode =
      (result.decodedValue != null && result.decodedValue.trim() !== '') ||
      (result.allExtractedValues != null &&
        result.allExtractedValues.some((v) => v != null && v.trim() !== ''));
    return !hasAnyCode;
  };

  // ONLY keep unmatched items (unmatched barcodes or failed decodes)
  const unmatchedResults = scanResults.filter(isUnmatched);

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

  return (
    <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-5 text-left space-y-4">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#26272E] pb-4">
        <div className="flex items-center gap-2">
          <ScanLine className="w-5 h-5 text-[#FB7185]" />
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider m-0">
              {t('unmatched.title')}
            </h3>
            <p className="text-xs text-[#8E929E] m-0 mt-0.5">
              {t('unmatched.subtitle')}
            </p>
          </div>
        </div>

        {/* Search */}
        {unmatchedResults.length > 0 && (
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-[#737887] absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t('unmatched.search')}
              className="pl-8 pr-3 py-1.5 rounded-md bg-[#16171B] border border-[#2B2D35] text-xs text-[#F3F4F6] placeholder-[#737887] focus:outline-none focus:border-[#FB7185] w-full sm:w-56"
            />
          </div>
        )}
      </div>

      {/* Grid Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-3.5 max-h-[30rem] overflow-y-auto pr-1">
        {filteredResults.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-[#737887] bg-[#16171B] rounded-md border border-[#26272E]">
            {unmatchedResults.length === 0
              ? t('unmatched.none')
              : t('unmatched.noSearch')}
          </div>
        ) : (
          filteredResults.map((item, index) => {
            const itemUnmatchedCodes = getUnmatchedBarcodesForItem(item);
            const isFailed =
              !item.success ||
              (itemUnmatchedCodes.length === 0 && !item.decodedValue);
            const barcodeDisplay =
              itemUnmatchedCodes.length > 0
                ? itemUnmatchedCodes.join(', ')
                : item.decodedValue || undefined;
            const previewUrl = fileUrlMap.get(item.filename);

            const handleCardClick = () => {
              if (previewUrl) {
                setSelectedPreviewImage({
                  url: previewUrl,
                  filename: item.filename,
                  barcode: barcodeDisplay,
                  isFailed,
                });
              }
            };

            return (
              <div
                key={index}
                onClick={handleCardClick}
                className={`p-4 rounded-md border shadow-sm flex flex-col justify-between transition cursor-pointer group ${
                  isFailed
                    ? 'bg-[#291B17]/40 border-[#5C2B1D] hover:border-[#F59E0B]/80 hover:bg-[#331F19]/50'
                    : 'bg-[#23171A]/40 border-[#5C1D24] hover:border-[#FB7185]/80 hover:bg-[#2F191E]/50'
                }`}
                title={previewUrl ? t('unmatched.preview') : item.filename}
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

                  {/* Barcode Number(s) Display */}
                  <div className="space-y-1.5 mb-1">
                    {itemUnmatchedCodes.length > 0 ? (
                      itemUnmatchedCodes.map((code, cIdx) => (
                        <div
                          key={cIdx}
                          className="font-mono text-base font-bold text-white tracking-wide truncate"
                          title={code}
                        >
                          {code}
                        </div>
                      ))
                    ) : (
                      <div className="text-xs text-[#F59E0B]/90 italic truncate">
                        {item.errorMessage || t('unmatched.noBarcodeTag')}
                      </div>
                    )}
                  </div>
                </div>

                {/* Footer with Filename and Click-to-preview icon */}
                <div className="text-[11px] text-[#737887] flex items-center justify-between pt-2.5 border-t border-[#26272E] mt-3">
                  <span className="truncate max-w-[120px]" title={item.filename}>
                    {item.filename}
                  </span>
                  {previewUrl && (
                    <span className="inline-flex items-center gap-1 text-[10px] text-[#A0A5B5] group-hover:text-[#A0E3E2] transition">
                      <Eye className="w-3 h-3" />
                      {t('unmatched.preview')}
                    </span>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Click-to-Preview Modal Overlay */}
      {selectedPreviewImage && (
        <div
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-150"
          onClick={() => setSelectedPreviewImage(null)}
        >
          <div
            className="bg-[#1C1D22] border border-[#3D404B] rounded-xl max-w-2xl w-full max-h-[90vh] overflow-hidden flex flex-col shadow-2xl animate-in zoom-in-95 duration-150"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="flex items-center justify-between px-5 py-3.5 border-b border-[#26272E] bg-[#16171B]">
              <div className="flex items-center gap-2 overflow-hidden">
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
                className="p-1.5 rounded-md text-[#8E929E] hover:text-white hover:bg-[#2B2D35] transition cursor-pointer ml-3 shrink-0"
                title={t('unmatched.close')}
                aria-label={t('unmatched.close')}
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Modal Image View */}
            <div className="p-4 overflow-y-auto flex-1 flex items-center justify-center bg-[#121316]">
              <img
                src={selectedPreviewImage.url}
                alt={selectedPreviewImage.filename}
                className="max-h-[60vh] max-w-full object-contain rounded-lg border border-[#2B2D35] shadow-md"
              />
            </div>

            {/* Modal Footer with Barcode Badge */}
            <div className="px-5 py-3 border-t border-[#26272E] bg-[#16171B] flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-xs text-[#8E929E]">បាកូដ:</span>
                {selectedPreviewImage.barcode ? (
                  <span className="font-mono text-sm font-bold text-[#FB7185] bg-[#461B21] px-2.5 py-0.5 rounded border border-[#5C1D24]">
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
                className="px-3 py-1.5 text-xs font-medium rounded-md bg-[#24262E] text-[#D1D5DB] hover:bg-[#2D2F38] hover:text-white transition cursor-pointer"
              >
                {t('unmatched.close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
