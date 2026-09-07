import type {
  ReconciliationConfig,
  ReconciliationResponse,
  ReconciliationStatusResponse,
  ReconciliationSubmissionResponse,
} from '@/features/reconciliation/model/types';
import {
  reconciliationDownloadPath,
  reconciliationResultPath,
  reconciliationStatusPath,
} from './reconciliationApiPaths.ts';

export const API_BASE_URL = import.meta.env?.VITE_API_URL || '';

export async function submitReconciliation(
  excelFile: File,
  imageFiles: File[],
  config?: Partial<ReconciliationConfig>,
  onProgress?: (percent: number) => void
): Promise<ReconciliationSubmissionResponse> {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append('excelFile', excelFile);

    for (let i = 0; i < imageFiles.length; i++) {
      formData.append('images', imageFiles[i]);
    }

    if (config?.columnName) {
        formData.append('columnName', config.columnName);
    }
    if (config?.highlightFullRow !== undefined) {
      formData.append('highlightFullRow', String(config.highlightFullRow));
    }
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE_URL}/api/v1/barcodes/reconcile`);

    if (onProgress && xhr.upload) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100);
          onProgress(percent);
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response: ReconciliationSubmissionResponse = JSON.parse(xhr.responseText);
          resolve(response);
        } catch {
          reject(new Error('Invalid response format from server'));
        }
      } else {
        try {
          const errData = JSON.parse(xhr.responseText);
          reject(new Error(errData.error || `Server error: ${xhr.statusText}`));
        } catch {
          reject(new Error(`Server error: ${xhr.statusText} (${xhr.status})`));
        }
      }
    };

    xhr.onerror = () => {
      reject(new Error('Network error occurred while communicating with backend service'));
    };

    xhr.send(formData);
  });
}

export async function getReconciliationStatus(
  reconciliationId: string
): Promise<ReconciliationStatusResponse> {
  return getJson<ReconciliationStatusResponse>(reconciliationStatusPath(reconciliationId));
}

export async function getReconciliationResult(
  reconciliationId: string
): Promise<ReconciliationResponse> {
  return getJson<ReconciliationResponse>(reconciliationResultPath(reconciliationId));
}

export async function waitForReconciliation(
  reconciliationId: string,
  options: { intervalMs?: number; timeoutMs?: number } = {}
): Promise<ReconciliationStatusResponse> {
  const intervalMs = options.intervalMs ?? 1000;
  const timeoutMs = options.timeoutMs ?? 30 * 60 * 1000;
  const deadline = Date.now() + timeoutMs;

  while (Date.now() <= deadline) {
    const status = await getReconciliationStatus(reconciliationId);
    if (status.status === 'COMPLETED') {
      return status;
    }
    if (status.status === 'FAILED') {
      throw new Error(status.errorMessage || 'Barcode reconciliation failed');
    }

    const remainingMs = deadline - Date.now();
    if (remainingMs <= 0) break;
    await delay(Math.min(intervalMs, remainingMs));
  }

  throw new Error('Barcode reconciliation timed out');
}

export async function downloadReconciliation(
  reconciliationId: string,
  filename: string
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}${reconciliationDownloadPath(reconciliationId)}`
  );
  if (!response.ok) {
    throw new Error(await getErrorMessage(response, `Download failed (${response.status})`));
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || 'reconciliation_highlighted.xlsx';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);
  if (!response.ok) {
    throw new Error(await getErrorMessage(response, `Server error (${response.status})`));
  }
  return response.json() as Promise<T>;
}

async function getErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as { error?: string };
    return body.error || fallback;
  } catch {
    return fallback;
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => globalThis.setTimeout(resolve, milliseconds));
}

export function downloadBase64Excel(base64Data: string, filename: string) {
  const binaryString = window.atob(base64Data);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  const blob = new Blob([bytes], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || 'highlighted.xlsx';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
