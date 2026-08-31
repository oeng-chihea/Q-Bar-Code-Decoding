import React from 'react';
import { FileSpreadsheet, QrCode, Sparkles, RefreshCw } from 'lucide-react';

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
    <header className="sticky top-0 z-30 backdrop-blur-md bg-slate-950/80 border-b border-slate-800/80 px-6 py-4">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Logo & Title */}
        <div className="flex items-center gap-3">
          <div className="relative p-2.5 rounded-xl bg-gradient-to-tr from-indigo-600 via-rose-500 to-amber-400 shadow-lg shadow-rose-500/20 text-white flex items-center justify-center">
            <FileSpreadsheet className="w-6 h-6 absolute -top-1 -left-1 text-white/90" />
            <QrCode className="w-6 h-6 relative z-10 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold tracking-tight text-white m-0">
                Excel Barcode Reconciler
              </h1>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/30">
                <Sparkles className="w-3 h-3" />
                AI-Powered
              </span>
            </div>
            <p className="text-xs text-slate-400 m-0">
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
              className="flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold text-white bg-slate-800 hover:bg-slate-700 border border-slate-600 transition disabled:opacity-50 cursor-pointer shadow-sm"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              New Scan
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
