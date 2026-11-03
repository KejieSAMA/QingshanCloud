import { describe,it,expect,vi,beforeEach,afterEach } from 'vitest';
import { createPreviewSession,decodePreviewText } from '../preview/previewSession.js';
import { rotation,rotatedSize,clampZoom,fitScale,pageNumber,zoomAnchor } from '../preview/viewerGeometry.js';
const ready=(extra={})=>({previewTicket:'ticket',sourceIdentity:'source',kind:'office',supported:true,status:'READY',expiresAt:Date.now()+600000,...extra});
const wait=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
function fixture(){
  let owner='alice';const publish=vi.fn(),describe=vi.fn(async()=>ready()),inspect=vi.fn(async()=>ready()),blob=vi.fn(async()=>new Blob(['text'])),revoke=vi.fn();
  const session=createPreviewSession({describe,inspect,blob,publish,identity:()=>owner,makeUrl:()=> 'blob:image',revoke});
  return {session,describe,inspect,blob,publish,revoke,account:()=>owner='bob'};
}
beforeEach(()=>vi.useFakeTimers());afterEach(()=>vi.useRealTimers());
describe('common file preview lifecycle',()=>{
  it('uses one ordinary preview generation with no editor requests',async()=>{
    const f=fixture();await f.session.open({item:{id:'one'}});expect(f.inspect).toHaveBeenCalledTimes(1);
    expect(f.inspect.mock.calls[0].slice(0,3)).toEqual(['ticket',true,false]);expect(f.session.snapshot().kind).toBe('pdf');f.session.dispose();
  });
  it('old server responses stop with a clear compatibility error instead of polling forever',async()=>{
    const f=fixture();f.describe.mockResolvedValue({kind:'office',supported:true,previewTicket:'legacy'});await f.session.open({item:{id:'one'}});
    expect(f.session.snapshot().phase).toBe('error');expect(f.session.snapshot().retryable).toBe(false);expect(f.inspect).not.toHaveBeenCalled();f.session.dispose();
  });
  it('does not overlap polling slow requests',async()=>{
    const f=fixture();let resolve;f.inspect.mockResolvedValueOnce(ready({status:'PENDING'})).mockImplementationOnce(()=>new Promise(r=>resolve=r));
    await f.session.open({item:{id:'one'}});await vi.advanceTimersByTimeAsync(1000);await vi.advanceTimersByTimeAsync(10000);
    expect(f.inspect).toHaveBeenCalledTimes(2);resolve(ready());await wait();expect(f.session.snapshot().phase).toBe('ready');f.session.dispose();
  });
  it('shows real queued and converting phases',async()=>{
    const f=fixture();f.inspect.mockResolvedValueOnce(ready({status:'PENDING',phase:'QUEUED'})).mockResolvedValueOnce(ready({status:'PROCESSING',phase:'CONVERTING'}));
    await f.session.open({item:{id:'one'}});expect(f.session.snapshot().phase).toBe('queued');await vi.advanceTimersByTimeAsync(1000);
    expect(f.session.snapshot().phase).toBe('converting');await vi.advanceTimersByTimeAsync(1000);expect(f.session.snapshot().phase).toBe('ready');f.session.dispose();
  });
  it('backs off transient poll errors and exposes a single eventual failure',async()=>{
    const f=fixture();f.inspect.mockResolvedValueOnce(ready({status:'PENDING'})).mockRejectedValue(new Error('network'));
    await f.session.open({item:{id:'one'}});await vi.advanceTimersByTimeAsync(1000);expect(f.inspect).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1999);expect(f.inspect).toHaveBeenCalledTimes(2);await vi.advanceTimersByTimeAsync(1);expect(f.inspect).toHaveBeenCalledTimes(3);
    await vi.advanceTimersByTimeAsync(4000+8000);expect(f.session.snapshot().phase).toBe('error');f.session.dispose();
  });
  it('pauses hidden polling and resumes immediately',async()=>{
    const f=fixture();f.inspect.mockResolvedValueOnce(ready({status:'PENDING'}));await f.session.open({item:{id:'one'}});
    f.session.setVisible(false);await vi.advanceTimersByTimeAsync(15000);expect(f.inspect).toHaveBeenCalledTimes(1);
    f.session.setVisible(true);await vi.advanceTimersByTimeAsync(0);expect(f.session.snapshot().phase).toBe('ready');f.session.dispose();
  });
  it('visibility changes during initial loading do not query an empty ticket',async()=>{
    const f=fixture();let resolve;f.describe.mockImplementationOnce(()=>new Promise(r=>resolve=r));const opening=f.session.open({item:{id:'one'}});
    f.session.setVisible(false);f.session.setVisible(true);await vi.advanceTimersByTimeAsync(1000);expect(f.inspect).not.toHaveBeenCalled();
    resolve(ready());await opening;expect(f.inspect).toHaveBeenCalledTimes(1);f.session.dispose();
  });
  it('resuming while a poll is in flight does not create a competing query',async()=>{
    const f=fixture();let resolve;f.inspect.mockResolvedValueOnce(ready({status:'PENDING'})).mockImplementationOnce(()=>new Promise(r=>resolve=r));
    await f.session.open({item:{id:'one'}});await vi.advanceTimersByTimeAsync(1000);f.session.setVisible(false);f.session.setVisible(true);
    await vi.advanceTimersByTimeAsync(1000);expect(f.inspect).toHaveBeenCalledTimes(2);resolve(ready());await wait();f.session.dispose();
  });
  it('ignores a late response after switching files',async()=>{
    const f=fixture();let resolve;f.describe.mockImplementationOnce(()=>new Promise(r=>resolve=r));
    const old=f.session.open({item:{id:'old'}});await wait();await f.session.open({item:{id:'new'}});
    resolve(ready({sourceIdentity:'old'}));await old;expect(f.session.snapshot().descriptor.sourceIdentity).toBe('source');f.session.dispose();
  });
  it('account switch cannot expose old image data',async()=>{
    const f=fixture();let resolve;f.describe.mockResolvedValue(ready({kind:'image'}));f.blob.mockImplementation(()=>new Promise(r=>resolve=r));
    const opening=f.session.open({item:{id:'one'}});await wait();f.account();resolve(new Blob(['old']));await opening;
    expect(f.session.snapshot().url).toBe('');f.session.dispose();
  });
  it('close aborts requests, clears text and destroys blob URLs',async()=>{
    const f=fixture();f.describe.mockResolvedValue(ready({kind:'image'}));await f.session.open({item:{id:'one'}});
    expect(f.session.snapshot().url).toBe('blob:image');f.session.close();expect(f.revoke).toHaveBeenCalledWith('blob:image');expect(f.session.snapshot().ticket).toBe('');f.session.dispose();
  });
  it('a non-cooperating response after close is ignored',async()=>{
    const f=fixture();let resolve,signal;f.describe.mockImplementation((_,s)=>{signal=s;return new Promise(r=>resolve=r);});
    const opening=f.session.open({item:{id:'one'}});f.session.close();expect(signal.aborted).toBe(true);resolve(ready());await opening;expect(f.session.snapshot().phase).toBe('closed');f.session.dispose();
  });
  it('permission and version failures are not automatically retried',async()=>{
    const f=fixture();f.describe.mockRejectedValue(Object.assign(new Error('denied'),{status:403}));await f.session.open({item:{id:'one'}});
    await vi.advanceTimersByTimeAsync(30000);expect(f.describe).toHaveBeenCalledTimes(1);expect(f.session.snapshot().retryable).toBe(false);f.session.dispose();
  });
  it('terminal conversion errors have no retry action',async()=>{
    const f=fixture();f.inspect.mockResolvedValue(ready({status:'FAILED',retryable:false,unavailableMessage:'文件已损坏'}));await f.session.open({item:{id:'one'}});
    expect(f.session.snapshot().error).toBe('文件已损坏');await f.session.retry();expect(f.inspect).toHaveBeenCalledTimes(1);f.session.dispose();
  });
  it('an explicit retry carries the retry flag',async()=>{
    const f=fixture();f.inspect.mockResolvedValueOnce(ready({status:'FAILED',retryable:true}));await f.session.open({item:{id:'one'}});await f.session.retry();
    expect(f.inspect.mock.calls[1].slice(0,3)).toEqual(['ticket',true,true]);f.session.dispose();
  });
  it('plain text remains text, including HTML-shaped content',async()=>{
    const f=fixture();f.describe.mockResolvedValue(ready({kind:'text'}));f.blob.mockResolvedValue(new Blob(['<script>alert(1)</script>']));await f.session.open({item:{id:'one'}});
    expect(f.session.snapshot().text).toBe('<script>alert(1)</script>');expect(f.inspect).not.toHaveBeenCalled();f.session.dispose();
  });
  it('text size is checked before decoding',async()=>{
    const f=fixture();f.describe.mockResolvedValue(ready({kind:'text'}));f.blob.mockResolvedValue(new Blob(['a'.repeat(1024*1024+1)]));await f.session.open({item:{id:'one'}});
    expect(f.session.snapshot().text).toBe('');expect(f.session.snapshot().error).toContain('1 MB');f.session.dispose();
  });
  it('renews an ordinary grant while preserving source identity',async()=>{
    const f=fixture();await f.session.open({item:{id:'one'}});f.describe.mockResolvedValue(ready({previewTicket:'renewed'}));await vi.advanceTimersByTimeAsync(540000);
    expect(f.session.snapshot().ticket).toBe('renewed');expect(f.session.snapshot().phase).toBe('ready');f.session.dispose();
  });
  it('does not silently replace a viewed file after an upload',async()=>{
    const f=fixture();await f.session.open({item:{id:'one'}});f.describe.mockResolvedValue(ready({sourceIdentity:'replacement'}));await vi.advanceTimersByTimeAsync(540000);
    expect(f.session.snapshot().phase).toBe('error');expect(f.session.snapshot().error).toContain('文件已更新');f.session.dispose();
  });
});
describe('OA-compatible viewer geometry',()=>{
  it('reads common Chinese text encodings without evaluating markup',()=>{
    expect(decodePreviewText(new TextEncoder().encode('云盘预览').buffer)).toBe('云盘预览');
    expect(decodePreviewText(Uint8Array.from([0xd6,0xd0,0xce,0xc4]).buffer)).toBe('中文');
    expect(decodePreviewText(Uint8Array.from([0xff,0xfe,0x2d,0x4e]).buffer)).toBe('中');
  });
  it('fits portrait and landscape pages without cropping',()=>{expect(fitScale(600,800,332,432)).toBe(.5);expect(rotatedSize(600,800,90)).toEqual({width:800,height:600});});
  it('clamps zoom and normalizes rotation',()=>{expect(clampZoom(50)).toBe(4);expect(clampZoom(0)).toBe(.5);expect(rotation(-90)).toBe(270);expect(rotation(450)).toBe(90);});
  it('accepts only real page numbers',()=>{expect(pageNumber('2',3)).toBe(2);for(const value of ['0','4','a','1.5',''])expect(pageNumber(value,3)).toBeNull();});
  it('keeps the pinch anchor stable while zooming',()=>{expect(zoomAnchor(100,50,500,1000)).toBe(250);});
});
