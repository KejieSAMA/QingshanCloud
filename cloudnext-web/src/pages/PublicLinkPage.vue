<template>
  <main class="shared-document">
    <header><h1>{{ name }}</h1><p>文件分享</p></header>
    <p v-if="loading" role="status">正在检查访问权限…</p><p v-if="error" role="alert">{{ error }}</p>
    <section v-if="meta"><h2>{{ meta.fileName }}</h2><p v-if="!meta.open">该链接已失效或文件不可用。</p>
      <template v-else><label v-if="meta.requirePassword">提取码 <input v-model="password" type="password" autocomplete="off" /></label>
        <div class="shared-actions"><button class="btn primary" :disabled="busy" @click="preview">预览</button><button v-if="meta.permission==='download'" class="btn" :disabled="busy" @click="downloadClick">下载</button></div>
      </template>
    </section>
  </main>
  <FilePreview v-if="target" :target="target" :download="meta.permission==='download'?download:undefined" @close="target=null" />
</template>
<script setup>
import { computed,onMounted,onUnmounted,ref } from 'vue';
import { useRoute,useRouter } from 'vue-router';
import { fetchBlob,getPublicLinkMeta,verifyPublicLink,publicLinkPreviewUrl,publicLinkDownloadUrl } from '../api/cloudnext';
import { getStoredSession,sessionContextKey } from '../session';
import { driveRouteQuery } from '../driveContext';
import FilePreview from '../preview/FilePreview.vue';
const route=useRoute(),router=useRouter(),token=String(route.params.token||'');
const meta=ref(null),password=ref(''),loading=ref(true),busy=ref(false),error=ref(''),target=ref(null);
const name=computed(()=>meta.value?.driveName||getStoredSession()?.drive?.name||'专属云盘');
let disposed=false,sequence=0;
function failure(e){
  if(disposed||e.name==='AbortError')return;
  if(e.status===401){const match=/DRIVE_LOGIN_REQUIRED:([a-zA-Z0-9_-]+)/.exec(e.message);
    void router.replace({name:'login',query:{...driveRouteQuery(),...(match?{entryId:match[1]}:{}),returnPath:route.fullPath}});
  }else error.value=e.message||'无法访问该文件';
}
async function accessTicket(){if(!meta.value.requirePassword)return '';return (await verifyPublicLink(token,password.value)).accessTicket;}
async function preview(){
  if(busy.value)return;busy.value=true;error.value='';const n=++sequence,owner=sessionContextKey();
  try{const ticket=await accessTicket();if(disposed||n!==sequence||owner!==sessionContextKey())return;
    target.value={item:{name:meta.value.fileName},publicUrl:publicLinkPreviewUrl(token,ticket)};
  }catch(e){failure(e);}finally{if(!disposed&&n===sequence)busy.value=false;}
}
async function downloadClick(){
  if(busy.value)return;busy.value=true;error.value="";
  try{await download();}catch(e){failure(e);}finally{if(!disposed)busy.value=false;}
}
async function download(){
  const owner=sessionContextKey();const ticket=await accessTicket(),blob=await fetchBlob(publicLinkDownloadUrl(token,ticket));
  if(disposed||owner!==sessionContextKey())return;
  const url=URL.createObjectURL(blob),anchor=document.createElement('a');anchor.href=url;anchor.download=meta.value.fileName;anchor.click();setTimeout(()=>URL.revokeObjectURL(url),60000);
}
onMounted(async()=>{try{const result=await getPublicLinkMeta(token);if(!disposed)meta.value=result;}catch(e){failure(e);}finally{loading.value=false;}});
onUnmounted(()=>{disposed=true;sequence++;password.value='';target.value=null;});
</script>
<style scoped>
.shared-document{max-width:960px;margin:0 auto;padding:32px 20px}.shared-document header{border-bottom:.5px solid var(--line);margin-bottom:24px}.shared-document h1{font-size:24px}.shared-actions{display:flex;gap:12px;margin:20px 0}.shared-document [role=alert]{color:var(--danger)}
</style>
