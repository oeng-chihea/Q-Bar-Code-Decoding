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
    if (e.dataTran sfer.files && e.dataTransfer.files.length > 0) {
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
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-left">
        {/* 1. Excel File Upload Card */}
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setExcelDragOver(true);
          }}
          onDragLeave={() => setExcelDragOver(false)}
          onDrop={handleExcelDrop}
          onClick={() => !excelFile && excelInputRef.current?.click()}
          className={`relative rounded-lg border-2 border-dashed p-5 transition-all cursor-pointer flex flex-col justify-between min-h-[250px] ${excelDragOver
              ? 'border-[#34D399] bg-[#143827]/30'
              : excelFile
                ? 'border-[#1E4D36] bg-[#1C1D22] cursor-default'
                : 'border-[#2B2D35] bg-[#1C1D22] hover:border-[#3D404B] hover:bg-[#202127]'
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
              <div className="flex items-center gap-2 text-xs font-semibold text-[#34D399] uppercase tracking-wider">
                <FileSpreadsheet className="w-4 h-4" />
                Step 1: Excel Spreadsheet
              </div>
              {excelFile && (
                <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded bg-[#143827] text-[#34D399] border border-[#1E4D36]">
                  <FileCheck className="w-3.5 h-3.5" /> Ready
                </span>
              )}
            </div>

            {!excelFile ? (
              <div className="flex flex-col items-center justify-center py-6 text-center">
                <div className="p-3 rounded-md bg-[#24262E] text-[#34D399] mb-3 border border-[#2D2F36]">
                  <UploadCloud className="w-6 h-6" />
                </div>
                <div className="text-sm font-medium text-[#F3F4F6] mb-1">
                  Click to browse or drag &amp; drop Excel file
                </div>
                <div className="text-xs text-[#8E929E]">
                  Supports .xlsx, .xls containing "QR Barcode" column
                </div>
              </div>
            ) : (
              <div className="bg-[#16171B] border border-[#2B2D35] rounded-md p-3.5 flex items-center justify-between">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="p-2 rounded bg-[#143827] text-[#34D399] shrink-0 border border-[#1E4D36]">
                    <FileSpreadsheet className="w-5 h-5" />
                  </div>
                  <div className="truncate">
                    <div className="text-sm font-medium text-[#F3F4F6] truncate">
                      {excelFile.name}
                    </div>
                    <div className="text-xs text-[#8E929E]">
                      {formatFileSize(excelFile.size)}
                    </div>
                  </div>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectExcel(null);
                  }}
                  className="p-1.5 rounded text-[#8E929E] hover:text-[#FB7185] hover:bg-[#461B21] transition cursor-pointer"
                  title="Remove file"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>

          <div className="text-[11px] text-[#737887] flex items-center gap-1 mt-4">
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
          className={`relative rounded-lg border-2 border-dashed p-5 transition-all cursor-pointer flex flex-col justify-between min-h-[250px] ${imageDragOver
              ? 'border-[#A0E3E2] bg-[#12403F]/30'
              : imageFiles.length > 0
                ? 'border-[#23585D] bg-[#1C1D22] cursor-default'
                : 'border-[#2B2D35] bg-[#1C1D22] hover:border-[#3D404B] hover:bg-[#202127]'
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
              <div className="flex items-center gap-2 text-xs font-semibold text-[#A0E3E2] uppercase tracking-wider">
                <Images className="w-4 h-4" />
                Step 2: Barcode / QR Images
              </div>
              {imageFiles.length > 0 && (
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold px-2 py-0.5 rounded bg-[#18393C] text-[#A0E3E2] border border-[#23585D]">
                    {imageFiles.length} {imageFiles.length === 1 ? 'Image' : 'Images'} (
                    {formatFileSize(totalImageSize)})
                  </span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectImages([]);
                    }}
                    className="p-1 rounded text-[#8E929E] hover:text-[#FB7185] hover:bg-[#461B21] transition cursor-pointer"
                    title="Clear all images"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </div>

            {imageFiles.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-6 text-center">
                <div className="p-3 rounded-md bg-[#24262E] text-[#A0E3E2] mb-3 border border-[#2D2F36]">
                  <UploadCloud className="w-6 h-6" />
                </div>
                <div className="text-sm font-medium text-[#F3F4F6] mb-1">
                  Select or drag &amp; drop multiple barcode/QR images
                </div>
                <div className="text-xs text-[#8E929E]">
                  Upload batch of 40+ photos (.png, .jpg, .webp)
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="grid grid-cols-4 sm:grid-cols-6 lg:grid-cols-8 xl:grid-cols-10 gap-2 max-h-32 overflow-y-auto pr-1">
                  {imageFiles.slice(0, 16).map((file, idx) => (
                    <div
                      key={idx}
                      className="relative group rounded overflow-hidden border border-[#2B2D35] bg-[#16171B] aspect-square"
                    >
                      <img
                        src={URL.createObjectURL(file)}
                        alt={file.name}
                        className="w-full h-full object-cover"
                      />
                    </div>
                  ))}
                  {imageFiles.length > 16 && (
                    <div className="rounded border border-[#2B2D35] bg-[#202126] flex items-center justify-center text-xs font-semibold text-[#8E929E] aspect-square">
                      +{imageFiles.length - 16}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => imageInputRef.current?.click()}
                  className="w-full py-1.5 text-xs text-center font-medium text-[#A0E3E2] hover:text-white bg-[#172D30] rounded border border-[#23585D] hover:bg-[#1E3B3E] transition cursor-pointer"
                >
                  + Add More Images
                </button>
              </div>
            )}
          </div>

          <div className="text-[11px] text-[#737887] flex items-center gap-1 mt-4">
            <Sparkles className="w-3.5 h-3.5 shrink-0 text-[#A0E3E2]" />
            <span>Parallel workers decode images concurrently in real time.</span>
          </div>
        </div>
      </div>

      {/* Start Button & Progress */}
      <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-4 flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="text-left text-xs text-[#8E929E]">
          <div>
            <span className="font-semibold text-[#F3F4F6]">Reconciliation Ready:</span>{' '}
            {excelFile ? '1 Excel spreadsheet' : 'No Excel'} &bull;{' '}
            {imageFiles.length} {imageFiles.length === 1 ? 'image' : 'images'}
          </div>
        </div>

        <button
          onClick={onStartReconcile}
          disabled={!excelFile || imageFiles.length === 0 || isProcessing}
          className="w-full md:w-auto px-6 py-2.5 rounded-md font-semibold text-xs text-[#0E1726] bg-[#A0E3E2] hover:bg-[#8EE0DF] active:bg-[#7AD8D7] disabled:opacity-40 disabled:cursor-not-allowed shadow-sm transition-all flex items-center justify-center gap-2 cursor-pointer"
        >
          {isProcessing ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin text-[#0E1726]" />
              <span>
                Processing &amp; Reconciling... {uploadProgress > 0 && `(${uploadProgress}%)`}
              </span>
            </>
          ) : (
            <>
              <Play className="w-3.5 h-3.5 fill-[#0E1726] text-[#0E1726]" />
              <span>Start Reconcile &amp; Highlight</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
};
