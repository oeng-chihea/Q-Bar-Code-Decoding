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

  // Helper to pick barcode number (numeric) as default primary display
  const getPrimaryBarcodeNumber = (item: BarcodeResult) => {
    if (!item.success) return null;
    const values = item.allExtractedValues && item.allExtractedValues.length > 0
      ? item.allExtractedValues
      : item.decodedValue ? [item.decodedValue] : [];

    // Prioritize pure digits (standard barcode)
    const numericCode = values.find((v) => /^\d{6,18}$/.test(v.trim()));
    if (numericCode) return numericCode.trim();

    // Secondary priority: contains at least 6 digits
    const mostlyNumeric = values.find((v) => /\d{6,}/.test(v.trim()));
    if (mostlyNumeric) return mostlyNumeric.trim();

    return item.decodedValue || values[0] || null;
  };

  const getSecondaryModelName = (item: BarcodeResult, primaryCode: string | null) => {
    if (!item.allExtractedValues) return null;
    const others = item.allExtractedValues.filter(
      (v) => normalize(v) !== normalize(primaryCode || '')
    );
    return others.length > 0 ? others.join(', ') : null;
  };

  const isMatched = (result: BarcodeResult) => {
    if (!result.success) return false;
    if (result.decodedValue && matchedSet.has(normalize(result.decodedValue))) {
      return true;
    }
    if (result.allExtractedValues) {
      for (const val of result.allExtractedValues) {
        if (matchedSet.has(normalize(val))) {
          return true;
        }
      }
    }
    return false;
  };

  // ONLY keep matched items
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
              Matched Barcode Items ({matchedResults.length})
            </h3>
            <p className="text-xs text-slate-400 m-0 mt-0.5">
              Showing only the barcode images that matched entries in your spreadsheet
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

      {/* Grid Cards - Showing ONLY Matched Items */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 max-h-96 overflow-y-auto pr-1">
        {filteredResults.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-slate-500 bg-slate-950/40 rounded-xl border border-slate-800/40">
            {matchedResults.length === 0
              ? 'No matching barcode items were found in the uploaded spreadsheet.'
              : 'No items match your search query.'}
          </div>
        ) : (
          filteredResults.map((item, index) => {
            const primaryBarcode = getPrimaryBarcodeNumber(item);
            const secondaryModel = getSecondaryModelName(item, primaryBarcode);

            return (
              <div
                key={index}
                className="p-4 rounded-xl border bg-rose-950/20 border-rose-500/50 shadow-md shadow-rose-950/30 flex flex-col justify-between"
              >
                <div>
                  {/* Top Bar with Match Tag */}
                  <div className="flex items-center justify-between gap-1 mb-2.5">
                    <span className="text-[11px] font-medium text-slate-400 truncate">
                      Matched Item #{index + 1}
                    </span>

                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[10px] font-bold bg-rose-500 text-white shadow-sm shadow-rose-500/40">
                      <CheckCircle2 className="w-2.5 h-2.5" />
                      Matched in Red
                    </span>
                  </div>

                  {/* Primary Barcode Number Display */}
                  <div className="mb-1">
                    <div
                      className="font-mono text-base font-bold text-white tracking-wide truncate"
                      title={primaryBarcode || ''}
                    >
                      {primaryBarcode}
                    </div>
                    {secondaryModel && (
                      <div className="text-[11px] text-slate-400 truncate mt-0.5">
                        SKU / Model: <span className="text-slate-300 font-medium">{secondaryModel}</span>
                      </div>
                    )}
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
