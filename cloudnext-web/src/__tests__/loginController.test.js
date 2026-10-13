import {beforeEach,describe,expect,it,vi} from 'vitest';
const m=vi.hoisted(()=>({
 mounted:null,unmounted:null,token:'',query:{},replace:vi.fn(),login:vi.fn(),complete:vi.fn(),status:vi.fn(),logout:vi.fn(),set:vi.fn()
}));
vi.mock('vue',async importOriginal=>({...await importOriginal(),onMounted:fn=>m.mounted=fn,onUnmounted:fn=>m.unmounted=fn}));
vi.mock('vue-router',()=>({useRoute:()=>({query:m.query}),useRouter:()=>({replace:m.replace})}));
vi.mock('../api/cloudnext',()=>({getAuthStatus:m.status,login:m.login,logout:m.logout,completeSsoLogin:m.complete,buildSsoStartUrl:()=>'/api/auth/sso/start'}));
vi.mock('../session',()=>({getStoredToken:()=>m.token,clearStoredSession:()=>m.token='',setStoredSession:s=>{m.token=s.token;m.set(s);}}));
vi.mock('../loginPreference',()=>({
 loadLoginPreference:()=>({rememberPassword:false,credential:''}),saveLoginPreference:vi.fn(),loadBrowserPassword:vi.fn(),
 preventSilentPasswordAccess:vi.fn(),storeBrowserPassword:vi.fn()
}));
import {useLoginController} from '../auth/useLoginController';
beforeEach(()=>{
 m.token='';m.query={};m.replace.mockReset();m.set.mockReset();m.login.mockReset();m.complete.mockReset();
 m.status.mockResolvedValue({provider:'FORM',configured:true,ssoEnabled:true});m.logout.mockResolvedValue({});
});
describe('shared desktop/mobile authentication controller',()=>{
 it('password login returns to the saved directory using the router',async()=>{
  m.query={returnPath:'/next.html#/my-files?parentId=folder'};
  m.login.mockResolvedValue({token:'signed-in'});
  const form=useLoginController();form.form.credential='employee';form.form.password='fixture';
  await form.onSubmit();
  expect(m.set).toHaveBeenCalledWith({token:'signed-in'});
  expect(m.replace).toHaveBeenCalledWith('/my-files?parentId=folder');
 });
 it('SSO completion consumes bootstrap once and preserves the shared location',async()=>{
  m.query={sso:'complete',returnPath:'/shared?shareId=s&parentId=f'};m.token='previous';
  m.complete.mockResolvedValue({token:'sso-session'});
  useLoginController();await m.mounted();
  expect(m.logout).toHaveBeenCalledTimes(1);expect(m.complete).toHaveBeenCalledTimes(1);
  expect(m.replace).toHaveBeenCalledWith('/shared?shareId=s&parentId=f');
 });
 it('disposed login requests cannot restore an old account',async()=>{
  let done;m.login.mockReturnValue(new Promise(r=>done=r));
  const form=useLoginController();form.form.credential='employee';form.form.password='fixture';
  const pending=form.onSubmit();m.unmounted();done({token:'old'});await pending;
  expect(m.set).not.toHaveBeenCalled();expect(m.replace).not.toHaveBeenCalled();expect(form.form.password).toBe('');
 });
 it('SSO failures stay on the recovery form instead of consuming another ticket',async()=>{
  m.query={sso:'complete'};m.complete.mockRejectedValue({status:401,data:{code:'TICKET_INVALID'}});
  const form=useLoginController();await m.mounted();
  expect(form.recoveryCode.value).toBe('TICKET_INVALID');expect(m.complete).toHaveBeenCalledTimes(1);
  expect(m.replace).not.toHaveBeenCalled();
 });
});
