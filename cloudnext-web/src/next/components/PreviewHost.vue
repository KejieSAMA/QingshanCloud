<template><FilePreview v-if="ui.preview" :target="ui.preview" :download="download" :system-open="usesHostFiles() ? openExternal : undefined" @close="ui.preview = null" /></template>
<script setup>
import FilePreview from '../../preview/FilePreview.vue';
import { ui } from '../state';
import * as api from '../api';
import { usesHostFiles, hostFileAction } from '../../embedded/transport.js';
async function openExternal() {
  const p=ui.preview;
  return hostFileAction({ itemIds:[p.item.id], versionId:p.version?.id, action:'OPEN' });
}
async function download() {
  const p=ui.preview;
  if(usesHostFiles()) return hostFileAction({ itemIds:[p.item.id], versionId:p.version?.id, action:'SAVE' });
  if(!p.version) return api.downloadDriveItem(p.item);
  const b=await api.blob('/api/drive/items/'+api.encode(p.item.id)+'/versions/'+api.encode(p.version.id)+'/download');
  const u=URL.createObjectURL(b),a=document.createElement('a');a.href=u;a.download=p.version.fileName;a.click();
  setTimeout(()=>URL.revokeObjectURL(u),60000);
}
</script>
