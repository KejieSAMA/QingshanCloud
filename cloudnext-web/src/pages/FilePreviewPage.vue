<template>
  <FilePreview v-if="!editing && ticket" :target="target" @close="close" />
  <main v-else class="file-preview-unavailable"><p role="alert">{{ editing ? '在线编辑已停用，请重新打开文件预览。' : '预览链接无效，请重新打开文件。' }}</p><button type="button" @click="close">返回云盘</button></main>
</template>
<script setup>
import { computed } from 'vue';
import { useRoute,useRouter } from 'vue-router';
import FilePreview from '../preview/FilePreview.vue';
import { withDriveContext } from '../driveContext.js';
const route=useRoute(),router=useRouter();
const ticket=computed(()=>typeof route.query.ticket==='string'?route.query.ticket:'');
const editing=computed(()=>route.name==='edit-office');
const target=computed(()=>({ticket:ticket.value}));
function close(){if(window.history.state?.back)router.back();else void router.replace(withDriveContext('/'));}
</script>
<style scoped>.file-preview-unavailable{padding:24px;color:#475467}</style>
