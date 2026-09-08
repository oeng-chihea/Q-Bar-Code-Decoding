export interface BarcodeResult {
  filename: string;
  decodedValue?: string;
  allExtractedValues?: string[];
  decoderType: 'ZXING' | 'GEMINI_AI' | 'FAILED';
  success: boolean;
  barcodeFormat?: string;
  errorMessage?: string;
  matched?: boolean;
  previewUrl?: string;
}

export interface UnmatchedImageDownload {
  imageIndex: number;
  filename: string;
  contentType: string;
  size: number;
  downloadUrl: string;
}

export interface UnmatchedImagesResponse {
  images: UnmatchedImageDownload[];
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
  excelSourceType?: 'EXCEL_TABLE_IMAGE';
  executionTimeMs: number;
}

export interface ReconciliationConfig {
  columnName: string;
  highlightFullRow: boolean;
}

export type ReconciliationStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface ReconciliationSubmissionResponse {
  reconciliationId: string;
  status: ReconciliationStatus;
  statusUrl: string;
}

export interface ReconciliationStatusResponse {
  reconciliationId: string;
  status: ReconciliationStatus;
  stage: string;
  errorMessage?: string;
  resultAvailable: boolean;
  downloadFileName?: string;
}
