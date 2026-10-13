<template><section class="saved-mobile"><p v-if="error" role="alert">{{error}} <button @click="load">重试</button></p><MobileFileList :items="items" :loading="busy" empty-text="暂无可访问的文件" @item-click="open"/><div class="saved-pager"><button :disabled="page<=1||busy" @click="change(page-1)">上一页</button><span>{{page}} / {{pages}} · {{total}} 项</span><button :disabled="page>=pages||busy" @click="change(page+1)">下一页</button></div></section></template>
<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';
import { useRoute,useRouter } from 'vue-router';
import MobileFileList from '../../components/mobile/MobileFileList.vue';
import { fetchJson, previewDriveItem } from '../../api/cloudnext';
import { itemLocation } from '../../next/navigation';
const route=useRoute(),router=useRouter(),items=ref([]),total=ref(0),busy=ref(false),error=ref('');
const page=computed(()=>Math.max(1,Number(route.query.page)||1)),pages=computed(()=>Math.max(1,Math.ceil(total.value/30)));
let controller,sequence=0;
async function load(){
 controller?.abort();controller=new AbortController();const seq=++sequence;busy.value=true;error.value='';
 try {const path=route.name==='favorites'?'/api/drive/me/favorites':'/api/drive/me/recent-opened/query';const result=await fetchJson(path+'?page='+page.value+'&size=30',{signal:controller.signal});if(seq===sequence){items.value=result.items||[];total.value=result.total||0;}}
 catch(e){if(seq===sequence&&e.name!=='AbortError')error.value=e.message;}
 finally{if(seq===sequence)busy.value=false;}
}
function change(value){router.push({query:{...route.query,page:value}});}
async function open(item){try{if(item.itemType==='folder')await router.push(itemLocation(item,true));else await previewDriveItem(item);}catch(e){error.value=e.message;}}
watch(()=>route.fullPath,load,{immediate:true});onUnmounted(()=>{sequence++;controller?.abort();});
</script>
<style scoped>.saved-mobile{background:#fff;min-height:100%}.saved-mobile>p{padding:16px;color:#b42318}.saved-pager{display:flex;align-items:center;justify-content:space-between;padding:12px;gap:12px;color:var(--text-secondary);font-size:12px}.saved-pager button{padding:8px;border-radius:6px;background:var(--bg-panel-soft);color:var(--text-primary)}.saved-pager button:disabled{opacity:.4}</style>
