import React from 'react';
import { QrCode, RefreshCw } from 'lucide-react';
import { useTranslation } from '@/shared/i18n/i18n';

interface HeaderProps {
  onReset?: () => void;
  isProcessing?: boolean;
  hasResults?: boolean;
}

export const AppHeader: React.FC<HeaderProps> = ({
  onReset,
  isProcessing,
  hasResults,
}) => {
  const { t } = useTranslation();

  return (
    <header className="sticky top-0 z-30 w-full border-b border-[#26272E] bg-[#16171B]/90 px-4 sm:px-6 py-3.5 sm:py-4 backdrop-blur-md">
      <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5 sm:gap-3">
          <div className="flex shrink-0 items-center justify-center rounded-md border border-[#2D2F36] bg-[#202126] p-2 text-[#A0E3E2] shadow-sm sm:p-2.5">
            <QrCode className="h-5 w-5 text-[#A0E3E2] sm:h-6 sm:w-6" />
          </div>
          <div className="min-w-0">
            <h1 className="m-0 truncate text-base sm:text-xl font-bold leading-tight tracking-tight text-white">
              {t('header.title')}
            </h1>
            <p className="m-0 mt-0.5 hidden truncate text-xs text-[#8E929E] sm:block">
              {t('header.subtitle')}
            </p>
          </div>
        </div>

        {hasResults && onReset && (
          <button
            type="button"
            onClick={onReset}
            disabled={isProcessing}
            aria-label={t('header.newScan')}
            className="flex min-h-11 shrink-0 items-center gap-2 rounded-md border border-[#2D2F36] bg-[#202126] px-3.5 py-1.5 text-xs font-semibold text-[#F3F4F6] shadow-sm transition hover:bg-[#282A31] disabled:cursor-not-allowed disabled:opacity-50"
          >
            <RefreshCw className="w-3.5 h-3.5 text-[#A0E3E2]" />
            <span className="hidden sm:inline">{t('header.newScan')}</span>
          </button>
        )}
      </div>
    </header>
  );
};
