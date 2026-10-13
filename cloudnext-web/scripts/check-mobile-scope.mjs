import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Linter } from 'eslint';
import globals from 'globals';
import { parse } from '@vue/compiler-sfc';

const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const linter = new Linter();
const vueMacros = Object.fromEntries([
  'defineProps', 'defineEmits', 'defineExpose', 'defineOptions',
  'defineSlots', 'defineModel', 'withDefaults'
].map(name => [name, 'readonly']));

// Check lexical bindings in scripts, not just syntax or matching symbol names.
// This is read-only: no formatting, fixes, template compilation or browser usage.
export function checkSource(source, filename) {
  let scripts = [{ content: source, line: 1 }];
  if (filename.endsWith('.vue')) {
    const result = parse(source, { filename });
    if (result.errors.length) return result.errors.map(error => ({
      file: filename, line: error.loc?.start?.line || 1, column: 1,
      message: error.message || String(error)
    }));
    scripts = [result.descriptor.script, result.descriptor.scriptSetup].filter(Boolean)
      .map(script => ({ content: script.content, line: script.loc.start.line }));
  }
  return scripts.flatMap(script => linter.verify(script.content, {
    languageOptions: { ecmaVersion: 'latest', sourceType: 'module', globals: { ...globals.browser, ...vueMacros } },
    rules: { 'no-undef': ['error', { typeof: true }] }
  }, { filename: filename + '.js', allowInlineConfig: false })
    .filter(message => message.severity === 2)
    .map(message => ({
      file: filename, line: script.line + message.line - 1,
      column: message.column, message: message.message, ruleId: message.ruleId
    })));
}

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(file) : [file];
  });
}

export function checkMobileScope() {
  const files = ['src/mobile', 'src/components/mobile', 'src/composables', 'src/auth']
    .flatMap(directory => walk(path.join(projectRoot, directory)))
    .filter(file => /\.(vue|js)$/.test(file) && !file.endsWith('.test.js'));
  return {
    files: files.length,
    errors: files.flatMap(file => checkSource(fs.readFileSync(file, 'utf8'), path.relative(projectRoot, file)))
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = checkMobileScope();
  for (const error of result.errors) console.error(error.file + ':' + error.line + ':' + error.column + ' ' + error.message);
  console.log('Mobile/shared script scope: ' + result.files + ' files, ' + result.errors.length + ' errors.');
  process.exitCode = result.errors.length ? 1 : 0;
}
