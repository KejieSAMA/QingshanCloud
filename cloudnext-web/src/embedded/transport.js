import { embedded } from './context.js';
let transport;
export function setFileTransport(value) { transport = value; }
export function usesHostFiles() { return embedded && Boolean(transport); }
export function hostFileAction(payload) { return transport(payload); }
