import {
  CheckCircle2,
  FileSpreadsheet,
  Zap,
  Download,
  CheckCheck,
  ScanLine,
} from 'lucide-react';
import type { ReconciliationResponse } from '../types';
import { downloadBase64Excel } from '../services/api';

interface ReconciliationStatsProps {
  results: ReconciliationResponse;
}

export const ReconciliationStats = ({ results }: ReconciliationStatsProps) => {
  const matchRate =
    results.excelTotalRows > 0
      ? Math.round((results.matchedRowsCount / results.excelTotalRows) * 100)
      : 0;

  const handleDownload = () => {
    downloadBase64Excel(results.highlightedExcelBase64, results.downloadFileName);
  };

  return (
    <div className="space-y-4 text-left">
      {/* Top Banner with Download Action */}
      <div className="bg-gradient-to-r from-slate-900 via-rose-950/40 to-slate-900 border border-rose-500/30 rounded-2xl p-5 flex flex-col md:flex-row items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-3.5">
          <div className="p-3 rounded-xl bg-rose-500/20 text-rose-400 border border-rose-500/30">
            <CheckCheck className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white m-0">
              Reconciliation Completed
            </h2>
            <p className="text-xs text-slate-300 m-0 mt-0.5">
              Matched entries have been identified and highlighted in <span className="text-rose-400 font-semibold">RED</span>.
            </p>
          </div>
        </div>

        <button
          onClick={handleDownload}
          className="w-full md:w-auto px-6 py-3 rounded-xl font-semibold text-sm text-white bg-rose-600 hover:bg-rose-500 active:bg-rose-700 shadow-lg shadow-rose-600/30 transition-all flex items-center justify-center gap-2 cursor-pointer"
        >
          <Download className="w-4 h-4" />
          <span>Download Highlighted Excel ({results.downloadFileName})</span>
        </button>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
        {/* Card 1: Images Scanned */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
            <span>Images Uploaded</span>
            <ScanLine className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="text-2xl font-bold text-white">
            {results.decodedImagesCount}
            <span className="text-sm font-normal text-slate-400">/{results.totalImages}</span>
          </div>
          <div className="text-[11px] text-indigo-400 mt-1 font-medium">
            Scanned &amp; Processed
          </div>
        </div>

        {/* Card 2: Excel Rows */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
            <span>Catalog Rows</span>
            <FileSpreadsheet className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold text-white">{results.excelTotalRows}</div>
          <div className="text-[11px] text-slate-400 mt-1">
            {results.activeSheetName ? (
              <span>Sheet: <strong className="text-slate-200">{results.activeSheetName}</strong></span>
            ) : (
              <span>Target: <strong className="text-slate-200">{results.matchedColumnName}</strong></span>
            )}
          </div>
        </div>

        {/* Card 3: Matched & Highlighted in Red */}
        <div className="bg-slate-900/60 border border-rose-500/30 rounded-xl p-4 bg-rose-950/10">
          <div className="flex items-center justify-between text-rose-300 text-xs mb-2">
            <span>Highlighted in Red</span>
            <CheckCircle2 className="w-4 h-4 text-rose-400" />
          </div>
          <div className="text-2xl font-bold text-rose-400">{results.matchedRowsCount}</div>
          <div className="text-[11px] text-rose-300 mt-1 font-medium">
            {matchRate}% of catalog matched
          </div>
        </div>

        {/* Card 4: Duration */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4">
          <div className="flex items-center justify-between text-slate-400 text-xs mb-2">
            <span>Processing Time</span>
            <Zap className="w-4 h-4 text-cyan-400" />
          </div>
          <div className="text-2xl font-bold text-cyan-400">
            {results.executionTimeMs}
            <span className="text-sm font-normal text-slate-400"> ms</span>
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            Fast parallel execution
          </div>
        </div>
      </div>
    </div>
  );
};
