export type ExcelUploadKind = 'table-image' | 'unsupported';

export interface ExcelUploadValidation {
  accepted: boolean;
  kind: ExcelUploadKind;
  errorKey?: 'errors.unsupported';
}

const tableImagePattern = /\.(png|jpg|jpeg|webp)$/i;

export function isImageFile(file: File): boolean {
  return file.type.startsWith('image/') || tableImagePattern.test(file.name);
}

export function validateExcelUpload(file: File): ExcelUploadValidation {
  if (isImageFile(file)) {
    return { accepted: true, kind: 'table-image' };
  }

  return { accepted: false, kind: 'unsupported', errorKey: 'errors.unsupported' };
}
