/** Framework-free preview lifecycle: one request at a time, version/account guarded. */
export function createPreviewSession({ describe, inspect, blob, identity, publish, now = Date.now,
  schedule = setTimeout, unschedule = clearTimeout, makeUrl = b => URL.createObjectURL(b), revoke = u => URL.revokeObjectURL(u) }) {
  let generation = 0, abort, timer, target, owner, alive = true, visible = true, readyUrl = '', failures = 0, running = null;
  let state = { phase: 'loading', descriptor: null, ticket: '', kind: '', url: '', text: '', error: '', retryable: false };
  const emit = change => { state = { ...state, ...change }; publish(state); };
  const valid = n => alive && n === generation && owner === identity() && !abort?.signal.aborted;
  function cancel() { unschedule(timer); timer = null; abort?.abort(); }
  function clearUrl() { if (readyUrl) revoke(readyUrl); readyUrl = ''; }
  function later(n, ms) { if (visible && valid(n)) timer = schedule(() => void poll(n), ms); }
  function error(n, e) {
    if (!valid(n) || e?.name === 'AbortError') return;
    emit({ phase: 'error', error: e?.message || '预览加载失败，请重试。', retryable: ![401, 403, 404, 409, 410, 413, 415, 426].includes(e?.status) });
  }
  async function consume(n, descriptor) {
    if (!valid(n)) return;
    if(!descriptor || typeof descriptor.status!=='string') throw Object.assign(new Error('预览服务尚未更新，请稍后重新打开。'),{status:426});
    if (state.descriptor?.sourceIdentity && descriptor.sourceIdentity !== state.descriptor.sourceIdentity)
      throw Object.assign(new Error('文件已更新，请重新打开预览。'), { status: 409 });
    emit({ descriptor, ticket: descriptor.previewTicket || state.ticket });
    if (descriptor.supported === false) { emit({ phase: 'unsupported', error: descriptor.unavailableMessage || '此格式暂不支持预览。', retryable: false }); return; }
    if (descriptor.status === 'FAILED') { emit({ phase: 'error', error: descriptor.unavailableMessage || '预览生成失败。', retryable: descriptor.retryable === true }); return; }
    if (descriptor.status !== 'READY') {
      emit({ phase: descriptor.phase === 'CONVERTING' ? 'converting' : 'queued' }); later(n, 1000); return;
    }
    const kind = descriptor.kind === 'office' ? 'pdf' : descriptor.kind;
    if (kind === 'pdf') emit({ kind, phase: 'ready', error: '', retryable: false });
    else {
      const content = await blob(state.ticket, abort.signal);
      if (!valid(n)) return;
      if (kind === 'text') {
        if (content.size > 1024 * 1024) throw Object.assign(new Error('文本超过 1 MB，请下载后查看。'), { status: 413 });
        const text = decodePreviewText(await content.arrayBuffer()); if (!valid(n)) return;
        emit({ kind, text, phase: 'ready', error: '', retryable: false });
      } else if (kind === 'image') {
        clearUrl(); readyUrl = makeUrl(content); emit({ kind, url: readyUrl, phase: 'ready', error: '', retryable: false });
      }
    }
    // Renew an ordinary viewer grant before it expires. Retain the visible page during renewal.
    if (target && !target.ticket) later(n, Math.max(1000, (descriptor.expiresAt || now() + 540000) - now() - 60000));
  }
  async function poll(n) {
    if (!valid(n) || !visible || running) return;
    const request = { generation: n }; running = request;
    try {
      if (state.phase === 'ready') { await consume(n, await describe(target, abort.signal)); }
      else { await consume(n, await inspect(state.ticket, false, false, abort.signal)); }
      failures = 0;
    } catch (e) {
      if (!valid(n) || e?.name === 'AbortError') return;
      if ([401,403,404,409,410].includes(e?.status)) error(n,e);
      else { failures++; if (failures >= 4) error(n,e); else later(n, Math.min(8000, 1000 * 2 ** failures)); }
    } finally { if (running === request) running = null; }
  }
  async function open(value, retry = false) {
    cancel(); clearUrl(); target = value; owner = identity(); const n = ++generation;
    abort = new AbortController(); failures = 0;
    const request = { generation: n }; running = request;
    emit({ phase: 'loading', descriptor: null, ticket: '', kind: '', url: '', text: '', error: '', retryable: false });
    try {
      let descriptor = await describe(value, abort.signal); if (!valid(n)) return;
      if(!descriptor || typeof descriptor.status!=='string') throw Object.assign(new Error('预览服务尚未更新，请稍后重新打开。'),{status:426});
      if (descriptor.kind === 'office' && descriptor.supported !== false)
        descriptor = await inspect(descriptor.previewTicket, true, retry, abort.signal);
      await consume(n, descriptor);
    } catch (e) { error(n,e); }
    finally { if (running === request) running = null; }
  }
  return {
    open,
    retry() { if (state.retryable && target) return open(target, true); },
    setVisible(value) {
      visible = value;
      if (!value) { unschedule(timer); timer = null; }
      else if (!running && alive && owner === identity() && target) { unschedule(timer); timer = schedule(() => void poll(generation), 0); }
    },
    close() { generation++; cancel(); clearUrl(); target = null; emit({ phase: 'closed', url: '', text: '', descriptor: null, ticket: '' }); },
    dispose() { alive = false; generation++; cancel(); clearUrl(); },
    snapshot: () => state,
  };
}
export function decodePreviewText(buffer) {
  const bytes=new Uint8Array(buffer);
  if(bytes[0]===0xff&&bytes[1]===0xfe)return new TextDecoder('utf-16le').decode(bytes);
  if(bytes[0]===0xfe&&bytes[1]===0xff)return new TextDecoder('utf-16be').decode(bytes);
  try{return new TextDecoder('utf-8',{fatal:true}).decode(bytes);}
  catch{return new TextDecoder('gb18030').decode(bytes);}
}
