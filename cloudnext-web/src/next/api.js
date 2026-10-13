import { fetchJson, fetchBlob } from '../api/cloudnext';
export * from '../api/cloudnext';
export const query = (params = {}) => { const q = new URLSearchParams(); for (const [key, value] of Object.entries(params)) if (value !== '' && value != null) q.set(key, value); return q.size ? '?' + q : ''; };
export const get = (url, params = {}, signal) => fetchJson(url + query(params), { signal });
export const send = (url, body, method = 'POST') => fetchJson(url, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
export const blob = fetchBlob;
export const encode = encodeURIComponent;
export const present = ids => send('/api/drive/items/presentation', [...new Set(ids)].slice(0, 100));
