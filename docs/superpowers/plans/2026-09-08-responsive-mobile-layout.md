# Responsive Mobile Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing React/Vite reconciliation workflow usable on narrow phones and tablets without changing its API or reconciliation behavior.

**Architecture:** Keep `App` as the state owner and preserve the existing feature component boundaries. Add a small global responsive/accessibility foundation, then tune each existing layout at the component boundary: shell, upload, results/table, and unmatched-image preview. Use source-contract tests consistent with the repository’s current Node test style, then verify with the full test, lint, build, and browser-width checks.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind CSS v4, Lucide React, Node’s built-in test runner, Oxlint.

**Spec:** `docs/superpowers/specs/2026-09-08-responsive-mobile-design.md`

## Global Constraints

- Preserve the current dark visual identity, Khmer-only copy, API contracts, file validation, polling, matching, filtering, downloads, and modal behavior.
- Prevent viewport-wide horizontal overflow at phone widths; only the spreadsheet data region may scroll horizontally.
- Use a 44px minimum interactive hit area for mobile controls where the visual control is actionable.
- Respect `prefers-reduced-motion` for existing transitions, fade-ins, and spinner motion.
- Keep drag-and-drop behavior for pointer devices; mobile users continue to use native file pickers.
- Do not add dependencies or change backend code.

---

### Task 1: Add responsive shell and accessibility foundation

**Files:**
- Create: `frontend/tests/responsiveLayoutContracts.test.ts`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/app/components/AppHeader.tsx`
- Modify: `frontend/src/styles/index.css`

**Interfaces:**
- Consumes: the existing `AppHeader` props and `App` layout.
- Produces: a fluid, centered content frame; phone-safe page overflow behavior; visible focus styles; reduced-motion rules used by all later components.

- [ ] **Step 1: Write the failing source-contract tests**

Add `frontend/tests/responsiveLayoutContracts.test.ts`:

```ts
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const readSource = (path: string) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8');

test('App uses a centered mobile-safe content frame', () => {
  const source = readSource('../src/app/App.tsx');

  assert.match(source, /overflow-x-hidden/);
  assert.match(source, /max-w-7xl/);
  assert.match(source, /mx-auto/);
  assert.match(source, /safe-area-inset-bottom/);
});

test('header keeps its brand and action usable at phone widths', () => {
  const source = readSource('../src/app/components/AppHeader.tsx');

  assert.match(source, /px-4 sm:px-6/);
  assert.match(source, /text-base sm:text-xl/);
  assert.match(source, /min-h-11/);
});

test('global styles provide focus visibility and reduced-motion support', () => {
  const source = readSource('../src/styles/index.css');

  assert.match(source, /:focus-visible/);
  assert.match(source, /prefers-reduced-motion: reduce/);
});
```

- [ ] **Step 2: Run the focused test to verify it fails for missing responsive contracts**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: FAIL because the current shell, header, and global stylesheet do not yet contain the new responsive/accessibility contracts.

- [ ] **Step 3: Implement the shell changes**

In `App.tsx`, make the root page `overflow-x-hidden`, wrap the existing main content in a responsive centered frame, use phone-safe horizontal padding, and add bottom padding that includes `env(safe-area-inset-bottom)`:

```tsx
<main className="flex-1 w-full overflow-x-hidden px-4 pb-[calc(1.5rem+env(safe-area-inset-bottom))] pt-5 sm:px-6 sm:py-6">
  <div className="mx-auto w-full max-w-7xl space-y-8">
    {/* existing error, upload, and results content */}
  </div>
</main>
```

Keep the existing conditional upload/results tree inside the wrapper; do not alter handlers or API calls.

In `AppHeader.tsx`, use `px-4 sm:px-6`, reduce the title to `text-base sm:text-xl`, use a compact icon on phones only when the new-scan action is present, and give the new-scan button `min-h-11` with `shrink-0`. Preserve its accessible label and `onReset` behavior.

- [ ] **Step 4: Add global focus and reduced-motion rules**

Append to `frontend/src/styles/index.css`:

```css
button:focus-visible,
input:focus-visible,
[tabindex]:focus-visible {
  outline: 2px solid #A0E3E2;
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 5: Run the focused test to verify it passes**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: PASS with 3 tests and 0 failures.

- [ ] **Step 6: Commit the shell slice**

```bash
git add frontend/tests/responsiveLayoutContracts.test.ts frontend/src/app/App.tsx frontend/src/app/components/AppHeader.tsx frontend/src/styles/index.css
git commit -m "feat: make app shell mobile safe"
```

### Task 2: Make the upload workflow touch-first

**Files:**
- Modify: `frontend/tests/responsiveLayoutContracts.test.ts`
- Modify: `frontend/src/features/reconciliation/components/FileUploadZone.tsx`

**Interfaces:**
- Consumes: the existing file state callbacks, validation, preview URLs, and reconciliation start handler.
- Produces: stacked phone upload cards, wrapped metadata, a contained thumbnail area, and full-width touch-friendly start action.

- [ ] **Step 1: Extend the failing source-contract tests**

Append to `frontend/tests/responsiveLayoutContracts.test.ts`:

```ts
test('upload workflow stacks cards and exposes touch-sized actions', () => {
  const source = readSource('../src/features/reconciliation/components/FileUploadZone.tsx');

  assert.match(source, /grid-cols-1 md:grid-cols-2/);
  assert.match(source, /min-h-\[220px\] sm:min-h-\[250px\]/);
  assert.match(source, /grid-cols-3 sm:grid-cols-6/);
  assert.match(source, /min-h-11/);
  assert.match(source, /sm:flex-row/);
});
```

- [ ] **Step 2: Run the focused test to verify the upload contracts fail**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: FAIL on the upload test because the current component lacks the narrow-screen sizing and touch-target contracts.

- [ ] **Step 3: Implement mobile upload layout changes**

In `FileUploadZone.tsx`:

- Change both upload cards to `min-h-[220px] sm:min-h-[250px]`, `p-4 sm:p-5`, and add `min-w-0` so long Khmer/file content can wrap.
- Add `flex-wrap` and `gap-2` to the card heading/status rows; allow badges to wrap instead of forcing a single line.
- Change the populated thumbnail grid to `grid-cols-3 sm:grid-cols-6 md:grid-cols-5 lg:grid-cols-7 xl:grid-cols-8`, with `max-h-52 sm:max-h-40` and `min-w-0` on the wrapper.
- Give remove, clear-all, add-more, and start controls `min-h-11` and keep their existing labels/ARIA names.
- Change the summary/action footer to `items-stretch sm:flex-row sm:items-center`; make the start button `min-h-11 sm:w-auto` while keeping `w-full` at phone widths.
- Keep all file event handlers, validation, preview URL cleanup, and processing state unchanged.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: PASS with 4 tests and 0 failures.

- [ ] **Step 5: Commit the upload slice**

```bash
git add frontend/tests/responsiveLayoutContracts.test.ts frontend/src/features/reconciliation/components/FileUploadZone.tsx
git commit -m "feat: optimize upload workflow for touch screens"
```

### Task 3: Make statistics and spreadsheet preview responsive

**Files:**
- Modify: `frontend/tests/responsiveLayoutContracts.test.ts`
- Modify: `frontend/src/features/reconciliation/components/ReconciliationStats.tsx`
- Modify: `frontend/src/features/reconciliation/components/ExcelPreviewTable.tsx`

**Interfaces:**
- Consumes: the existing `ReconciliationResponse`, download action, filter state, and preview row data.
- Produces: readable phone metric cards and a spreadsheet panel whose dense data scrolls inside the panel only.

- [ ] **Step 1: Extend the failing source-contract tests**

Append to `frontend/tests/responsiveLayoutContracts.test.ts`:

```ts
test('results metrics preserve a two-column phone layout', () => {
  const source = readSource('../src/features/reconciliation/components/ReconciliationStats.tsx');

  assert.match(source, /grid-cols-2 lg:grid-cols-4/);
  assert.match(source, /min-w-0/);
  assert.match(source, /break-words/);
});

test('spreadsheet controls wrap and table overflow stays inside its panel', () => {
  const source = readSource('../src/features/reconciliation/components/ExcelPreviewTable.tsx');

  assert.match(source, /p-4 sm:p-5/);
  assert.match(source, /flex-col sm:flex-row/);
  assert.match(source, /min-h-11/);
  assert.match(source, /min-w-0 overflow-x-auto/);
  assert.match(source, /min-w-\[640px\]/);
});
```

- [ ] **Step 2: Run the focused test to verify the results contracts fail**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: FAIL because the stats cards do not yet explicitly constrain long content and the spreadsheet panel lacks the new phone spacing/scroll contract.

- [ ] **Step 3: Implement responsive statistics**

In `ReconciliationStats.tsx`:

- Add `min-w-0` to the root and completion banner wrappers.
- Use `p-4 sm:p-5` on the banner and metric cards.
- Add `min-w-0 break-words` to metric labels and supporting text so Khmer labels and sheet names wrap without widening the grid.
- Keep `grid-cols-2 lg:grid-cols-4`, values, colors, and match-rate calculation unchanged.

- [ ] **Step 4: Implement responsive spreadsheet preview**

In `ExcelPreviewTable.tsx`:

- Use `p-4 sm:p-5 min-w-0` on the panel.
- Make the header controls `flex-col sm:flex-row`, set the action group to `w-full sm:w-auto`, and give filter/download buttons `min-h-11`; on phones each action may use `flex-1` so it remains easy to tap.
- Add `min-w-0` to the header text wrapper and allow the badges/title to wrap.
- Keep the existing outer `min-w-0 overflow-x-auto` wrapper and give the table `min-w-[640px]` so dense columns remain legible while the wrapper, not the page, scrolls.
- Keep the filter state, download guard, error rendering, row mapping, and backend cell text unchanged.

- [ ] **Step 5: Run the focused test to verify it passes**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: PASS with 6 tests and 0 failures.

- [ ] **Step 6: Commit the results slice**

```bash
git add frontend/tests/responsiveLayoutContracts.test.ts frontend/src/features/reconciliation/components/ReconciliationStats.tsx frontend/src/features/reconciliation/components/ExcelPreviewTable.tsx
git commit -m "feat: make reconciliation results responsive"
```

### Task 4: Make unmatched cards and image preview phone-safe

**Files:**
- Modify: `frontend/tests/responsiveLayoutContracts.test.ts`
- Modify: `frontend/src/features/reconciliation/components/ImageScanGrid.tsx`

**Interfaces:**
- Consumes: the existing unmatched filtering, local file lookup, portal preview, download action, and search state.
- Produces: one-column phone cards, stacked controls, keyboard-visible card focus, and a viewport-contained preview dialog.

- [ ] **Step 1: Extend the failing source-contract tests**

Append to `frontend/tests/responsiveLayoutContracts.test.ts`:

```ts
test('unmatched results use a phone list and stretch controls', () => {
  const source = readSource('../src/features/reconciliation/components/ImageScanGrid.tsx');

  assert.match(source, /grid-cols-1 sm:grid-cols-2/);
  assert.match(source, /flex-col sm:flex-row/);
  assert.match(source, /min-h-11/);
  assert.match(source, /focus-visible:outline/);
});

test('image preview fits a phone viewport and wraps its footer', () => {
  const source = readSource('../src/features/reconciliation/components/ImageScanGrid.tsx');

  assert.match(source, /max-w-\[calc\(100vw-2rem\)\]/);
  assert.match(source, /max-h-\[calc\(100dvh-9rem\)\]/);
  assert.match(source, /flex-wrap/);
  assert.match(source, /sm:max-h-\[90vh\]/);
});
```

- [ ] **Step 2: Run the focused test to verify the unmatched contracts fail**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: FAIL because the current cards, controls, and modal do not yet contain the new phone-safe sizing/focus contracts.

- [ ] **Step 3: Implement responsive unmatched cards and controls**

In `ImageScanGrid.tsx`:

- Use `p-4 sm:p-5 min-w-0` on the panel.
- Make the header controls `flex-col sm:flex-row`; on phones set the action group and search input to `w-full`, and give the download button/input `min-h-11`.
- Keep the grid one column on phones and the existing denser breakpoints from `sm` upward with the existing `max-h-[30rem]` scroll container.
- Add `min-h-11`, `focus-visible:outline-2`, and `focus-visible:outline-[#A0E3E2]` to the clickable card surface, while preserving the current `onCardClick` callback. Keep the inner preview button labelled and touch-sized.
- Add `min-w-0` to barcode/filename wrappers so long values truncate within the card.

- [ ] **Step 4: Implement the phone-safe modal**

Update the portal modal classes without changing its state or `FileReader` fallback logic:

```tsx
<div className="fixed inset-0 z-[9999] overflow-y-auto bg-black/85 p-2 backdrop-blur-sm sm:flex sm:items-center sm:justify-center sm:p-4">
  <div className="mx-auto flex min-h-[calc(100dvh-1rem)] w-full max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-xl ... sm:min-h-0 sm:max-h-[90vh] sm:max-w-2xl">
    <div className="... flex-wrap ...">...</div>
    <div className="min-h-0 max-h-[calc(100dvh-9rem)] ... sm:max-h-[60vh]">...</div>
    <div className="flex flex-wrap items-center justify-between gap-3 ...">...</div>
  </div>
</div>
```

Retain the current image alt text, error fallback, barcode badge, close action, Escape listener, and `createPortal(document.body)` behavior.

- [ ] **Step 5: Run the focused test to verify it passes**

Run: `node --experimental-strip-types --test tests/responsiveLayoutContracts.test.ts`

Expected: PASS with 8 tests and 0 failures.

- [ ] **Step 6: Commit the unmatched-preview slice**

```bash
git add frontend/tests/responsiveLayoutContracts.test.ts frontend/src/features/reconciliation/components/ImageScanGrid.tsx
git commit -m "feat: make unmatched previews mobile friendly"
```

### Task 5: Run full verification and inspect real viewport behavior

**Files:**
- Modify: none unless verification exposes a defect; any fix follows the same test-first cycle and is committed separately.

**Interfaces:**
- Consumes: all responsive slices and existing frontend test coverage.
- Produces: evidence that mobile layout changes compile, preserve existing behavior, and do not introduce viewport overflow.

- [ ] **Step 1: Run the complete frontend test suite**

Run: `npm test`

Expected: all existing tests plus the 8 responsive layout contract tests pass with 0 failures.

- [ ] **Step 2: Run lint and production build**

Run: `npm run lint && npm run build`

Expected: Oxlint reports no errors and Vite produces a successful production bundle.

- [ ] **Step 3: Start the frontend for viewport inspection**

Run: `npm run dev -- --host 127.0.0.1`

Open the Vite URL in a browser and inspect at 320px, 375px, 430px, 768px, and 1280px viewport widths. Use representative states: empty upload, selected uploads with thumbnails, results with a wide table, unmatched cards, and the image preview modal.

- [ ] **Step 4: Verify the responsive acceptance checklist**

Confirm each item from the spec:

- The page does not scroll horizontally at phone widths.
- Upload cards stack and file actions remain tappable.
- The start action is full width and does not overlap the safe-area bottom padding.
- Khmer headings, status badges, filenames, and sheet names wrap or truncate within their panels.
- Metrics stay in a readable 2 × 2 phone grid.
- Spreadsheet overflow is contained inside the preview panel.
- Unmatched cards use one column on phones and remain keyboard focusable.
- The preview modal fits inside the phone viewport, keeps its image scrollable, and wraps its footer.
- Focus outlines and reduced-motion behavior are present.

- [ ] **Step 5: Review the final diff and working tree**

Run: `git diff --check && git status --short && git log -5 --oneline`

Expected: no whitespace errors; only the intended responsive files and commits are present, while the pre-existing untracked sample/test-resource files remain untouched.
