export type ExcelUploadKind = 'spreadsheet' | 'table-image' | 'unsupported';

export interface ExcelUploadValidation {
  accepted: boolean;
  kind: ExcelUploadKind;
  errorKey?: 'errors.unsupported';
}

const spreadsheetPattern = /\.(xlsx|xls|csv)$/i;
const tableImagePattern = /\.(png|jpg|jpeg|webp)$/i;

export function isImageFile(file: File): boolean {
  return file.type.startsWith('image/') || tableImagePattern.test(file.name);
}

export function validateExcelUpload(file: File): ExcelUploadValidation {
  if (isImageFile(file)) {
    return { accepted: true, kind: 'table-image' };
  }

  if (spreadsheetPattern.test(file.name)) {
    return { accepted: true, kind: 'spreadsheet' };
  }

  return { accepted: false, kind: 'unsupported', errorKey: 'errors.unsupported' };
}
