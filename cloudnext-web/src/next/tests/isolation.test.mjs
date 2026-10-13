import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {parse} from '@vue/compiler-sfc';
import {parse as parseJS, parseExpression} from '@babel/parser';
import postcss from 'postcss';
const root=fileURLToPath(new URL('../',import.meta.url)),src=path.resolve(root,'..');
const read=p=>fs.readFileSync(p,'utf8');
const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(d,e.name)):[path.join(d,e.name)]);
const codeFiles=walk(src).filter(f=>/\.(js|vue)$/.test(f)&&!f.includes('__tests__')&&!f.endsWith('.test.js'));
test('scoped desktop stylesheet preserves demo declarations except the requested preview inset removal',()=>{
 const reference=read(path.join(root,'reference/demo-v3.1.html')).match(/<style>([\s\S]*?)<\/style>/)[1];
 const baseline=postcss.parse(reference);
 let removed=0;
 baseline.walkRules('.preview-body',rule=>rule.walkDecls('padding',d=>{if(d.value==='32px'){d.remove();removed++;}}));
 assert.equal(removed,1);
 const declarations=css=>{const values=[];postcss.parse(css).walkDecls(d=>values.push([d.prop,d.value,d.important||false]));return values;};
 assert.deepEqual(declarations(read(path.join(root,'styles/demo.css'))),declarations(baseline.toString()));
});
test('desktop and mobile CSS cannot leak into the other experience',()=>{
 for(const file of ['next/styles/demo.css','next/styles/experience.css','mobile/styles/legacy.css','mobile/styles/mobile.css']){
  const css=postcss.parse(read(path.join(src,file)));
  css.walkRules(rule=>{
   if(rule.parent.type==='atrule'&&/keyframes$/.test(rule.parent.name))return;
   assert.ok(rule.selector.includes('data-experience='),file+': '+rule.selector);
  });
 }
 assert.ok(!fs.existsSync(path.join(src,'style.css')));
 assert.ok(!fs.existsSync(path.join(src,'next/main.js')));
});
test('desktop imports no mobile view or old UI; all app relative imports resolve',()=>{
 const seen=new Set();
 function imports(file,desktop=false){
  if(desktop)assert.ok(!file.includes(path.sep+'mobile'+path.sep)&&!file.includes(path.sep+'components'+path.sep+'drive'+path.sep),file);
  const key=file+desktop;if(seen.has(key))return;seen.add(key);
  if(!/\.(vue|js)$/.test(file))return;
  const source=read(file);
  for(const m of source.matchAll(/(?:from\s*|import\s*\(\s*|import\s*)['"]([^'"]+)['"]/g)){
   if(!m[1].startsWith('.'))continue;
   const p=path.resolve(path.dirname(file),m[1]);
   const target=[p,p+'.js',p+'.vue',p+'.css',path.join(p,'index.js')].find(f=>fs.existsSync(f)&&fs.statSync(f).isFile());
   assert.ok(target,'missing import '+m[1]+' from '+file);imports(target,desktop);
  }
 }
 imports(path.join(root,'App.vue'),true);imports(path.join(root,'pages/LoginPage.vue'),true);
 for(const file of codeFiles)imports(file);
});
test('all Vue/JS sources parse and mobile templates contain no desktop branches or inert templates',()=>{
 for(const file of codeFiles){
  const source=read(file);
  if(!file.endsWith('.vue')){parseJS(source,{sourceType:'module'});continue;}
  const parsed=parse(source,{filename:file});assert.deepEqual(parsed.errors,[],file);
  const script=parsed.descriptor.scriptSetup||parsed.descriptor.script;
  if(script)parseJS(script.content,{sourceType:'module'});
  if(!parsed.descriptor.template)continue;
  function visit(node){
   if(file.includes(path.sep+'mobile'+path.sep)){
    assert.ok(!(node.type===1&&node.tag==='template'&&!node.props.length),'inert template: '+file);
    for(const p of node.props||[])assert.ok(!p.exp?.content.includes('isMobile'),'desktop branch: '+file);
   }
   for(const d of node.props||[]){
    const value=d.exp?.content;if(d.type!==7||!value||d.name==='slot')continue;
    try{
     if(d.name==='for'){const m=value.match(/\s+(?:in|of)\s+([\s\S]+)$/);if(m)parseExpression(m[1]);}
     else if(d.name==='on')parseJS(value,{sourceType:'module'});
     else parseExpression(value);
    }catch(e){throw new Error(file+': '+value+' — '+e.message);}
   }
   for(const child of node.children||[])visit(child);
  }
  visit(parsed.descriptor.template.ast);
 }
});
test('one Vue application, history router and no desktop switch menu',()=>{
 assert.equal(read(path.join(src,'main.js')).match(/createApp\(/g).length,1);
 const appCode=codeFiles.map(read).join('\n');
 assert.ok(!appCode.includes('createWebHashHistory'));
 assert.ok(!appCode.includes('switchLegacy'));
 assert.ok(!appCode.includes('返回原版'));
 assert.ok(!appCode.includes('切换新版'));
 assert.ok(!/window\.location\.(assign|replace)/.test(read(path.join(src,'authNavigation.js'))));
 for(const entry of ['index.html','next.html'])assert.ok(read(path.join(src,'..',entry)).includes('src="/src/main.js"'));
});
test('desktop business code contains no native prompt or mock dataset',()=>{
 for(const file of walk(root).filter(f=>/\.(js|vue)$/.test(f))){
  assert.ok(!/window\.(alert|confirm|prompt)\s*\(/.test(read(file)),file);
  assert.ok(!/makeData\(|dieyun-ui-visual|下载样例|演示外链已生成/.test(read(file)),file);
 }
});
