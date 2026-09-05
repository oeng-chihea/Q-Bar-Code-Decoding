import React, { useRef, useState, useEffect, useMemo } from 'react';
import {
  FileImage,
  Images,
  UploadCloud,
  FileCheck,
  Trash2,
  Play,
  Loader2,
  AlertCircle,
  AlertTriangle,
  Sparkles,
  X,
  Plus,
} from 'lucide-react';
import { isImageFile, validateExcelUpload } from '@/features/reconciliation/model/fileValidation';
import { useTranslation } from '@/shared/i18n/i18n';
import type { TranslationKey } from '@/shared/i18n/i18n';

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
  const { t } = useTranslation();
  const excelInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);

  const [excelDragOver, setExcelDragOver] = useState(false);
  const [imageDragOver, setImageDragOver] = useState(false);
  const [excelErrorKey, setExcelErrorKey] = useState<TranslationKey | null>(null);

  // Memoize preview URL for Excel table image
  const excelPreviewUrl = useMemo(() => {
    if (excelFile && isImageFile(excelFile)) {
      return URL.createObjectURL(excelFile);
    }
    return null;
  }, [excelFile]);

  useEffect(() => {
    return () => {
      if (excelPreviewUrl) {
        URL.revokeObjectURL(excelPreviewUrl);
      }
    };
  }, [excelPreviewUrl]);

  // Memoize preview URLs and cleanup previous blob URLs when imageFiles change or component unmounts
  const imagePreviews = useMemo(() => {
    return imageFiles.map((file) => ({
      file,
      url: URL.createObjectURL(file),
    }));
  }, [imageFiles]);

  useEffect(() => {
    return () => {
      imagePreviews.forEach((p) => URL.revokeObjectURL(p.url));
    };
  }, [imagePreviews]);

  const validateAndSetExcelFile = (file: File) => {
    setExcelErrorKey(null);

    const validation = validateExcelUpload(file);
    if (validation.accepted) {
      onSelectExcel(file);
      return;
    }

    setExcelErrorKey(validation.errorKey ?? 'errors.unsupported');
  };

  const handleExcelDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setExcelDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      validateAndSetExcelFile(e.dataTransfer.files[0]);
    }
  };

  const handleImageDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setImageDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const newImages = Array.from(e.dataTransfer.files).filter((file) =>
        isImageFile(file)
      );
      onSelectImages([...imageFiles, ...newImages]);
    }
  };

  const handleRemoveSingleImage = (indexToRemove: number) => {
    const updated = imageFiles.filter((_, idx) => idx !== indexToRemove);
    onSelectImages(updated);
  };

  const handleClearAllImages = () => {
    onSelectImages([]);
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
          className={`relative rounded-lg border-2 border-dashed p-5 transition-all cursor-pointer flex flex-col justify-between min-h-[250px] ${
            excelDragOver
              ? 'border-[#34D399] bg-[#143827]/30'
              : excelFile
              ? 'border-[#1E4D36] bg-[#1C1D22] cursor-default'
              : 'border-[#2B2D35] bg-[#1C1D22] hover:border-[#3D404B] hover:bg-[#202127]'
          }`}
        >
          <input
            ref={excelInputRef}
            type="file"
            accept=".png, .jpg, .jpeg, .webp, image/png, image/jpeg, image/webp"
            className="hidden"
            onChange={(e) => {
              if (e.target.files && e.target.files.length > 0) {
                validateAndSetExcelFile(e.target.files[0]);
                e.target.value = '';
              }
            }}
          />

          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2 text-xs font-semibold text-[#34D399] uppercase tracking-wider">
                <FileImage className="w-4 h-4" />
                {t('upload.step1')}
              </div>
              {excelFile && (
                <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded bg-[#143827] text-[#34D399] border border-[#1E4D36]">
                  <FileCheck className="w-3.5 h-3.5" /> {t('upload.spreadsheetReady')}
                </span>
              )}
            </div>

            {/* Error Message for an invalid Excel file */}
            {excelErrorKey && (
              <div className="mb-3 p-2.5 rounded bg-[#461B21]/90 border border-[#FB7185]/60 text-[#FCA5A5] text-xs flex items-start gap-2 animate-in fade-in">
                <AlertTriangle className="w-4 h-4 text-[#FB7185] shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span>{t(excelErrorKey)}</span>
                </div>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setExcelErrorKey(null);
                  }}
                  className="text-[#D1D5DB] hover:text-white p-0.5"
                >
                  <X className="w-3 h-3" />
                </button>
              </div>
            )}

            {!excelFile ? (
              <div className="flex flex-col items-center justify-center py-6 text-center">
                <div className="p-3 rounded-md bg-[#24262E] text-[#34D399] mb-3 border border-[#2D2F36]">
                  <FileImage className="w-6 h-6" />
                </div>
                <div className="text-sm font-medium text-[#F3F4F6] mb-1">
                  {t('upload.browse')}
                </div>
                <div className="text-xs text-[#8E929E] space-y-0.5">
                  <div>{t('upload.supports')}</div>
                  <div className="text-[11px] text-[#A0A5B5]">{t('upload.tableHint')}</div>
                </div>
              </div>
            ) : (
              <div className="bg-[#16171B] border border-[#2B2D35] rounded-md p-3.5 flex items-center justify-between">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="p-1 rounded bg-[#143827] text-[#34D399] shrink-0 border border-[#1E4D36] w-10 h-10 flex items-center justify-center overflow-hidden">
                    {excelPreviewUrl ? (
                      <img
                        src={excelPreviewUrl}
                        alt={excelFile.name}
                        className="w-full h-full object-cover rounded"
                      />
                    ) : (
                      <FileImage className="w-5 h-5" />
                    )}
                  </div>
                  <div className="truncate">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm font-medium text-[#F3F4F6] truncate">
                        {excelFile.name}
                      </span>
                    </div>
                    <div className="text-xs text-[#8E929E]">
                      {formatFileSize(excelFile.size)} &bull; {t('upload.tableImage')}
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectExcel(null);
                    setExcelErrorKey(null);
                  }}
                  className="p-1.5 rounded text-[#8E929E] hover:text-[#FB7185] hover:bg-[#461B21] transition cursor-pointer"
                  title={t('upload.remove')}
                  aria-label={t('upload.remove')}
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>

          <div className="text-[11px] text-[#737887] flex items-center gap-1 mt-4">
            <AlertCircle className="w-3.5 h-3.5 shrink-0 text-[#34D399]" />
            <span>{t('upload.note')}</span>
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
          onClick={() => imageFiles.length === 0 && imageInputRef.current?.click()}
          className={`relative rounded-lg border-2 border-dashed p-5 transition-all flex flex-col justify-between min-h-[250px] ${
            imageDragOver
              ? 'border-[#A0E3E2] bg-[#12403F]/30'
              : imageFiles.length > 0
              ? 'border-[#23585D] bg-[#1C1D22]'
              : 'border-[#2B2D35] bg-[#1C1D22] hover:border-[#3D404B] hover:bg-[#202127] cursor-pointer'
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
                e.target.value = '';
              }
            }}
          />

          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2 text-xs font-semibold text-[#A0E3E2] uppercase tracking-wider">
                <Images className="w-4 h-4" />
                {t('upload.step2')}
              </div>
              {imageFiles.length > 0 && (
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold px-2 py-0.5 rounded bg-[#18393C] text-[#A0E3E2] border border-[#23585D]">
                    {imageFiles.length} {imageFiles.length === 1 ? t('upload.image') : t('upload.images')} (
                    {formatFileSize(totalImageSize)})
                  </span>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleClearAllImages();
                    }}
                    className="flex items-center gap-1 px-2 py-0.5 rounded text-xs text-[#8E929E] hover:text-[#FB7185] hover:bg-[#461B21] transition cursor-pointer border border-transparent hover:border-[#FB7185]/30"
                    title={t('upload.clearAll')}
                    aria-label={t('upload.clearAll')}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>{t('upload.clearAll')}</span>
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
                  {t('upload.dropzone')}
                </div>
                <div className="text-xs text-[#8E929E]">
                  {t('upload.batchCount')}
                </div>
              </div>
            ) : (
              <div className="space-y-3" onClick={(e) => e.stopPropagation()}>
                {/* Responsive thumbnail grid showing each image with remove button */}
                <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-5 lg:grid-cols-7 xl:grid-cols-8 gap-2 max-h-40 overflow-y-auto pr-1">
                  {imagePreviews.map((preview, idx) => (
                    <div
                      key={`${preview.file.name}-${preview.file.size}-${preview.file.lastModified}-${idx}`}
                      className="relative group rounded overflow-hidden border border-[#2B2D35] hover:border-[#A0E3E2]/70 bg-[#16171B] aspect-square transition-all shadow-sm"
                      title={`${preview.file.name} (${formatFileSize(preview.file.size)})`}
                    >
                      <img
                        src={preview.url}
                        alt={preview.file.name}
                        className="w-full h-full object-cover"
                      />

                      {/* Dark overlay on hover */}
                      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />

                      {/* Remove single photo button */}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleRemoveSingleImage(idx);
                        }}
                        className="absolute top-1 right-1 p-0.5 rounded-full bg-[#121316]/90 text-[#D1D5DB] hover:text-white hover:bg-[#FB7185] active:scale-90 transition-all cursor-pointer shadow-md z-10 opacity-80 group-hover:opacity-100"
                        title={`${t('upload.remove')}: ${preview.file.name}`}
                        aria-label={`${t('upload.remove')}: ${preview.file.name}`}
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>

                      {/* Filename footer on hover */}
                      <div className="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/90 via-black/60 to-transparent px-1 py-0.5 text-[9px] text-[#D1D5DB] truncate opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none text-center font-mono">
                        {preview.file.name}
                      </div>
                    </div>
                  ))}
                </div>

                <button
                  type="button"
                  onClick={() => imageInputRef.current?.click()}
                  className="w-full py-1.5 text-xs text-center font-medium text-[#A0E3E2] hover:text-white bg-[#172D30] rounded border border-[#23585D] hover:bg-[#1E3B3E] transition cursor-pointer flex items-center justify-center gap-1.5"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>{t('upload.addMore')}</span>
                </button>
              </div>
            )}
          </div>

          <div className="text-[11px] text-[#737887] flex items-center gap-1 mt-4">
            <Sparkles className="w-3.5 h-3.5 shrink-0 text-[#A0E3E2]" />
            <span>{t('upload.parallel')}</span>
          </div>
        </div>
      </div>

      {/* Start Button & Progress */}
      <div className="bg-[#1C1D22] border border-[#2B2D35] rounded-lg p-4 flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="text-left text-xs text-[#8E929E]">
          <div>
            <span className="font-semibold text-[#F3F4F6]">{t('upload.readySummary')}</span>{' '}
            {excelFile
              ? `1 ${t(isImageFile(excelFile) ? 'upload.tableImage' : 'upload.spreadsheetSummary')}`
              : t('upload.noExcel')} &bull;{' '}
            {imageFiles.length} {imageFiles.length === 1 ? t('upload.barcodeImage') : t('upload.barcodeImages')}
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
                {t('upload.processing')} {uploadProgress > 0 && `(${uploadProgress}%)`}
              </span>
            </>
          ) : (
            <>
              <Play className="w-3.5 h-3.5 fill-[#0E1726] text-[#0E1726]" />
              <span>{t('upload.start')}</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
};
