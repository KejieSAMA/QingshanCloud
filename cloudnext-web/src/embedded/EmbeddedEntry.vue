<template><div class="embedded-entry" role="status"><template v-if="embedState.error"><p>{{ embedState.error }}</p><button type="button" @click="retryEmbeddedAuth">重试</button></template><span v-else-if="embedState.phase !== 'error'">正在进入云盘…</span></div></template>
<script setup>
import { embedState, retryEmbeddedAuth } from './runtime.js';
</script>
<style scoped>
.embedded-entry { display: grid; align-content: center; justify-items: center; gap: 12px; height: 100%; min-height: 200px; padding: 24px; color: #60718c; }
.embedded-entry p { margin: 0; }
.embedded-entry button { padding: 8px 18px; background: #fff; color: #1765d1; border: .5px solid #ccd7e5; border-radius: 4px; }
</style>
