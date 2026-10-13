import { beforeEach, describe, expect, it, vi } from 'vitest';
const mocks=vi.hoisted(()=>({token:'',get:vi.fn(),admin:vi.fn(),clear:vi.fn(),refresh:vi.fn()}));
vi.mock('../next/api',()=>({get:mocks.get,getCurrentAdminStatus:mocks.admin}));
vi.mock('../session',()=>({
 getStoredToken:()=>mocks.token,getStoredSession:()=>null,
 clearStoredSession:mocks.clear,emitUnauthorizedSignal:vi.fn(),emitDriveRefreshSignal:mocks.refresh
}));
import { ui, syncSession, init, dialog, enqueue, busyUploads, applyPrefs } from '../next/state';

import {defaultPreferences,normalizePreferences} from '../next/preferences';
const requests=[];
class Xhr {
 constructor(){this.upload={};requests.push(this);}
 open(method,url){this.url=url;}
 setRequestHeader(name,value){this.token=value;}
 send(body){this.body=body;}
 abort(){this.aborted=true;this.onabort?.();}
}
function signIn(token){mocks.token=token;syncSession(token?{token,personKey:token,personName:token}:null);}
beforeEach(()=>{
 globalThis.document={documentElement:{dataset:{}},body:{dataset:{},classList:{toggle:vi.fn()}},activeElement:null};
 globalThis.XMLHttpRequest=Xhr;
 signIn('');requests.length=0;mocks.get.mockReset();mocks.admin.mockReset();
});
describe('unified desktop session and themes',()=>{
 it('defaults and reset use blue, saved green remains green',()=>{
  expect(defaultPreferences().primaryColor).toBe('blue');
  expect(normalizePreferences({primaryColor:'green'}).primaryColor).toBe('green');
  expect(normalizePreferences({primaryColor:'invalid'}).primaryColor).toBe('blue');
 });
 it('theme preview and cancellation also update the app-level dialog host',()=>{
  signIn('a');applyPrefs({...ui.prefs,primaryColor:'green'});
  expect(ui.effectivePrimaryColor).toBe('green');
  expect(ui.prefs.primaryColor).toBe('blue');
  applyPrefs();expect(ui.effectivePrimaryColor).toBe('blue');
 });
 it('clears account data and suspended dialogs but does not reset on same-session checks',async()=>{
  signIn('a');ui.recent=[{id:'private'}];ui.admin=true;ui.prefs.primaryColor='green';
  const pending=dialog({title:'old user dialog'});const revision=ui.accountRevision;
  syncSession({token:'a',personKey:'a',personName:'Renamed'});
  expect(ui.accountRevision).toBe(revision);expect(ui.recent).toHaveLength(1);
  signIn('b');
  expect(await pending).toBeNull();expect(ui.recent).toEqual([]);expect(ui.admin).toBe(false);
  expect(ui.prefs.primaryColor).toBe('blue');expect(ui.session.personKey).toBe('b');
 });
 it('does not apply a previous user preference response after switching accounts',async()=>{
  signIn('a');let resolve;const old=new Promise(r=>resolve=r);
  mocks.get.mockReturnValue(old);mocks.admin.mockResolvedValue({admin:true});
  const pending=init();signIn('b');resolve({primaryColor:'green'});await pending;
  expect(ui.prefs.primaryColor).toBe('blue');expect(ui.admin).toBe(false);expect(ui.ready).toBe(false);
 });
 it('keeps uploads across layout changes and aborts all old-account tasks on sign out',async()=>{
  signIn('a');const file=new Blob(['contents']);file.name='example.txt';
  enqueue([file,file],{scopeType:'WORKSPACE',scopeId:'w',parentId:'folder'});
  expect(busyUploads()).toBe(true);expect(requests).toHaveLength(1);
  document.documentElement.dataset.experience='mobile';
  expect(requests[0].aborted).not.toBe(true);
  expect(requests[0].url).toBe('/api/workspaces/w/files');
  expect(requests[0].body.get('parentId')).toBe('folder');
  signIn('b');await Promise.resolve();await Promise.resolve();
  expect(requests[0].aborted).toBe(true);expect(requests).toHaveLength(1);
  expect(ui.queue).toEqual([]);expect(ui.processing).toBe(false);
 });
});
