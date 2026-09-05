import { createContext, createElement, useContext, useEffect } from 'react';
import type { ReactNode } from 'react';

export const DEFAULT_LANGUAGE = 'km' as const;
export type Language = typeof DEFAULT_LANGUAGE;

const khmer = {
  'common.dismiss': 'បិទ',
  'header.title': 'កម្មវិធីផ្ទៀងផ្ទាត់បាកូដ Excel',
  'header.subtitle': 'ផ្ទៀងផ្ទាត់ឯកសារ Excel ជាមួយរូបថតបាកូដ • បន្លិចលទ្ធផលដែលត្រូវគ្នាជាពណ៌ក្រហម',
  'header.newScan': 'ស្កេនថ្មី',
  'app.heroTitle': 'ផ្ទៀងផ្ទាត់បាកូដជាមួយ Excel',
  'app.heroDescription': 'បញ្ចូលឯកសារស្តុក Excel ឬរូបភាពតារាង Excel រួមជាមួយរូបថតបាកូដ/QR។ ប្រព័ន្ធនឹងអាន និងផ្ទៀងផ្ទាត់ទិន្នន័យ ហើយបន្លិចធាតុដែលត្រូវគ្នាជាពណ៌ក្រហម។',
  'upload.step1': 'ជំហាន 1: Excel / រូបភាពតារាង',
  'upload.step2': 'ជំហាន 2: រូបភាពបាកូដ / QR',
  'upload.spreadsheetReady': 'ឯកសារ/រូបភាពរួចរាល់',
  'upload.browse': 'ចុចជ្រើសរើស ឬអូសឯកសារ Excel ឬរូបភាពតារាងមកទីនេះ',
  'upload.supports': 'គាំទ្រ .xlsx, .xls, .csv, .png, .jpg, .jpeg និង .webp',
  'upload.tableHint': 'រូបភាពត្រូវតែជារូបថត/ស្គ្រីនសត់តារាង Excel ដែលមានជួរឈរបាកូដ ឬលេខកូដ',
  'upload.spreadsheet': 'សៀវភៅបញ្ជី',
  'upload.tableImage': 'រូបភាពតារាង Excel',
  'upload.remove': 'លុបឯកសារ',
  'upload.note': 'អនុញ្ញាតឯកសារ Excel ឬរូបភាពតារាង Excel។ សូមបញ្ចូលរូបថតបាកូដនៅជំហាន 2។',
  'upload.parallel': 'កម្មវិធីជាច្រើនដំណើរការស្កេនរូបភាពក្នុងពេលតែមួយ។',
  'upload.dropzone': 'ជ្រើសរើស ឬអូសរូបភាពបាកូដ/QR ច្រើនមកទីនេះ',
  'upload.batchCount': 'បញ្ចូលរូបថតជាក្រុម 40+ (.png, .jpg, .webp)',
  'upload.image': 'រូបភាព',
  'upload.images': 'រូបភាព',
  'upload.clearAll': 'លុបទាំងអស់',
  'upload.addMore': 'បន្ថែមរូបភាព',
  'upload.readySummary': 'រួចរាល់សម្រាប់ផ្ទៀងផ្ទាត់:',
  'upload.noExcel': 'គ្មាន Excel',
  'upload.spreadsheetSummary': 'សៀវភៅបញ្ជី Excel',
  'upload.barcodeImage': 'រូបភាពបាកូដ',
  'upload.barcodeImages': 'រូបភាពបាកូដ',
  'upload.start': 'ចាប់ផ្តើមផ្ទៀងផ្ទាត់ និងបន្លិច',
  'upload.processing': 'កំពុងដំណើរការ និងផ្ទៀងផ្ទាត់...',
  'upload.progress': 'ដំណើរការបញ្ចូល: {{percent}}%',
  'errors.unsupported': 'ទម្រង់ឯកសារមិនគាំទ្រ។ សូមបញ្ចូល Excel (.xlsx, .xls, .csv) ឬរូបភាពតារាង (.png, .jpg, .jpeg, .webp)។',
  'errors.barcode': 'មិនអាចប្រើរូបភាពបាកូដជាតារាង Excel បានទេ។ សូមបញ្ចូលរូបភាពតារាង Excel នៅជំហាន 1 និងរូបភាពបាកូដនៅជំហាន 2។',
  'errors.excelRequired': 'ត្រូវការ Excel (.xlsx, .xls, .csv) ឬរូបភាពតារាង Excel (.png, .jpg, .jpeg, .webp)។',
  'errors.invalidResponse': 'ទម្រង់ចម្លើយពីម៉ាស៊ីនមេមិនត្រឹមត្រូវ',
  'errors.network': 'មានបញ្ហាបណ្តាញពេលទំនាក់ទំនងជាមួយសេវាកម្មខាងក្រោយ',
  'errors.unexpected': 'មានបញ្ហាមិនរំពឹងទុកពេលផ្ទៀងផ្ទាត់',
  'results.completed': 'ការផ្ទៀងផ្ទាត់បានបញ្ចប់',
  'results.completedDescription': 'ធាតុដែលត្រូវគ្នាត្រូវបានរកឃើញ និងបន្លិចជាពណ៌ក្រហម។',
  'results.imagesUploaded': 'រូបភាពបានបញ្ចូល',
  'results.scannedProcessed': 'បានស្កេន និងដំណើរការ',
  'results.catalogRows': 'ជួរដេកក្នុងបញ្ជី',
  'results.sheet': 'សន្លឹក',
  'results.target': 'គោលដៅ',
  'results.highlightedRed': 'បានបន្លិចពណ៌ក្រហម',
  'results.matchRate': '{{rate}}% នៃបញ្ជីត្រូវគ្នា',
  'results.processingTime': 'ពេលដំណើរការ',
  'results.fast': 'ដំណើរការស្របគ្នាលឿន',
  'preview.title': 'មើលសៀវភៅបញ្ជី',
  'preview.sheet': 'សន្លឹក: {{name}}',
  'preview.target': 'គោលដៅ: {{name}}',
  'preview.confidence': 'ទំនុកចិត្ត {{rate}}%',
  'preview.showing': 'បង្ហាញ {{count}} ជួរ • {{matched}} ជួរត្រូវគ្នាត្រូវបានបន្លិចជាពណ៌ក្រហម',
  'preview.showMatched': 'បង្ហាញតែជួរដែលត្រូវគ្នា',
  'preview.showAll': 'បង្ហាញគ្រប់ជួរ',
  'preview.download': 'ទាញយក .xlsx',
  'preview.status': 'ស្ថានភាព',
  'preview.targetLabel': 'គោលដៅ',
  'preview.matchRed': 'ត្រូវគ្នា (ក្រហម)',
  'preview.noRows': 'គ្មានជួរដើម្បីបង្ហាញតាមតម្រងបច្ចុប្បន្ន។',
  'preview.previewing': 'កំពុងបង្ហាញ {{preview}} ជួរដំបូងក្នុងចំណោម {{total}} ជួរ។ ឯកសារដែលបានកែប្រែពេញលេញជាមួយ {{matched}} ជួរបន្លិចរួចរាល់សម្រាប់ទាញយក។',
  'matches.title': 'បាកូដដែលត្រូវគ្នា',
  'matches.subtitle': 'បង្ហាញលេខបាកូដពីរូបភាពដែលត្រូវគ្នានឹងធាតុក្នុងសៀវភៅបញ្ជី',
  'matches.search': 'ស្វែងរកបាកូដដែលត្រូវគ្នា...',
  'matches.none': 'រកមិនឃើញបាកូដដែលត្រូវគ្នានៅក្នុងសៀវភៅបញ្ជីដែលបានបញ្ចូល។',
  'matches.noSearch': 'គ្មានធាតុត្រូវនឹងការស្វែងរករបស់អ្នក។',
  'matches.image': 'រូបភាព #{{number}}',
  'matches.multiMatched': 'បាកូដ {{count}} ត្រូវគ្នា',
  'matches.matchedInRed': 'ត្រូវគ្នាជាពណ៌ក្រហម',
} as const;

export type TranslationKey = keyof typeof khmer;

export function translate(
  key: TranslationKey,
  values?: Record<string, string | number>,
): string {
  const template = khmer[key] ?? key;
  return template.replace(/\{\{(\w+)\}\}/g, (_, name: string) => String(values?.[name] ?? `{{${name}}}`));
}

export function localizeError(message: string): string {
  const normalized = message.trim();
  if (normalized.includes('Barcode images are not supported in the Excel section')) return translate('errors.barcode');
  if (normalized.includes('Only Excel spreadsheet files')) return translate('errors.unsupported');
  if (normalized.includes('Excel spreadsheet or table image file is required')) return translate('errors.excelRequired');
  if (normalized.includes('Unsupported file format')) return translate('errors.unsupported');
  if (normalized.includes('Invalid response format from server')) return translate('errors.invalidResponse');
  if (normalized.includes('Network error occurred')) return translate('errors.network');
  if (normalized.includes('unexpected error occurred during reconciliation')) return translate('errors.unexpected');
  return message;
}

interface TranslationContextValue {
  language: Language;
  t: (key: TranslationKey, values?: Record<string, string | number>) => string;
}

const TranslationContext = createContext<TranslationContextValue | null>(null);

export function LanguageProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    // Clear the old selector preference so a previous English choice cannot survive this update.
    window.localStorage.removeItem('reconciler_language');
    document.documentElement.lang = DEFAULT_LANGUAGE;
  }, []);

  const value: TranslationContextValue = {
    language: DEFAULT_LANGUAGE,
    t: translate,
  };

  return createElement(TranslationContext.Provider, { value }, children);
}

export function useTranslation(): TranslationContextValue {
  const context = useContext(TranslationContext);
  if (!context) throw new Error('useTranslation must be used inside LanguageProvider');
  return context;
}
