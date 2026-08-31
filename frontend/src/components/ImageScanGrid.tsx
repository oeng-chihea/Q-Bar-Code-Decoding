import { useState } from 'react';
import {
  CheckCircle2,
  CheckCheck,
  Search,
} from 'lucide-react';
import type { BarcodeResult } from '../types';

interface ImageScanGridProps {
  scanResults: BarcodeResult[];
  matchedCodes: string[];
}

export const ImageScanGrid = ({
  scanResults,
  matchedCodes,
}: ImageScanGridProps) => {
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
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 text-left space-y-4">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-2">
          <CheckCheck className="w-5 h-5 text-rose-400" />
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider m-0">
              Matched Barcodes
            </h3>
            <p className="text-xs text-slate-400 m-0 mt-0.5">
              Showing barcode numbers extracted from your images that matched entries in your spreadsheet
            </p>
          </div>
        </div>

        {/* Search */}
        {matchedResults.length > 0 && (
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search matched barcode..."
              className="pl-8 pr-3 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-rose-500 w-full sm:w-56"
            />
          </div>
        )}
      </div>

      {/* Grid Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 max-h-96 overflow-y-auto pr-1">
        {filteredResults.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-slate-500 bg-slate-950/40 rounded-xl border border-slate-800/40">
            {matchedResults.length === 0
              ? 'No matching barcode numbers were found in the uploaded spreadsheet.'
              : 'No items match your search query.'}
          </div>
        ) : (
          filteredResults.map((item, index) => {
            const itemMatchedCodes = getMatchedBarcodesForItem(item);

            return (
              <div
                key={index}
                className="p-4 rounded-xl border bg-rose-950/20 border-rose-500/50 shadow-md shadow-rose-950/30 flex flex-col justify-between"
              >
                <div>
                  {/* Top Bar with Match Count Tag */}
                  <div className="flex items-center justify-between gap-1 mb-2.5">
                    <span className="text-[11px] font-medium text-slate-400 truncate">
                      Image #{index + 1}
                    </span>

                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[10px] font-bold bg-rose-500 text-white shadow-sm shadow-rose-500/40">
                      <CheckCircle2 className="w-2.5 h-2.5" />
                      {itemMatchedCodes.length > 1 ? `${itemMatchedCodes.length} Barcodes Matched` : 'Matched in Red'}
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
                  className="text-[11px] text-slate-500 truncate pt-2.5 border-t border-slate-800/60 mt-3"
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
