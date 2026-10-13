import { computed, ref, watch } from 'vue';
import { ui, enqueue, processQueue } from '../../next/state';
import { formatFileSize } from '../../utils/formatters';

// The mobile dialog is retained; its tasks share the application queue with desktop.
export function useDriveUploadQueue({
 currentParentId, getMaxUploadFileSizeBytes=()=>0, getMaxUploadFileSizeLabel=()=>'', onQueueCompleted=async()=>{}
}={}) {
 const uploadDialogOpen=ref(false),dragActive=ref(false),uploadInputRef=ref(null);
 const uploadProcessing=computed(()=>ui.processing);
 const uploadQueue=computed(()=>ui.queue.map(e=>({...e,file:e.file||{name:e.name,size:e.sizeBytes}})));
 const uploadSummary=computed(()=>{
  const count=status=>uploadQueue.value.filter(e=>e.status===status).length;
  return '完成 '+count('done')+'，失败 '+count('failed')+'，上传中 '+count('uploading');
 });
 function openUploadDialog(){uploadDialogOpen.value=true;dragActive.value=false;}
 function closeUploadDialog(){uploadDialogOpen.value=false;dragActive.value=false;}
 function enqueueUploads(files){
  const max=Number(getMaxUploadFileSizeBytes()||0);
  const list=Array.from(files||[]);
  const target={scopeType:'PERSONAL',scopeId:'',parentId:currentParentId.value||''};
  for(const file of list){
   if(max&&file.size>max)ui.queue.push({id:'rejected-'+Date.now()+'-'+Math.random(),name:file.name,sizeBytes:file.size,file,target:{...target},status:'rejected',error:'文件大小不能超过 '+(getMaxUploadFileSizeLabel()||formatFileSize(max)),progress:0});
   else enqueue([file],target);
  }
 }
 function onUploadDialogFiles(event){const files=Array.from(event.target.files||[]);event.target.value='';if(files.length){openUploadDialog();enqueueUploads(files);}}
 function removeUploadEntry(id){ui.queue=ui.queue.filter(e=>e.id!==id||e.status==='uploading');}
 function retryUpload(entry){const task=ui.queue.find(e=>e.id===entry.id);if(!task?.file)return;task.status='waiting';task.error='';task.strategy='abort';void processQueue();}
 function describeUploadStatus(entry){return ({done:'上传完成',uploading:'上传中...',failed:entry.error||'上传失败',rejected:entry.error,cancelled:'已取消'})[entry.status]||'等待上传';}
 function onDragLeave(event){if(!event.currentTarget.contains(event.relatedTarget))dragActive.value=false;}
 function onDropFiles(event){dragActive.value=false;openUploadDialog();enqueueUploads(event.dataTransfer?.files);}
 watch(()=>ui.revision,()=>{void onQueueCompleted();});
 return {uploadDialogOpen,uploadProcessing,dragActive,uploadQueue,uploadInputRef,uploadSummary,openUploadDialog,closeUploadDialog,onUploadDialogFiles,removeUploadEntry,retryUpload,describeUploadStatus,onDragLeave,onDropFiles};
}
