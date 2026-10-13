import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const readSource = (relativePath) => readFileSync(new URL(relativePath, import.meta.url), 'utf8');

describe('CloudNext file type icon surface', () => {
  it('uses the shared resolver and no question-mark fallback', () => {
    const source = readSource('../components/drive/FileTypeIcon.vue');
    expect(source).toContain('resolveFileVisualKind');
    expect(source).not.toContain("return '?'");
    expect(source).not.toContain('显示 ?');
  });

  it('mobile file cards reuse FileTypeIcon instead of a separate icon map', () => {
    const source = readSource('../components/mobile/MobileFileCard.vue');
    expect(source).toContain('FileTypeIcon');
    expect(source).not.toContain('FILE_TYPE_ICONS');
    expect(source).not.toContain('m-file-icon-');
  });
});
