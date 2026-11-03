// The embed context survives SPA navigation, but never crosses a document/account.
const query = new URLSearchParams(globalThis.window?.location?.search || '');
export const embedded = query.get('embed') === '1';
export const channel = query.get('channel') || '';
export const parentOrigin = query.get('parentOrigin') || '';
export const nativeMobile = embedded && query.get('device') === 'mobile';
export const protocol = 'oa-cloudnext';
export const protocolVersion = 1;

export function embeddedQuery() {
  return embedded ? { embed: '1', channel, parentOrigin, ...(nativeMobile ? { device: 'mobile' } : {}) } : {};
}
export function validEnvelope(event, expectedWindow, origin, id) {
  const data = event?.data;
  return event.source === expectedWindow && event.origin === origin
    && data?.protocol === protocol && data?.version === protocolVersion
    && data?.channel === id && /^[a-f0-9]{32,64}$/.test(id)
    && typeof data.type === 'string';
}
export function trustedParent(origins) {
  try {
    const value = new URL(parentOrigin);
    return value.origin === parentOrigin && ['http:', 'https:', 'capacitor:'].includes(value.protocol)
      && /^[a-f0-9]{32,64}$/.test(channel)
      && Array.isArray(origins) && origins.includes(parentOrigin)
      && window.parent !== window;
  } catch { return false; }
}
