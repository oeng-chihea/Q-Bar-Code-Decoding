export interface BarcodeResult {
  filename: string;
  decodedValue?: string;
  allExtractedValues?: string[];
  decoderType: 'ZXING' | 'OLLAMA_AI' | 'FAILED';
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
  matchedColumnConfidence?: number;
  identifierColumnIndexes?: number[];
  activeSheetName?: string;
  columns: string[];
  scanResults: BarcodeResult[];
  allDecodedCodes: string[];
  matchedCodes: string[];
  unmatchedCodes: string[];
  previewRows: ExcelRowPreview[];
  highlightedExcelBase64: string;
  downloadFileName: string;
  excelSourceType?: 'EXCEL_FILE' | 'EXCEL_TABLE_IMAGE';
  executionTimeMs: number;
}

export interface ReconciliationConfig {
  columnName: string;
  highlightFullRow: boolean;
}
