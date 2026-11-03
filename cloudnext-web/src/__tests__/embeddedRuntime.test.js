import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import 'vue';
let surface, state, router, after, api, runtime, authReply;
const channel='a'.repeat(32);
const session={token:'cloud-only-token',personKey:'alice',drive:{id:'b'.repeat(64),entryId:'default'}};
const grant={ticket:'c'.repeat(64),fileKey:'c'.repeat(64),action:'SAVE',fileName:'文件.pdf',mimeType:'application/pdf',sizeBytes:12,expiresAt:Date.now()+300000,driveId:'b'.repeat(64)};
const json=value=>({ok:true,status:200,text:async()=>JSON.stringify(value)});
const flush=()=>new Promise(resolve=>setImmediate(resolve));
function message(type,payload={},origin='https://oa.test') {
  const event=new Event('message');Object.assign(event,{origin,source:surface.parent,data:{protocol:'oa-cloudnext',version:1,channel,type,...payload}});
  surface.dispatchEvent(event);
}
beforeEach(async()=>{
  vi.resetModules();authReply=async()=>json(session);
  surface=new EventTarget();Object.assign(surface,{location:{origin:'https://drive.test',search:'?embed=1&entryId=default&parentOrigin=https%3A%2F%2Foa.test&channel='+channel},parent:{postMessage:vi.fn()},
    localStorage:{getItem:()=>{throw Error('blocked storage');},setItem:()=>{throw Error('blocked storage');},removeItem:()=>{throw Error('blocked storage');}}});
  vi.stubGlobal('window',surface);vi.stubGlobal('document',{documentElement:{dataset:{}},querySelectorAll:()=>[]});
  vi.stubGlobal('fetch',vi.fn(async(url,options)=>{
    if(url==='/api/auth/status')return json({embeddedOrigins:['https://oa.test']});
    if(url==='/api/auth/sso/embedded')return authReply(options);
    if(url==='/api/auth/me')return json(session);
    if(url==='/api/drive/native-files/ticket')return json(grant);
    throw Error('Unexpected request: '+url);
  }));
  state={value:{path:'/oa-embed',fullPath:'/oa-embed',meta:{public:true}}};
  router={currentRoute:state,options:{history:{state:{position:0}}},afterEach:fn=>{after=fn;},beforeResolve:()=>()=>{},
    replace:vi.fn(async path=>{state.value={path:new URL(path,'https://drive.test').pathname,fullPath:path,meta:{}};after?.(state.value);}),
    push:vi.fn(async path=>{state.value={path:new URL(path,'https://drive.test').pathname,fullPath:path,meta:{}};after?.(state.value);}),back:vi.fn()};
  api=await import('../api/cloudnext.js');runtime=await import('../embedded/runtime.js');await runtime.startEmbedded(router);
});
afterEach(async()=>{message('dispose');await flush();vi.unstubAllGlobals();});
it('the actual bridge exchanges a single-use ticket without a cookie or OA token',async()=>{
  message('authenticate',{ticket:'single-use',returnPath:'/',platform:'web'});await flush();
  const call=fetch.mock.calls.find(([url])=>url==='/api/auth/sso/embedded');
  expect(call[1].credentials).toBe('omit');expect(JSON.parse(call[1].body)).toEqual({ticket:'single-use',returnPath:'/'});
  const {getStoredToken}=await import('../session.js');expect(getStoredToken()).toBe('cloud-only-token');expect(runtime.embedState.phase).toBe('ready');
  expect(surface.parent.postMessage).toHaveBeenCalledWith(expect.objectContaining({type:'authenticated'}),'https://oa.test');
});
it('a foreign window cannot trigger an authentication request',async()=>{
  message('authenticate',{ticket:'foreign',returnPath:'/'},'https://evil.test');await flush();expect(fetch.mock.calls.some(([url])=>url==='/api/auth/sso/embedded')).toBe(false);
});
it('preview visibility uses the authenticated frame boot and monotonic revisions',async()=>{
  message('authenticate',{ticket:'one',returnPath:'/'});await flush();
  runtime.setEmbeddedPreviewOpen(true);runtime.setEmbeddedPreviewOpen(false);
  const sent=surface.parent.postMessage.mock.calls.map(([d])=>d).filter(d=>d.type==='preview-state');
  expect(sent.at(-2).open).toBe(true);expect(sent.at(-1).open).toBe(false);
  expect(sent.at(-1).bootId).toBeTruthy();expect(sent.at(-1).revision).toBeGreaterThan(sent.at(-2).revision);
  expect(sent.at(-1)).not.toHaveProperty('token');
});
it('dispose aborts authentication and ignores a non-cooperating late success',async()=>{
  let complete,signal;authReply=options=>{signal=options.signal;return new Promise(resolve=>{complete=resolve;});};
  message('authenticate',{ticket:'one',returnPath:'/'});await flush();message('dispose');expect(signal.aborted).toBe(true);
  complete(json(session));await flush();const s=await import('../session.js');expect(s.getStoredToken()).toBe('');
});
it('auth failures report once and keep the password form out of embedded routing',async()=>{
  authReply=async()=>({ok:false,status:409,text:async()=>JSON.stringify({code:'DIRECTORY_BINDING_MISSING',message:'not ready'})});
  message('authenticate',{ticket:'one',returnPath:'/'});await flush();
  expect(runtime.embedState.phase).toBe('error');expect(surface.parent.postMessage.mock.calls.filter(([d])=>d.type==='auth-error')).toHaveLength(1);
});
it('native downloads send only the scoped file capability and accept a matching result',async()=>{
  message('authenticate',{ticket:'one',returnPath:'/',platform:'android',nativeFiles:true});await flush();
  const downloading=api.downloadDriveItem({id:'file-1'});await flush();
  const sent=surface.parent.postMessage.mock.calls.find(([d])=>d.type==='native-file')[0];
  expect(sent.grant).toEqual(grant);expect(JSON.stringify(sent)).not.toContain('cloud-only-token');
  message('file-result',{requestId:sent.requestId,ok:true,result:{saved:true}});await expect(downloading).resolves.toEqual({saved:true});
});
it('back closes the existing dialog instead of leaving an unsaved editor',async()=>{
  const click=vi.fn();document.querySelectorAll=()=>[{getClientRects:()=>[{}],querySelector:()=>({click,disabled:false})}];
  vi.stubGlobal('getComputedStyle',()=>({visibility:'visible'}));message('back',{requestId:'back-1'});await flush();
  expect(click).toHaveBeenCalledOnce();expect(router.back).not.toHaveBeenCalled();expect(router.replace).not.toHaveBeenCalled();
});
it('advertises tab navigation and sends scoped route state and a matching result',async()=>{
  const boot=surface.parent.postMessage.mock.calls.find(([d])=>d.type==='ready')[0];expect(boot.navigationVersion).toBe(1);
  message('authenticate',{ticket:'one',returnPath:'/'});await flush();
  message('navigate-tab',{requestId:'1'.repeat(32),tab:'files',bootId:boot.bootId});await flush();
  expect(router.push).toHaveBeenCalledOnce();const target=router.push.mock.calls[0][0];
  expect(target).toContain('/my-files?');expect(target).toContain('entryId=default');expect(target).toContain('embed=1');
  const route=surface.parent.postMessage.mock.calls.filter(([d])=>d.type==='route').at(-1)[0];
  expect(route.bootId).toBe(boot.bootId);expect(route.revision).toBe(2);
  expect(surface.parent.postMessage).toHaveBeenCalledWith(expect.objectContaining({type:'navigation-result',bootId:boot.bootId,requestId:'1'.repeat(32),ok:true}),'https://oa.test');
});
it('ignores a navigation command from a foreign origin or an old document',async()=>{
  message('authenticate',{ticket:'one',returnPath:'/'});await flush();
  const boot=surface.parent.postMessage.mock.calls.find(([d])=>d.type==='ready')[0];
  message('navigate-tab',{requestId:'1'.repeat(32),tab:'files',bootId:boot.bootId},'https://evil.test');
  message('navigate-tab',{requestId:'2'.repeat(32),tab:'files',bootId:'old'});await flush();
  expect(router.push).not.toHaveBeenCalled();
});
it('does not publish a destination from a failed route transition',async()=>{
  message('authenticate',{ticket:'one',returnPath:'/'});await flush();
  const count=surface.parent.postMessage.mock.calls.filter(([d])=>d.type==='route').length;
  after({path:'/shared',fullPath:'/shared',meta:{}},state.value,{type:'aborted'});
  expect(surface.parent.postMessage.mock.calls.filter(([d])=>d.type==='route')).toHaveLength(count);
});
