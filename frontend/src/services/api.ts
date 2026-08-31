import type { ReconciliationConfig, ReconciliationResponse } from '../types';

export const API_BASE_URL = import.meta.env.VITE_API_URL || '';

export async function reconcileFiles(
  excelFile: File,
  imageFiles: File[],
  config?: Partial<ReconciliationConfig>,
  onProgress?: (percent: number) => void
): Promise<ReconciliationResponse> {
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
    if (config?.geminiApiKey) {
      formData.append('geminiApiKey', config.geminiApiKey);
    }

    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE_URL}/api/v1/barcodes/reconcile`);

    if (config?.geminiApiKey) {
      xhr.setRequestHeader('X-Gemini-API-Key', config.geminiApiKey);
    }

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
          const response: ReconciliationResponse = JSON.parse(xhr.responseText);
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
