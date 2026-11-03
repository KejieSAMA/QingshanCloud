import { it,expect } from 'vitest';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { lowerPdfIccRuntime } from '../../build/pdfjs-webview83';
import { pdfJsWasmAssetsPlugin,PDFJS_VERSION } from '../../build/pdfjs-wasm-assets';
const require=createRequire(import.meta.url);
it('keeps native implementations while installing PDF runtime compatibility',async()=>{
  const original=[String.prototype.replaceAll,Object.hasOwn,globalThis.structuredClone,Promise.withResolvers];
  await import('../preview/pdfRuntimePolyfills');
  const current=[String.prototype.replaceAll,Object.hasOwn,globalThis.structuredClone,Promise.withResolvers];
  original.forEach((native,index)=>{if(typeof native==='function')expect(current[index]).toBe(native);else expect(typeof current[index]).toBe('function');});
  const pending=Promise.withResolvers();pending.resolve(7);expect(await pending.promise).toBe(7);
});
it('uses correct replacement and clone semantics when old WebViews lack native methods',()=>{
  const output=execFileSync(process.execPath,['-e',`
    const assert=require('node:assert/strict');
    delete String.prototype.replaceAll; delete Object.hasOwn; delete globalThis.structuredClone;
    const replace=require('core-js-pure/actual/string/replace-all'), own=require('core-js-pure/actual/object/has-own'), clone=require('core-js-pure/actual/structured-clone');
    assert.equal(replace('a.a','.','$&!'),'a.!a');assert.throws(()=>replace('aa',/a/,'b'),TypeError);
    const original={map:new Map([['a',1]])};original.self=original;const result=clone(original);
    assert.equal(result.self,result);assert.notEqual(result.map,original.map);assert.equal(own({x:1},'x'),true);console.log('passed');
  `],{encoding:'utf8'});expect(output.trim()).toBe('passed');
});
it('guards ICC finalization in the actual locked PDF.js worker',()=>{
  expect(PDFJS_VERSION).toBe('5.4.296');const source=readFileSync(require.resolve('pdfjs-dist/legacy/build/pdf.worker.mjs'),'utf8');
  const lowered=lowerPdfIccRuntime(source);expect(lowered).toContain('this.#useWasm && typeof FinalizationRegistry === "function"');
  expect(()=>lowerPdfIccRuntime(source.replace('if (this.#useWasm) {','if (false) {'))).toThrow();
});
it('registers local CJK maps and image decoders without a public CDN',()=>{
  const files=[];const plugin=pdfJsWasmAssetsPlugin({scriptTarget:'chrome83'});plugin.generateBundle.call({emitFile:file=>files.push(file)});
  expect(files.some(f=>f.fileName.endsWith('/UniGB-UCS2-H.bcmap'))).toBe(true);
  expect(files.some(f=>f.fileName.endsWith('/openjpeg.wasm'))).toBe(true);
  expect(files.every(f=>f.fileName.startsWith('vendor/pdfjs/5.4.296/'))).toBe(true);
});
