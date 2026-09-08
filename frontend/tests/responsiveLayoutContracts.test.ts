import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const readSource = (path: string) =>
  readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8');

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
