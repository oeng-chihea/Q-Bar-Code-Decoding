const RECONCILIATION_BASE_PATH = '/api/v1/barcode-reconciliations';

export function reconciliationStatusPath(reconciliationId: string): string {
  return `${RECONCILIATION_BASE_PATH}/${encodeURIComponent(reconciliationId)}`;
}

export function reconciliationResultPath(reconciliationId: string): string {
  return `${reconciliationStatusPath(reconciliationId)}/result`;
}

export function reconciliationDownloadPath(reconciliationId: string): string {
  return `${reconciliationStatusPath(reconciliationId)}/download`;
}
