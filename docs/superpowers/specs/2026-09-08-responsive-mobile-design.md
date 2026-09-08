# Responsive Mobile Layout Design

## Goal

Make the Excel barcode reconciliation web app comfortable and reliable on touch devices from narrow phones through tablets, while preserving the current desktop workflow and dark visual identity. The update is a layout and interaction change only: upload, polling, reconciliation, preview, filtering, downloads, and image inspection keep their existing behavior and API contracts.

## Design direction

Use a responsive retrofit rather than a separate mobile application. The existing charcoal canvas, mint primary action, green spreadsheet state, and red match/unmatched signals remain the visual language. The memorable mobile improvement is a clear, thumb-friendly action path: choose the table image, add barcode photos, then start reconciliation from a full-width action area that remains easy to reach.

### Responsive layout map

```text
Phone (320–639px)
┌────────────────────────────┐
│ compact brand row   [new]  │  sticky header
├────────────────────────────┤
│ title + short explanation   │
│ [table image upload]        │
│ [barcode photos upload]     │
│ [summary]                   │
│ [START RECONCILIATION]      │  full width / touch target
├────────────────────────────┤
│ result status               │
│ 2 × 2 metrics               │
│ spreadsheet panel           │  contained horizontal scroll
│ unmatched image list        │
└────────────────────────────┘

Tablet/Desktop (640px+)
┌─────────────────────────────────────────────┐
│ brand row                              [new] │
├─────────────────────────────────────────────┤
│ title                                       │
│ [table image upload] [barcode photos upload]│
│ [summary]                         [start]   │
│ results: 4-column metrics / wide panels     │
└─────────────────────────────────────────────┘
```

The page container becomes fluid with a readable maximum width. At phone widths, horizontal page overflow is prohibited; only the spreadsheet data region can scroll horizontally because dense columns are inherently tabular. All actionable controls use a minimum 44px touch target, even when their visual label is compact.

## Component changes

### App shell and header

- Add a responsive max-width content frame so wide screens do not stretch every panel edge to edge.
- Reduce header padding and brand typography on phones while preserving the subtitle on tablets and larger screens.
- Let the new-scan action use a compact icon-plus-label treatment on phones without crowding the brand row.
- Add safe-area-aware bottom spacing where the sticky mobile action can overlap content.

### Upload workflow

- Keep the two upload cards stacked below the medium breakpoint and give each a comfortable minimum height without forcing excessive empty space on small phones.
- Make headings, status badges, and helper text wrap rather than squeeze or overflow.
- Increase file/remove/add controls to touch-friendly dimensions.
- Keep the image thumbnail grid dense enough for batches but use fewer columns on narrow screens and a taller contained scroll region.
- Make the summary and start action stack on phones; the start button is full width at the phone breakpoint.
- Respect `prefers-reduced-motion` for existing fade and spinner behavior.

### Results and statistics

- Use one column for the completion banner on phones and allow its copy to wrap.
- Keep two metric columns on phones, expanding to four on large screens; prevent long Khmer labels or sheet names from widening the page.
- Keep metric values visually prominent while allowing their supporting text to wrap.

### Spreadsheet preview

- Make the panel padding and header controls responsive.
- Stack the filter and download actions on narrow phones and let each occupy a usable width.
- Keep the table inside an explicit overflow container with a minimum content width so cells stay legible without creating viewport overflow.
- Ensure the no-results row and preview note remain readable at small widths.

### Unmatched image results and preview

- Use a one-column card list on phones, two columns on small tablets, and the existing denser grid on larger screens.
- Stack or stretch search and download controls on narrow screens.
- Preserve card click behavior and add visible keyboard focus styles.
- Make the preview modal use the full available viewport on phones, with a scrollable image region and a footer that wraps barcode details and the close action.
- Prevent body-level modal content from exceeding the viewport width or height.

## Interaction and accessibility

- Preserve keyboard operation for file inputs, filters, buttons, cards, and the image preview close action.
- Add `:focus-visible` styling to controls that currently rely only on hover.
- Use `type="button"` for non-submit buttons where needed.
- Keep icon-only controls labelled with accessible names.
- Add reduced-motion rules for `.animate-in`, spinner motion, and transitions where the existing utility classes are used.
- Do not remove drag-and-drop support on pointer devices; mobile users continue to use the native file picker.

## Data flow and error handling

No data flow changes are required. `App` continues to own reconciliation state, the upload component continues to own file selection and previews, and result components continue to call the existing download APIs. Responsive changes must not alter file validation, polling, matching, filtering, or download error states. Error banners and modal content must wrap within the viewport instead of forcing horizontal scrolling.

## Verification strategy

- Add focused source-level regression tests for responsive class contracts on the shell, upload controls, spreadsheet overflow wrapper, and modal sizing where the existing test style supports it.
- Run the existing frontend test suite, lint, and production build.
- Start the Vite app and inspect representative narrow phone, tablet, and desktop widths for page overflow, clipped controls, readable Khmer text, usable touch targets, spreadsheet containment, and modal behavior.
- Re-check the existing upload and results interaction paths after responsive styling changes.

## Scope boundaries

Included: responsive layout, spacing, sizing, wrapping, touch targets, contained overflow, modal sizing, focus and reduced-motion affordances.

Excluded: backend/API changes, authentication, new reconciliation features, new language support, camera scanning, native app packaging, and redesigning the desktop information architecture.
