import { useState } from 'react';
import confetti from 'canvas-confetti';
import { AlertCircle } from 'lucide-react';
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
    <div className="min-h-screen bg-[#121316] text-[#F3F4F6] flex flex-col selection:bg-[#A0E3E2] selection:text-[#0E1726]">
      {/* Navigation Header */}
      <Header
        onReset={handleReset}
        isProcessing={isProcessing}
        hasResults={!!results}
      />

      {/* Main Content Area */}
      <main className="flex-1 w-full p-6 space-y-8">
        {/* Error Banner */}
        {error && (
          <div className="bg-[#461B21]/60 border border-[#FB7185]/40 rounded-md p-3.5 flex items-center justify-between gap-3 text-[#FCA5A5] text-sm animate-in fade-in">
            <div className="flex items-center gap-2.5">
              <AlertCircle className="w-5 h-5 text-[#FB7185] shrink-0" />
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
              <h2 className="text-3xl font-bold tracking-tight text-white m-0">
                Reconcile Barcode Images with Excel
              </h2>
              <p className="text-sm text-[#8E929E] m-0">
                Upload your inventory spreadsheet and photos of barcodes or QR codes. The system automatically decodes each image, matches against the catalog, and highlights matches in <span className="text-[#FB7185] font-semibold">RED</span>.
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

