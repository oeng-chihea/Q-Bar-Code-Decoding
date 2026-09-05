import { useState } from 'react';
import {
  CheckCircle2,
  CheckCheck,
  Search,
} from 'lucide-react';
import type { BarcodeResult } from '@/features/reconciliation/model/types';
import { useTranslation } from '@/shared/i18n/i18n';

interface ImageScanGridProps {
  scanResults: BarcodeResult[];
  matchedCodes: string[];
}

export const ImageScanGrid = ({
  scanResults,
  matchedCodes,
}: ImageScanGridProps) => {
  const { t } = useTranslation();
  const [searchQuery, setSearchQuery] = useState('');

  const normalize = (str: string) =>
    (str || '').trim().replace(/[\s_\-/:()!']+/g, '').toLowerCase();

  const matchedSet = new Set((matchedCodes || []).map(normalize));

  // Extract all matched barcodes for this item
  const getMatchedBarcodesForItem = (item: BarcodeResult): string[] => {
    if (!item.success) return [];
    const candidates = new Set<string>();
    if (item.decodedValue) candidates.add(item.decodedValue.trim());
    if (item.allExtractedValues) {
      for (const val of item.allExtractedValues) {
        if (val && val.trim() !== '') {
          candidates.add(val.trim());
        }
      }
    }
    const matchedList: string[] = [];
    for (const c of candidates) {
      if (matchedSet.has(normalize(c))) {
        matchedList.push(c);
      }
    }
    return matchedList;
  };

  const isMatched = (result: BarcodeResult) => {
    return getMatchedBarcodesForItem(result).length > 0;
  };

  // ONLY keep items with matches
  const matchedResults = scanResults.filter(isMatched);

  const filteredResults = matchedResults.filter((item) => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const codeMatch = item.decodedValue?.toLowerCase().includes(q);
      const fileMatch = item.filename?.toLowerCase().includes(q);
      const multiMatch = item.allExtractedValues?.some((v) => v.toLowerCase().includes(q));
      return codeMatch || fileMatch || multiMatch;
    }
    return true;
  });

  return (
    <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-5 text-left space-y-4">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[#26272E] pb-4">
        <div className="flex items-center gap-2">
          <CheckCheck className="w-5 h-5 text-[#34D399]" />
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider m-0">
              {t('matches.title')}
            </h3>
            <p className="text-xs text-[#8E929E] m-0 mt-0.5">
              {t('matches.subtitle')}
            </p>
          </div>
        </div>

        {/* Search */}
        {matchedResults.length > 0 && (
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-[#737887] absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t('matches.search')}
              className="pl-8 pr-3 py-1.5 rounded-md bg-[#16171B] border border-[#2B2D35] text-xs text-[#F3F4F6] placeholder-[#737887] focus:outline-none focus:border-[#A0E3E2] w-full sm:w-56"
            />
          </div>
        )}
      </div>

      {/* Grid Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-3.5 max-h-[30rem] overflow-y-auto pr-1">
        {filteredResults.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-[#737887] bg-[#16171B] rounded-md border border-[#26272E]">
            {matchedResults.length === 0
              ? t('matches.none')
              : t('matches.noSearch')}
          </div>
        ) : (
          filteredResults.map((item, index) => {
            const itemMatchedCodes = getMatchedBarcodesForItem(item);

            return (
              <div
                key={index}
                className="p-4 rounded-md border bg-[#23171A]/40 border-[#5C1D24] shadow-sm flex flex-col justify-between"
              >
                <div>
                  {/* Top Bar with Match Count Tag */}
                  <div className="flex items-center justify-between gap-1 mb-2.5">
                    <span className="text-[11px] font-medium text-[#8E929E] truncate">
                      {t('matches.image', { number: index + 1 })}
                    </span>

                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[10px] font-semibold bg-[#143827] text-[#34D399] border border-[#1E4D36]">
                      <CheckCircle2 className="w-2.5 h-2.5" />
                      {itemMatchedCodes.length > 1 ? t('matches.multiMatched', { count: itemMatchedCodes.length }) : t('matches.matchedInRed')}
                    </span>
                  </div>

                  {/* Pure Barcode Number(s) Display */}
                  <div className="space-y-1.5 mb-1">
                    {itemMatchedCodes.map((code, cIdx) => (
                      <div
                        key={cIdx}
                        className="font-mono text-base font-bold text-white tracking-wide truncate"
                        title={code}
                      >
                        {code}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Filename Footer */}
                <div
                  className="text-[11px] text-[#737887] truncate pt-2.5 border-t border-[#26272E] mt-3"
                  title={item.filename}
                >
                  {item.filename}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
