import React, { useRef, useState } from 'react';
import {
  FileSpreadsheet,
  Images,
  UploadCloud,
  FileCheck,
  Trash2,
  Play,
  Loader2,
  AlertCircle,
  Sparkles,
} from 'lucide-react';

interface FileUploadZoneProps {
  excelFile: File | null;
  imageFiles: File[];
  onSelectExcel: (file: File | null) => void;
  onSelectImages: (files: File[]) => void;
  onStartReconcile: () => void;
  isProcessing: boolean;
  uploadProgress: number;
}

export const FileUploadZone: React.FC<FileUploadZoneProps> = ({
  excelFile,
  imageFiles,
  onSelectExcel,
  onSelectImages,
  onStartReconcile,
  isProcessing,
  uploadProgress,
}) => {
  const excelInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);

  const [excelDragOver, setExcelDragOver] = useState(false);
  const [imageDragOver, setImageDragOver] = useState(false);

  const handleExcelDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setExcelDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      if (file.name.match(/\.(xlsx|xls|csv)$/i)) {
        onSelectExcel(file);
      }
    }
  };

  const handleImageDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setImageDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const newImages = Array.from(e.dataTransfer.files).filter((file) =>
        file.type.startsWith('image/') || file.name.match(/\.(png|jpg|jpeg|webp|heic)$/i)
      );
      onSelectImages([...imageFiles, ...newImages]);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const totalImageSize = imageFiles.reduce((acc, file) => acc + file.size, 0);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-left">
        {/* 1. Excel File Upload Card */}
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setExcelDragOver(true);
          }}
          onDragLeave={() => setExcelDragOver(false)}
          onDrop={handleExcelDrop}
          onClick={() => !excelFile && excelInputRef.current?.click()}
          className={`relative rounded-2xl border-2 border-dashed p-6 transition-all cursor-pointer flex flex-col justify-between min-h-[260px] ${
            excelDragOver
              ? 'border-emerald-500 bg-emerald-950/20'
              : excelFile
              ? 'border-emerald-500/60 bg-slate-900/80 cursor-default'
              : 'border-slate-800 bg-slate-900/40 hover:border-slate-700 hover:bg-slate-900/60'
          }`}
        >
          <input
            ref={excelInputRef}
            type="file"
            accept=".xlsx, .xls, .csv"
            className="hidden"
            onChange={(e) => {
              if (e.target.files && e.target.files.length > 0) {
                onSelectExcel(e.target.files[0]);
              }
            }}
          />

          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-emerald-400">
                <FileSpreadsheet className="w-5 h-5" />
                Step 1: Excel Spreadsheet
              </div>
              {excelFile && (
                <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <FileCheck className="w-3.5 h-3.5" /> Ready
                </span>
              )}
            </div>

            {!excelFile ? (
              <div className="flex flex-col items-center justify-center py-6 text-center">
                <div className="p-3 rounded-full bg-slate-800/80 text-emerald-400 mb-3">
                  <UploadCloud className="w-7 h-7" />
                </div>
                <div className="text-sm font-medium text-slate-200 mb-1">
                  Click to browse or drag &amp; drop Excel file
                </div>
                <div className="text-xs text-slate-400">
                  Supports .xlsx, .xls containing "QR Barcode" column
                </div>
              </div>
            ) : (
              <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="p-2.5 rounded-lg bg-emerald-500/10 text-emerald-400 shrink-0">
                    <FileSpreadsheet className="w-6 h-6" />
                  </div>
                  <div className="truncate">
                    <div className="text-sm font-medium text-slate-100 truncate">
                      {excelFile.name}
                    </div>
                    <div className="text-xs text-slate-400">
                      {formatFileSize(excelFile.size)}
                    </div>
                  </div>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectExcel(null);
                  }}
                  className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition cursor-pointer"
                  title="Remove file"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>

          <div className="text-[11px] text-slate-500 flex items-center gap-1 mt-4">
            <AlertCircle className="w-3.5 h-3.5 shrink-0" />
            <span>Matching rows will be styled with red background.</span>
          </div>
        </div>

        {/* 2. Images Batch Upload Card */}
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setImageDragOver(true);
          }}
          onDragLeave={() => setImageDragOver(false)}
          onDrop={handleImageDrop}
          onClick={() => imageInputRef.current?.click()}
          className={`relative rounded-2xl border-2 border-dashed p-6 transition-all cursor-pointer flex flex-col justify-between min-h-[260px] ${
            imageDragOver
              ? 'border-indigo-500 bg-indigo-950/20'
              : imageFiles.length > 0
              ? 'border-indigo-500/60 bg-slate-900/80 cursor-default'
              : 'border-slate-800 bg-slate-900/40 hover:border-slate-700 hover:bg-slate-900/60'
          }`}
        >
          <input
            ref={imageInputRef}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => {
              if (e.target.files) {
                const newFiles = Array.from(e.target.files);
                onSelectImages([...imageFiles, ...newFiles]);
              }
            }}
          />

          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-indigo-400">
                <Images className="w-5 h-5" />
                Step 2: Barcode / QR Images
              </div>
              {imageFiles.length > 0 && (
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                    {imageFiles.length} {imageFiles.length === 1 ? 'Image' : 'Images'} (
                    {formatFileSize(totalImageSize)})
                  </span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectImages([]);
                    }}
                    className="p-1 rounded text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition cursor-pointer"
                    title="Clear all images"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </div>

            {imageFiles.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-6 text-center">
                <div className="p-3 rounded-full bg-slate-800/80 text-indigo-400 mb-3">
                  <UploadCloud className="w-7 h-7" />
                </div>
                <div className="text-sm font-medium text-slate-200 mb-1">
                  Select or drag &amp; drop multiple barcode/QR images
                </div>
                <div className="text-xs text-slate-400">
                  Upload batch of 40+ photos (.png, .jpg, .webp)
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="grid grid-cols-6 gap-2 max-h-28 overflow-y-auto pr-1">
                  {imageFiles.slice(0, 12).map((file, idx) => (
                    <div
                      key={idx}
                      className="relative group rounded-lg overflow-hidden border border-slate-800 bg-slate-950 aspect-square"
                    >
                      <img
                        src={URL.createObjectURL(file)}
                        alt={file.name}
                        className="w-full h-full object-cover"
                      />
                    </div>
                  ))}
                  {imageFiles.length > 12 && (
                    <div className="rounded-lg border border-slate-800 bg-slate-950/80 flex items-center justify-center text-xs font-semibold text-slate-400 aspect-square">
                      +{imageFiles.length - 12}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => imageInputRef.current?.click()}
                  className="w-full py-1.5 text-xs text-center font-medium text-indigo-400 hover:text-indigo-300 bg-indigo-950/30 rounded-lg border border-indigo-900/50 hover:bg-indigo-950/60 transition cursor-pointer"
                >
                  + Add More Images
                </button>
              </div>
            )}
          </div>

          <div className="text-[11px] text-slate-500 flex items-center gap-1 mt-4">
            <Sparkles className="w-3.5 h-3.5 shrink-0 text-amber-400" />
            <span>Parallel workers decode images concurrently in real time.</span>
          </div>
        </div>
      </div>

      {/* Start Button & Progress */}
      <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-4 flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="text-left text-xs text-slate-400">
          <div>
            <span className="font-semibold text-slate-200">Reconciliation Ready:</span>{' '}
            {excelFile ? '1 Excel spreadsheet' : 'No Excel'} &bull;{' '}
            {imageFiles.length} {imageFiles.length === 1 ? 'image' : 'images'}
          </div>
        </div>

        <button
          onClick={onStartReconcile}
          disabled={!excelFile || imageFiles.length === 0 || isProcessing}
          className="w-full md:w-auto px-8 py-3.5 rounded-xl font-semibold text-sm text-white bg-gradient-to-r from-rose-600 to-indigo-600 hover:from-rose-500 hover:to-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed shadow-xl shadow-rose-600/20 transition-all flex items-center justify-center gap-2.5 cursor-pointer"
        >
          {isProcessing ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>
                Processing &amp; Reconciling... {uploadProgress > 0 && `(${uploadProgress}%)`}
              </span>
            </>
          ) : (
            <>
              <Play className="w-4 h-4 fill-white" />
              <span>Start Reconcile &amp; Highlight</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
};
