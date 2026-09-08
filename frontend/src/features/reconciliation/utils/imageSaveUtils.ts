/**
 * Utilities for saving and sharing images cross-platform.
 * On mobile devices (such as iPhone iOS Safari and Android), uses the Web Share API
 * with File objects so users can tap native "Save Image" to save directly to the
 * device Photos / Camera Roll / Gallery.
 * On desktop or unsupported browsers, seamlessly falls back to standard file download.
 */

export function inferImageMimeType(filename: string, existingType?: string): string {
  if (existingType && existingType.startsWith('image/') && existingType !== 'image/octet-stream') {
    return existingType;
  }

  const lower = (filename || '').toLowerCase();
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.webp')) return 'image/webp';
  if (lower.endsWith('.gif')) return 'image/gif';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  if (lower.endsWith('.svg')) return 'image/svg+xml';

  return 'image/jpeg';
}

export function sanitizeImageFilename(candidateName?: string, fallbackIndex = 1): string {
  let name = (candidateName || '').trim();
  if (!name || name === '.' || name === '..') {
    name = `unmatched-image-${fallbackIndex}.jpg`;
  }

  // Remove directory separators
  const separatorIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
  if (separatorIndex >= 0) {
    name = name.substring(separatorIndex + 1);
  }

  // Replace spaces and special characters
  name = name.replaceAll(/[^A-Za-z0-9._-]/g, '_');

  // Replace .bin extension with .jpg for iOS gallery compatibility
  if (name.toLowerCase().endsWith('.bin')) {
    name = `${name.slice(0, -4)}.jpg`;
  }

  // Ensure image extension
  const lower = name.toLowerCase();
  if (
    !lower.endsWith('.jpg') &&
    !lower.endsWith('.jpeg') &&
    !lower.endsWith('.png') &&
    !lower.endsWith('.webp') &&
    !lower.endsWith('.gif')
  ) {
    name = `${name}.jpg`;
  }

  return name;
}

export function normalizeImageFile(fileOrBlob: Blob | File, filename?: string, fallbackIndex = 1): File {
  const safeFilename = sanitizeImageFilename(
    filename || (fileOrBlob instanceof File ? fileOrBlob.name : undefined),
    fallbackIndex,
  );
  const mimeType = inferImageMimeType(safeFilename, fileOrBlob.type);

  if (fileOrBlob instanceof File && fileOrBlob.name === safeFilename && fileOrBlob.type === mimeType) {
    return fileOrBlob;
  }

  return new File([fileOrBlob], safeFilename, { type: mimeType });
}

export function canShareImages(files: File[]): boolean {
  if (typeof navigator === 'undefined' || typeof navigator.canShare !== 'function' || typeof navigator.share !== 'function') {
    return false;
  }
  if (!Array.isArray(files) || files.length === 0) {
    return false;
  }
  // Web Share for gallery saving is specifically for mobile devices (iPhone, iPad, Android).
  // Desktop Mac/PC browsers prefer standard direct file download to the Downloads folder.
  if (!isMobileDevice()) {
    return false;
  }
  try {
    return navigator.canShare({ files });
  } catch {
    return false;
  }
}

export function isMobileDevice(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  const isTouchScreen = typeof navigator.maxTouchPoints === 'number' && navigator.maxTouchPoints > 1;
  return /iPhone|iPad|iPod|Android/i.test(ua) || (isTouchScreen && /Macintosh/i.test(ua));
}

export function triggerBrowserDownload(blob: Blob, filename: string): void {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  const browserUrl = window.URL;
  const url = browserUrl.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  globalThis.setTimeout(() => {
    try {
      browserUrl.revokeObjectURL(url);
    } catch {
      // ignore revocation error
    }
  }, 1000);
}

export interface SaveImageResult {
  shared: boolean;
  downloaded: boolean;
  canceled: boolean;
}

/**
 * Saves a single image. On mobile devices where Web Share is supported, opens
 * the native share sheet with the File object so users can choose "Save Image" to Gallery.
 * On desktop or if share is unsupported, triggers a browser file download.
 */
export async function saveOrShareImage(
  fileOrBlob: Blob | File,
  filename?: string,
  fallbackIndex = 1,
): Promise<SaveImageResult> {
  const file = normalizeImageFile(fileOrBlob, filename, fallbackIndex);

  if (canShareImages([file])) {
    try {
      await navigator.share({
        files: [file],
        title: file.name,
      });
      return { shared: true, downloaded: false, canceled: false };
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        // User closed or dismissed the share sheet without picking an action
        return { shared: false, downloaded: false, canceled: true };
      }
      // If sharing failed due to permission or other error, fallback to download
      triggerBrowserDownload(file, file.name);
      return { shared: false, downloaded: true, canceled: false };
    }
  }

  triggerBrowserDownload(file, file.name);
  return { shared: false, downloaded: true, canceled: false };
}

/**
 * Saves or shares a batch of images. If Web Share supports sharing the collection of files,
 * the native share sheet opens offering "Save [N] Images" to Photos.
 * Otherwise, invokes the fallback downloader (e.g. downloading a single ZIP archive).
 */
export async function saveOrShareBatch(
  files: (Blob | File)[],
  fallbackDownload: () => Promise<void>,
): Promise<SaveImageResult> {
  const normalizedFiles = files.map((f, idx) => normalizeImageFile(f, undefined, idx + 1));

  if (canShareImages(normalizedFiles)) {
    try {
      await navigator.share({
        files: normalizedFiles,
        title: 'Unmatched Images',
      });
      return { shared: true, downloaded: false, canceled: false };
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        return { shared: false, downloaded: false, canceled: true };
      }
      await fallbackDownload();
      return { shared: false, downloaded: true, canceled: false };
    }
  }

  await fallbackDownload();
  return { shared: false, downloaded: true, canceled: false };
}
