import { useState, useEffect } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { createPortal } from 'react-dom';
import { Eye, X, ImageOff, Download, Loader2 } from 'lucide-react';
import type { ReceiptPreview } from '@/features/reconciliation/hooks/useReceiptPreview';
import { downloadUnmatchedImage } from '@/features/reconciliation/api/reconciliationApi';
import { saveOrShareImage } from '@/features/reconciliation/utils/imageSaveUtils';
import { useTranslation } from '@/shared/i18n/i18n';

interface ReceiptPreviewModalProps {
  preview: ReceiptPreview | null;
  setPreview: Dispatch<SetStateAction<ReceiptPreview | null>>;
  imageFiles?: File[];
  reconciliationId?: string;
  onSaveError: (message: string) => void;
}

export const ReceiptPreviewModal = ({
  preview,
  setPreview,
  imageFiles,
  reconciliationId,
  onSaveError,
}: ReceiptPreviewModalProps) => {
  const { t } = useTranslation();
  const [isSaving, setIsSaving] = useState(false);

  // Close preview modal on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setPreview(null);
      }
    };
    if (preview) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [preview, setPreview]);

  const handleSave = async () => {
    if (!preview || isSaving) return;
    setIsSaving(true);
    try {
      if (preview.file) {
        await saveOrShareImage(preview.file, preview.filename);
      } else if (reconciliationId && preview.item && preview.imageIndex !== undefined) {
        await downloadUnmatchedImage(reconciliationId, preview.imageIndex, preview.filename);
      } else if (preview.url) {
        const res = await fetch(preview.url);
        const blob = await res.blob();
        await saveOrShareImage(blob, preview.filename);
      }
    } catch (error: unknown) {
      onSaveError(error instanceof Error ? error.message : t('unmatched.downloadError'));
    } finally {
      setIsSaving(false);
    }
  };

  if (typeof document === 'undefined' || !preview) return null;

  // Click-to-Preview Modal Overlay rendered at document.body
  return createPortal(
    <div
      className="fixed inset-0 z-[9999] overflow-y-auto bg-black/85 p-2 backdrop-blur-sm sm:flex sm:items-center sm:justify-center sm:p-4"
      onClick={() => setPreview(null)}
    >
      <div
        className="mx-auto flex min-h-[calc(100dvh-1rem)] w-full max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-xl border border-[#3D404B] bg-[#1C1D22] shadow-2xl sm:min-h-0 sm:max-h-[90vh] sm:max-w-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#26272E] bg-[#16171B] px-4 py-3.5 sm:px-5">
          <div className="flex min-w-0 items-center gap-2 overflow-hidden">
            <Eye className="w-4 h-4 text-[#A0E3E2] shrink-0" />
            <div className="truncate">
              <h4 className="text-sm font-semibold text-white m-0">
                {t('unmatched.previewTitle')}
              </h4>
              <p className="text-[11px] text-[#8E929E] m-0 truncate">
                {preview.filename}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setPreview(null)}
            className="ml-3 flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-md text-[#8E929E] hover:bg-[#2B2D35] hover:text-white transition cursor-pointer"
            title={t('unmatched.close')}
            aria-label={t('unmatched.close')}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal Image View */}
        <div className="min-h-0 max-h-[calc(100dvh-9rem)] flex-1 overflow-y-auto bg-[#121316] p-4 flex items-center justify-center sm:max-h-[60vh]">
          {preview.url ? (
            <img
              src={preview.url}
              alt={preview.filename}
              className="max-h-[60vh] max-w-full object-contain rounded-lg border border-[#2B2D35] shadow-md"
              onError={() => {
                const currentFilename = preview.filename;
                const file = imageFiles?.find(
                  (f) =>
                    f.name === currentFilename ||
                    f.name.toLowerCase().trim() === currentFilename.toLowerCase().trim()
                );
                if (file) {
                  const reader = new FileReader();
                  reader.onload = () => {
                    if (typeof reader.result === 'string') {
                      setPreview((prev) =>
                        prev ? { ...prev, url: reader.result as string } : null
                      );
                    }
                  };
                  reader.readAsDataURL(file);
                }
              }}
            />
          ) : (
            <div className="text-center py-10 px-4 space-y-2 text-[#8E929E]">
              <ImageOff className="w-10 h-10 mx-auto opacity-50 text-[#8E929E]" />
              <p className="text-xs m-0">
                {preview.filename}
              </p>
            </div>
          )}
        </div>

        {/* Modal Footer with Barcode Badge */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#26272E] bg-[#16171B] px-4 py-3 sm:px-5">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="text-xs text-[#8E929E]">បាកូដ:</span>
            {preview.barcode ? (
              <span className="max-w-full break-all rounded border border-[#5C1D24] bg-[#461B21] px-2.5 py-0.5 font-mono text-sm font-bold text-[#FB7185]">
                {preview.barcode}
              </span>
            ) : (
              <span className="text-xs text-[#F59E0B] bg-[#3D2115] px-2.5 py-0.5 rounded border border-[#5C2B1D]">
                {t('unmatched.noBarcodeTag')}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={isSaving}
              className="inline-flex min-h-11 items-center justify-center gap-1.5 rounded-md bg-[#461B21] border border-[#FB7185]/40 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-[#5C2028] transition cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              title={t('unmatched.saveToGallery')}
            >
              {isSaving ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin text-[#FB7185]" />
              ) : (
                <Download className="w-3.5 h-3.5 text-[#FB7185]" />
              )}
              <span>{t('unmatched.saveImage')}</span>
            </button>

            <button
              type="button"
              onClick={() => setPreview(null)}
              className="min-h-11 shrink-0 rounded-md bg-[#24262E] px-3 py-1.5 text-xs font-medium text-[#D1D5DB] hover:bg-[#2D2F38] hover:text-white transition cursor-pointer"
            >
              {t('unmatched.close')}
            </button>
          </div>

          {/* Mobile guidance for iOS / iPhone users */}
          <p className="w-full m-0 text-[11px] text-[#8E929E] italic text-center sm:hidden">
            {t('unmatched.iosHint')}
          </p>
        </div>
      </div>
    </div>,
    document.body
  );
};
