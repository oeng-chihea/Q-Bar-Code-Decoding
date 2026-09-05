# Excel Barcode Reconciler Frontend

React 19 + TypeScript + Vite frontend for reconciling barcode/QR photos against Excel workbooks or images of Excel tables.

The first upload accepts `.xlsx`, `.xls`, `.csv`, `.png`, `.jpg`, `.jpeg`, and `.webp`. Table images are converted by the backend OCR pipeline before reconciliation.

## Development

```bash
npm install
npm run dev
```

The Vite dev server runs at `http://localhost:5173` and proxies `/api` requests to the backend at `http://localhost:8080`.

## Source structure

```text
src/
├── app/
│   ├── App.tsx
│   └── components/AppHeader.tsx
├── features/reconciliation/
│   ├── api/reconciliationApi.ts
│   ├── components/
│   └── model/
├── shared/i18n/i18n.ts
├── styles/index.css
└── main.tsx
```

- `app/` composes the application and owns app-shell UI.
- `features/reconciliation/` contains reconciliation components, API calls, validation, and data contracts.
- `shared/i18n/` contains the Khmer-only language provider and translation utilities.
- `styles/` contains global Tailwind and base styles.

## Verification

```bash
node --experimental-strip-types --test tests/fileValidation.test.ts tests/i18n.test.ts
npm run lint
npm run build
```
