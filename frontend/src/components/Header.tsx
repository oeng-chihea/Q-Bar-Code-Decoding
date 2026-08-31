import React from 'react';
import { QrCode, RefreshCw } from 'lucide-react';

interface HeaderProps {
  onReset?: () => void;
  isProcessing?: boolean;
  hasResults?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  onReset,
  isProcessing,
  hasResults,
}) => {
  return (
    <header className="sticky top-0 z-30 backdrop-blur-md bg-[#16171B]/90 border-b border-[#26272E] px-6 py-4 w-full">
      <div className="w-full flex items-center justify-between">
        {/* Logo & Title */}
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-md bg-[#202126] border border-[#2D2F36] text-[#A0E3E2] flex items-center justify-center shadow-sm">
            <QrCode className="w-6 h-6 text-[#A0E3E2]" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white m-0">
              Excel Barcode Reconciler
            </h1>
            <p className="text-xs text-[#8E929E] m-0 mt-0.5">
              Batch decode barcodes/QRs, compare against Excel &amp; highlight matches in red
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-3">
          {hasResults && onReset && (
            <button
              onClick={onReset}
              disabled={isProcessing}
              className="flex items-center gap-2 px-3.5 py-1.5 rounded-md text-xs font-semibold text-[#F3F4F6] bg-[#202126] hover:bg-[#282A31] border border-[#2D2F36] transition disabled:opacity-50 cursor-pointer shadow-sm"
            >
              <RefreshCw className="w-3.5 h-3.5 text-[#A0E3E2]" />
              New Scan
            </button>
          )}
        </div>
      </div>
    </header>
  );
};

