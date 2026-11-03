<template><section class="file-action"><a v-if="oaUrl" :href="oaUrl">返回 OA</a><h1>{{ file?.fileName || '云盘文件' }}</h1><p v-if="error" role="alert">{{ error }}</p><form v-else-if="file" method="post" action="/api/drive/native-files/content"><input type="hidden" name="ticket" :value="ticket"><button type="submit">下载</button></form><p v-else>正在准备文件…</p></section></template>
<script setup>
import { onMounted, ref } from 'vue';
import { getAuthStatus, fetchUnauthenticatedJson } from '../api/cloudnext.js';
import { oaReturnUrl } from '../ssoRecovery.js';
const ticket = new URLSearchParams(location.hash.slice(1)).get('ticket') || '';
history.replaceState(history.state, '', location.pathname);
const oaUrl = ref(''), file = ref(null), error = ref('');
onMounted(async () => {
  try {
    const status = await getAuthStatus(); oaUrl.value = oaReturnUrl(status.formSsoStartUrl);
    file.value = await fetchUnauthenticatedJson('/api/drive/native-files/metadata', { headers: { 'X-CloudNext-File-Ticket': ticket } });
  } catch (e) { error.value = e.message || '文件已失效，请返回 OA 重试。'; }
});
</script>
<style scoped>.file-action{padding:24px;max-width:700px;margin:auto;font:16px/1.6 sans-serif}.file-action h1{font-size:20px;overflow-wrap:anywhere}.file-action button{min-height:44px;padding:8px 24px;border:0;background:#1765d1;color:white}.file-action a{color:#1765d1}</style>
