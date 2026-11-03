import { beforeEach,afterEach,it,expect,vi } from 'vitest';
import { createSSRApp,h } from 'vue';
import { renderToString } from 'vue/server-renderer';
import PanZoomSurface from '../preview/PanZoomSurface.vue';
import fs from 'node:fs';
let api;
beforeEach(async()=>{
  vi.resetModules();
  vi.stubGlobal('window',{location:{search:'',origin:'https://drive.test'},dispatchEvent:vi.fn(),localStorage:{getItem:()=>null,setItem:vi.fn(),removeItem:vi.fn()},open:vi.fn()});
  vi.stubGlobal('document',{documentElement:{dataset:{}},body:{dataset:{},classList:{toggle:vi.fn()}}});
  vi.stubGlobal('fetch',vi.fn());api=await import('../api/cloudnext.js');
});
afterEach(()=>vi.unstubAllGlobals());
it('opens a local reader without a popup, download fallback or edit ticket',async()=>{
  const {ui}=await import('../next/state.js');await api.previewDriveItem({id:'one',name:'报告.docx'});
  expect(ui.preview.item.id).toBe('one');expect(window.open).not.toHaveBeenCalled();expect(fetch).not.toHaveBeenCalled();
  expect(api).not.toHaveProperty('editDriveItem');expect(api).not.toHaveProperty('getDriveItemEdit');expect(api).not.toHaveProperty('forceSaveDriveItem');
});
it('reader geometry and controls render without browser UI automation',async()=>{
  const html=await renderToString(createSSRApp({render:()=>h(PanZoomSurface,{width:600,height:800,resetKey:'one',label:'PDF'},
    {leading:()=>h('span','1 / 5'),default:()=>h('canvas')})}));
  expect(html.match(/1 \/ 5/g)).toHaveLength(1);expect(html).toContain('适应页面');expect(html).toContain('向右旋转');expect(html).toContain('预览工具');
});
it('PDF uses protected range loading and the locked OA-compatible worker',()=>{
  const source=fs.readFileSync(new URL('../preview/PdfPreview.vue',import.meta.url),'utf8');
  expect(source).toContain('httpHeaders: previewHeaders(props.ticket)');expect(source).toContain('disableAutoFetch: true');expect(source).toContain('disableStream: true');
  expect(source).not.toContain('<iframe');expect(source).not.toContain('DocsAPI');
});
it('public shares and file/version previews share the same reader',()=>{
  for(const path of ['../pages/PublicLinkPage.vue','../next/components/PreviewHost.vue','../pages/FilePreviewPage.vue'])
    expect(fs.readFileSync(new URL(path,import.meta.url),'utf8')).toContain('FilePreview');
});
