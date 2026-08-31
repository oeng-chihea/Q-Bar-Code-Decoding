import { useState } from 'react';
import {
  FileSpreadsheet,
  Download,
  Filter,
  CheckCircle2,
  Layers,
} from 'lucide-react';
import type { ExcelRowPreview } from '../types';
import { downloadBase64Excel } from '../services/api';

interface ExcelPreviewTableProps {
  columns: string[];
  previewRows: ExcelRowPreview[];
  matchedColumnName: string;
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
  activeSheetName,
  totalRows,
  matchedCount,
  highlightedExcelBase64,
  downloadFileName,
}: ExcelPreviewTableProps) => {
  const [onlyMatched, setOnlyMatched] = useState(false);

  const displayRows = onlyMatched
    ? previewRows.filter((r) => r.matched)
    : previewRows;

  const handleDownload = () => {
    downloadBase64Excel(highlightedExcelBase64, downloadFileName);
  };

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 text-left space-y-4">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-2">
          <FileSpreadsheet className="w-5 h-5 text-emerald-400" />
          <div>
            <h3 className="text-sm font-bold text-white uppercase tracking-wider m-0 flex items-center gap-2 flex-wrap">
              <span>Spreadsheet Preview</span>
              {activeSheetName && (
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-medium flex items-center gap-1">
                  <Layers className="w-3 h-3" />
                  Sheet: {activeSheetName}
                </span>
              )}
              <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-normal">
                Target: {matchedColumnName}
              </span>
            </h3>
            <p className="text-xs text-slate-400 m-0 mt-0.5">
              Showing preview of first {previewRows.length} rows &bull; {matchedCount} matched rows highlighted in red
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Toggle Filter */}
          <button
            onClick={() => setOnlyMatched(!onlyMatched)}
            className={`px-3 py-1.5 rounded-lg border text-xs font-medium transition cursor-pointer flex items-center gap-1.5 ${
              onlyMatched
                ? 'bg-rose-950/60 text-rose-300 border-rose-500'
                : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-slate-200'
            }`}
          >
            <Filter className="w-3 h-3" />
            {onlyMatched ? 'Showing Matched Only' : 'Show All Rows'}
          </button>

          {/* Download Action */}
          <button
            onClick={handleDownload}
            className="px-4 py-1.5 rounded-lg border border-rose-500/50 text-xs font-semibold text-white bg-rose-600 hover:bg-rose-500 transition shadow-md shadow-rose-600/20 flex items-center gap-1.5 cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            Download .xlsx
          </button>
        </div>
      </div>

      {/* Spreadsheet Table */}
      <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-950/80">
        <table className="w-full text-xs text-left border-collapse">
          <thead>
            <tr className="bg-slate-900/90 border-b border-slate-800 text-slate-300">
              <th className="py-3 px-3.5 font-semibold w-12 text-slate-500 border-r border-slate-800">
                #
              </th>
              <th className="py-3 px-3.5 font-semibold w-24 border-r border-slate-800">
                Status
              </th>
              {columns.map((col, idx) => {
                const isTarget = col.toLowerCase() === matchedColumnName.toLowerCase();
                return (
                  <th
                    key={idx}
                    className={`py-3 px-4 font-semibold border-r border-slate-800 whitespace-nowrap ${
                      isTarget ? 'text-rose-400 bg-rose-950/20' : ''
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>{col}</span>
                      {isTarget && (
                        <span className="text-[10px] px-1.5 py-0.2 rounded bg-rose-500/20 text-rose-300 border border-rose-500/40">
                          TARGET
                        </span>
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60">
            {displayRows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length + 2}
                  className="py-8 text-center text-slate-500 text-xs"
                >
                  No rows to display with current filter.
                </td>
              </tr>
            ) : (
              displayRows.map((row) => {
                return (
                  <tr
                    key={row.rowIndex}
                    className={`transition ${
                      row.matched
                        ? 'bg-rose-950/40 text-rose-100 hover:bg-rose-950/60 font-medium'
                        : 'hover:bg-slate-900/40 text-slate-300'
                    }`}
                  >
                    <td className="py-2.5 px-3.5 text-slate-500 font-mono border-r border-slate-800/80">
                      {row.rowIndex}
                    </td>
                    <td className="py-2.5 px-3.5 border-r border-slate-800/80">
                      {row.matched ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-500 text-white shadow-sm shadow-rose-500/50">
                          <CheckCircle2 className="w-3 h-3" />
                          MATCH (RED)
                        </span>
                      ) : (
                        <span className="text-[10px] text-slate-500 font-normal">
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
                          className={`py-2.5 px-4 border-r border-slate-800/80 whitespace-nowrap font-mono ${
                            isTarget && row.matched
                              ? 'text-rose-300 font-bold bg-rose-500/20'
                              : isTarget
                              ? 'text-slate-200'
                              : ''
                          }`}
                        >
                          {val || <span className="text-slate-600 italic">null</span>}
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
        <div className="text-[11px] text-slate-500 text-center pt-2">
          Previewing first {previewRows.length} of {totalRows} total rows. The full modified file with all {matchedCount} highlighted rows is ready in the downloaded Excel file.
        </div>
      )}
    </div>
  );
};
