import { describe, expect, it } from 'vitest';
import {
  FILE_VISUAL_KINDS,
  resolveFileVisualKind
} from '../utils/fileTypes';

describe('resolveFileVisualKind', () => {
  it.each([
    [{ fileType: 'file', name: '-1-copy.png' }, 'image'],
    [{ fileType: 'file', name: 'thesis.doc' }, 'word'],
    [{ fileType: 'file', name: 'O2PLATFORM-6.4.34.apk' }, 'app'],
    [{ name: 'config.toml' }, 'text'],
    [{ name: 'main.java' }, 'code'],
    [{ name: 'music.mp3' }, 'audio'],
    [{ name: 'archive.7z' }, 'archive'],
    [{ name: 'drawing.dwg' }, 'cad'],
    [{ name: 'font.woff2' }, 'font'],
    [{ name: 'server.crt' }, 'certificate'],
    [{ name: 'unknown-without-extension' }, 'file']
  ])('resolves %o to %s', (input, expected) => {
    expect(resolveFileVisualKind(input)).toBe(expected);
  });

  it('uses content type when the filename has no usable extension', () => {
    expect(resolveFileVisualKind({ fileType: 'file', contentType: 'audio/mpeg' })).toBe('audio');
    expect(resolveFileVisualKind({ fileType: 'file', contentType: 'application/pdf' })).toBe('pdf');
  });

  it('falls back to backend fileType after filename and content type checks', () => {
    expect(resolveFileVisualKind({ fileType: 'code' })).toBe('code');
    expect(resolveFileVisualKind({ fileType: 'unknown' })).toBe('file');
  });

  it('only returns supported visual kinds', () => {
    const supported = new Set(FILE_VISUAL_KINDS);
    const samples = ['report.docx', 'budget.xlsx', 'deck.pptx', 'photo.heic', 'README'];
    for (const name of samples) {
      expect(supported.has(resolveFileVisualKind({ name }))).toBe(true);
    }
  });
});
