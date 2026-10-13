import {beforeEach,expect,it,vi} from 'vitest';
const session=vi.hoisted(()=>({token:'a',clear:vi.fn(),unauthorized:vi.fn()}));
vi.mock('../session',()=>({getStoredToken:()=>session.token,clearStoredSession:session.clear,emitUnauthorizedSignal:session.unauthorized}));
import {fetchJson,fetchBlob,fetchMultipart,login} from '../api/cloudnext';
beforeEach(()=>{session.token='a';session.clear.mockClear();session.unauthorized.mockClear();globalThis.fetch=vi.fn();});
it('a late 401 from an old token does not sign out the new user',async()=>{
 let done;fetch.mockReturnValue(new Promise(r=>done=r));
 const pending=fetchJson('/api/test');session.token='b';
 done({ok:false,status:401,text:async()=>JSON.stringify({message:'expired'})});
 await expect(pending).rejects.toMatchObject({name:'AbortError'});
 expect(session.clear).not.toHaveBeenCalled();expect(session.unauthorized).not.toHaveBeenCalled();
});
it('discards successful JSON, binary and upload responses belonging to another account',async()=>{
 for(const request of [()=>fetchJson('/api/test'),()=>fetchBlob('/api/test'),()=>fetchMultipart('/api/test',new FormData())]){
  session.token='a';let done;fetch.mockReturnValue(new Promise(r=>done=r));
  const pending=request();session.token='b';
  done({ok:true,text:async()=>'{}',blob:async()=>new Blob(['old'])});
  await expect(pending).rejects.toMatchObject({name:'AbortError'});
 }
});
it('unauthenticated login does not reference authenticated request state',async()=>{
 fetch.mockResolvedValue({ok:true,text:async()=>JSON.stringify({token:'login-token'})});
 await expect(login({credential:'test',password:'fixture'})).resolves.toEqual({token:'login-token'});
});
