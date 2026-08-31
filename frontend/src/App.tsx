import { useState } from 'react';
import confetti from 'canvas-confetti';
import {
  FileSpreadsheet,
  QrCode,
  AlertCircle,
  Sparkles,
} from 'lucide-react';
import { Header } from './components/Header';
import { FileUploadZone } from './components/FileUploadZone';
import { ReconciliationStats } from './components/ReconciliationStats';
import { ImageScanGrid } from './components/ImageScanGrid';
import { ExcelPreviewTable } from './components/ExcelPreviewTable';
import type { ReconciliationResponse } from './types';
import { reconcileFiles } from './services/api';

export function App() {
  const [excelFile, setExcelFile] = useState<File | null>(null);
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [isProcessing, setIsProcessing] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [results, setResults] = useState<ReconciliationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleStartReconcile = async () => {
    if (!excelFile || imageFiles.length === 0) return;

    setIsProcessing(true);
    setError(null);
    setUploadProgress(0);

    try {
      const res = await reconcileFiles(excelFile, imageFiles, {}, (percent) => {
        setUploadProgress(percent);
      });

      setResults(res);

      // Trigger confetti celebration
      try {
        confetti({
          particleCount: 80,
          spread: 70,
          origin: { y: 0.6 },
        });
      } catch {
        // ignore confetti errors
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'An unexpected error occurred during reconciliation';
      setError(msg);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleReset = () => {
    setResults(null);
    setError(null);
    setUploadProgress(0);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col selection:bg-rose-500 selection:text-white">
      {/* Navigation Header */}
      <Header
        onReset={handleReset}
        isProcessing={isProcessing}
        hasResults={!!results}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 space-y-8">
        {/* Error Banner */}
        {error && (
          <div className="bg-red-950/60 border border-red-500/50 rounded-2xl p-4 flex items-center justify-between gap-3 text-red-200 text-sm animate-in fade-in">
            <div className="flex items-center gap-2.5">
              <AlertCircle className="w-5 h-5 text-red-400 shrink-0" />
              <span>{error}</span>
            </div>
            <button
              onClick={() => setError(null)}
              className="text-xs font-semibold underline hover:text-white cursor-pointer"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Upload Section */}
        {!results ? (
          <div className="space-y-8 animate-in fade-in duration-300">
            {/* Hero Title */}
            <div className="text-center max-w-2xl mx-auto space-y-2 pt-4">
              <h2 className="text-3xl font-extrabold tracking-tight text-white m-0">
                Reconcile Barcode Images with Excel
              </h2>
              <p className="text-sm text-slate-400 m-0">
                Upload your inventory spreadsheet and photos of barcodes or QR codes. The system automatically decodes each image, matches against the catalog, and highlights matches in <span className="text-rose-400 font-semibold">RED</span>.
              </p>
            </div>

            {/* Dual Upload Card */}
            <FileUploadZone
              excelFile={excelFile}
              imageFiles={imageFiles}
              onSelectExcel={setExcelFile}
              onSelectImages={setImageFiles}
              onStartReconcile={handleStartReconcile}
              isProcessing={isProcessing}
              uploadProgress={uploadProgress}
            />

            {/* Feature Highlights Footer */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-4 text-left">
              <div className="p-4 rounded-xl bg-slate-900/40 border border-slate-800/60 flex items-start gap-3">
                <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 shrink-0">
                  <FileSpreadsheet className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wide m-0">
                    Smart Multi-Sheet Engine
                  </h4>
                  <p className="text-xs text-slate-400 m-0 mt-1">
                    Auto-detects data tables across all workbook sheets, skips title banners, and highlights matching rows in red.
                  </p>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/40 border border-slate-800/60 flex items-start gap-3">
                <div className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400 shrink-0">
                  <QrCode className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wide m-0">
                    Adaptive Label Auto-Cropping
                  </h4>
                  <p className="text-xs text-slate-400 m-0 mt-1">
                    Isolates white sticker labels from full camera scenes with multi-scale contrast binarization.
                  </p>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/40 border border-slate-800/60 flex items-start gap-3">
                <div className="p-2 rounded-lg bg-purple-500/10 text-purple-400 shrink-0">
                  <Sparkles className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wide m-0">
                    AI Vision Intelligence
                  </h4>
                  <p className="text-xs text-slate-400 m-0 mt-1">
                    Extracts barcodes, QR codes, and SKU text from complex, 3D-angled, or blurry photos.
                  </p>
                </div>
              </div>
            </div>
          </div>
        ) : (
          /* Results Section */
          <div className="space-y-6 animate-in fade-in duration-300">
            {/* Statistics Cards & Download Banner */}
            <ReconciliationStats results={results} />

            {/* Excel Preview Spreadsheet Table */}
            <ExcelPreviewTable
              columns={results.columns}
              previewRows={results.previewRows}
              matchedColumnName={results.matchedColumnName}
              activeSheetName={results.activeSheetName}
              totalRows={results.excelTotalRows}
              matchedCount={results.matchedRowsCount}
              highlightedExcelBase64={results.highlightedExcelBase64}
              downloadFileName={results.downloadFileName}
            />

            {/* Matched Barcode Cards */}
            <ImageScanGrid
              scanResults={results.scanResults}
              matchedCodes={results.matchedCodes}
            />
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
