export interface BarcodeResult {
  filename: string;
  decodedValue?: string;
  allExtractedValues?: string[];
  decoderType: 'ZXING' | 'GEMINI_AI' | 'FAILED';
  success: boolean;
  barcodeFormat?: string;
  errorMessage?: string;
  previewUrl?: string;
}

export interface ExcelRowPreview {
  rowIndex: number;
  cells: Record<string, string>;
  barcodeValue: string;
  matched: boolean;
}

export interface ReconciliationResponse {
  totalImages: number;
  decodedImagesCount: number;
  excelTotalRows: number;
  matchedRowsCount: number;
  unmatchedImagesCount: number;
  matchedColumnName: string;
  activeSheetName?: string;
  columns: string[];
  scanResults: BarcodeResult[];
  allDecodedCodes: string[];
  matchedCodes: string[];
  unmatchedCodes: string[];
  previewRows: ExcelRowPreview[];
  highlightedExcelBase64: string;
  downloadFileName: string;
  executionTimeMs: number;
}

export interface ReconciliationConfig {
  columnName: string;
  highlightFullRow: boolean;
  geminiApiKey: string;
}
