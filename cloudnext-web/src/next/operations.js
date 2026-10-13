import * as api from './api';
import { ui, dialog, confirm, notify, changed, batchResult, run, applyPrefs, busyUploads } from './state';
import { itemLocation } from './navigation';
import { clearStoredSession, getSessionPersonKey, getStoredToken } from '../session';
import { formatFileSize as size, formatTime as time } from '../utils/formatters';
import { canManageMembers, canChangeMemberRole, canPromoteToOwner, canRemoveMember } from '../composables/drive/workspacePermissions';
export const permissionOptions = [['read','仅预览'],['download','可预览和下载'],['edit','可编辑']];
export const conflictField = {key:'conflictStrategy',label:'遇到同名项目',type:'select',options:[['keep_both','保留两份'],['abort','遇同名停止'],['replace','覆盖']]};
export const dateValue = v => v ? v.slice(0,10) : '';
export const expires = v => v ? new Date(v+'T23:59:59').toISOString() : '';
export function menu(event, actions) {
 const rect=event.currentTarget?.getBoundingClientRect();
 const x=event.type==='contextmenu'?event.clientX:(rect?.right||event.clientX);
 const y=event.type==='contextmenu'?event.clientY:(rect?.bottom||event.clientY);
 ui.popup={x:Math.min(window.innerWidth-235,Math.max(8,x-215)),y:Math.max(8,Math.min(y,window.innerHeight-actions.length*36-20)),actions};event.preventDefault();event.stopPropagation();
}
export async function preferences(router) {
 const saved={...ui.prefs};
 const result=await dialog({title:'界面偏好',kind:'preferences',values:{...saved},fields:[
  {key:'density',label:'列表密度',type:'select',options:[['compact','紧凑 · 同屏显示更多文件'],['comfortable','舒适 · 更多阅读空间']]},
  {key:'layout',label:'文件显示方式',type:'select',options:[['list','列表'],['grid','网格']]},
  {key:'motion',label:'界面过渡动效',type:'checkbox'},
  {key:'pageSize',label:'每页项目',type:'select',options:[[20,'20'],[50,'50'],[100,'100']]}
 ],onSubmit:async values=>{ui.prefs=await api.send('/api/drive/preferences/ui',values,'PUT');return true;},onClose:()=>applyPrefs()});
 if(result){if(router&&ui.prefs.pageSize!==saved.pageSize)await router.replace({query:{...router.currentRoute.value.query,size:ui.prefs.pageSize,page:1}});changed();}
}

export async function logout(router) {
 if(busyUploads()&&!await confirm('退出登录？','退出将中断当前上传任务。'))return;
 const token=getStoredToken();
 try { await api.logout(); }
 finally { if(token===getStoredToken()){clearStoredSession();await router.replace('/login');} }
}
export async function transfers() {
 const history=await api.get('/api/drive/me/transfers');
 void dialog({title:'传输记录',kind:'transfers',description:'本次上传任务与最近成功上传记录。刷新后未完成的任务需要重新选择文件。',records:history.map(r=>({id:r.id,name:r.name,description:size(r.sizeBytes)+' · '+time(r.createdAt)+' · 上传成功'}))});
}
export function help() {
 void dialog({title:'使用说明',records:[
  {id:'search',name:'搜索',description:'Ctrl / ⌘ K 聚焦搜索。按名称搜索所有有权限的文件、文件夹与协作空间。'},
  {id:'files',name:'文件操作',description:'文件名打开内容；复选框选择；右键或更多菜单操作。表头切换排序，偏好中切换密度、主题及网格。'},
  {id:'share',name:'共享与复制',description:'共享权限由服务端核对。仅预览内容无法复制或下载，移动限原范围，复制可跨个人盘和可写空间。'},
  {id:'version',name:'历史版本',description:'内容覆盖及在线编辑会保留历史。文件拥有者及可写空间成员可以查看、下载，历史版本不提供恢复。'},
  {id:'capacity',name:'存储空间',description:'100GB 为参考容量，不限制上传；用量包括个人盘、回收站及历史版本。'},
  {id:'new',name:'新建文档',description:'暂不支持该功能。可以先在本地创建文档，再上传在线编辑。'}
 ]});
}
export async function createFolder(target=ui.context) {
 const result=await dialog({title:'新建文件夹',fields:[{key:'name',label:'文件夹名称',required:true,maxlength:255}],values:{name:''},
 onSubmit:v=>target.scopeType==='WORKSPACE'?api.createWorkspaceFolder(target.scopeId,{name:v.name.trim(),parentId:target.parentId}):api.createFolder({name:v.name.trim(),parentId:target.parentId})});
 if(result)changed();
}
export async function rename(items) {
 const many=items.length>1;
 const result=await dialog({title:many?'批量重命名':'重命名',fields:many?[{key:'baseName',label:'名称前缀',required:true},{key:'startNumber',label:'起始序号',type:'number',min:0},{key:'numberWidth',label:'序号位数',type:'number',min:1,max:8}]:[{key:'name',label:'名称',required:true}],values:many?{baseName:'文件',startNumber:1,numberWidth:2}:{name:items[0].name},
 onSubmit:v=>many?api.batchRenameDriveItems({itemIds:items.map(i=>i.id),baseName:v.baseName,startNumber:Number(v.startNumber),numberWidth:Number(v.numberWidth)}):api.renameDriveItem(items[0].id,{name:v.name})});
 if(result)batchResult(result);
}
export async function copyOrMove(items,copy=true) {
 const first=items[0];
 if(!copy&&items.some(i=>(i.scopeId||'')!==(first.scopeId||'')))throw new Error('移动只能在同一空间内进行，请改用复制到');
 const result=await dialog({title:copy?'复制到':'移动到',className:'destination-modal',description:'已选择 '+items.length+' 项',fields:[
 {key:'target',label:'目标文件夹',type:'folder',exclude:items.filter(i=>i.itemType==='folder').map(i=>i.id),fixedScope:copy?null:{scopeType:first.scopeType||'PERSONAL',scopeId:first.scopeId||''}},conflictField],values:{target:null,conflictStrategy:'keep_both'},
 onSubmit:v=>{if(!v.target)throw new Error('请选择目标目录');return copy?api.copyDriveItems({itemIds:items.map(i=>i.id),targetScopeType:v.target.scopeType,targetScopeId:v.target.scopeId,targetParentId:v.target.parentId,conflictStrategy:v.conflictStrategy}):api.moveDriveItems({itemIds:items.map(i=>i.id),targetParentId:v.target.parentId,conflictStrategy:v.conflictStrategy});}});
 if(result)batchResult(result);
}
export async function remove(items) {
 if(!await confirm('移入回收站？','将 '+items.length+' 个项目及其子项移入回收站，可以之后恢复。'))return;
 const succeeded=[],failed={};
 for(const item of items){try{await api.deleteDriveItem(item.id);succeeded.push(item.id);}catch(e){failed[item.id]=e.message;}}
 batchResult({totalSucceeded:succeeded.length,totalFailed:Object.keys(failed).length,failedItems:failed});
}
export async function restore(items) {batchResult(await api.restoreDriveItems({itemIds:items.map(i=>i.id)}));}
export async function purge(items) {if(await confirm('永久删除？','所选文件及其全部历史版本将被彻底删除，无法恢复。'))batchResult(await api.purgeTrashItems({itemIds:items.map(i=>i.id)}));}
export async function clearTrash() {if(await confirm('清空个人回收站？','所有已删除的个人文件及历史版本将被彻底删除。'))batchResult(await api.clearTrashItems());}
export async function favorite(item){await api.send('/api/drive/me/favorites/'+api.encode(item.id),undefined,item.favorite?'DELETE':'PUT');changed();}
export async function download(items) {
 if(items.length===1&&items[0].itemType==='file')await api.downloadDriveItem(items[0]);
 else await api.batchDownloadDriveItems(items.map(i=>i.id),'青山云盘文件.zip');
}
export async function openItem(item,router,edit=false,version=null) {
 if(item.deleted){await details(item);return;}
 if(item.itemType==='folder'||item.resultType==='workspace'){await router.push(itemLocation(item,true));return;}
 ui.preview={item,edit,version,id:Date.now()};
}
export async function details(item){
 ui.detail={...item,loading:true,error:'',versions:[]};
 try{const info=await api.get('/api/drive/items/'+api.encode(item.id)+'/details');if(ui.detail?.id!==item.id)return;
  ui.detail={...item,...info,loading:false,error:'',versions:[]};
  if(info.allowVersions)ui.detail.versions=await api.get('/api/drive/items/'+api.encode(item.id)+'/versions');
 }catch(e){if(ui.detail?.id===item.id){ui.detail.loading=false;ui.detail.error=e.message;}}
}
export async function share(items) {
 const summary=items.length===1?await api.getDriveShareSummary(items[0].id):null;
 const records=(summary?.collaborators||[]).map(c=>({id:c.shareId,name:c.targetName,description:c.targetType+' · '+c.permission+(c.expiresAt?' · '+time(c.expiresAt):''),actions:[
 {label:'修改',run:()=>editShare(c)},
 {label:'撤销',run:async()=>{if(await confirm('撤销共享？','该对象将失去此共享授予的访问权限。')){await api.revokeDriveShare(c.shareId);changed();const parent=ui.dialogs.at(-1);if(parent)parent.records=parent.records.filter(r=>r.id!==c.shareId);}}}
 ]}));
 const result=await dialog({title:'共享与权限',description:items.map(i=>i.name).join('、'),records,fields:[
 {key:'targetType',label:'共享对象类型',type:'select',options:[['person','人员'],['unit','组织'],['identity','身份']]},
 {key:'target',label:'添加共享对象',type:'org'},
 {key:'permission',label:'权限',type:'select',options:permissionOptions},
 {key:'expiresAt',label:'有效期（留空长期有效）',type:'date'},
 {key:'canReshare',label:'允许再次共享',type:'checkbox'}],values:{targetType:'person',target:null,permission:'read',expiresAt:'',canReshare:false},
 submitLabel:'添加共享',onSubmit:v=>{if(!v.target?.id)throw new Error('请选择真实共享对象');return api.createDriveShares({itemIds:items.map(i=>i.id),targets:[v.target],permission:v.permission,expiresAt:expires(v.expiresAt),canReshare:v.canReshare});}});
 if(result)batchResult(result);
}
export async function editShare(share) {
 const id=share.shareId||share.id;
 const r=await dialog({title:'修改共享权限',fields:[{key:'permission',label:'权限',type:'select',options:permissionOptions},{key:'expiresAt',label:'有效期',type:'date'},{key:'canReshare',label:'允许再次共享',type:'checkbox'}],values:{permission:share.permission||'read',expiresAt:dateValue(share.expiresAt),canReshare:Boolean(share.canReshare)},
 onSubmit:v=>api.updateDriveShare(id,{permission:v.permission,expiresAt:expires(v.expiresAt),canReshare:v.canReshare})});
 if(r)changed();
}
export function linkStatus(l){if(l.revoked)return '已停用';if(l.expiresAt&&new Date(l.expiresAt)<=new Date())return '已过期';if(l.downloadLimit>=0&&l.downloadCount>=l.downloadLimit)return '次数已用尽';return '有效';}
export async function linkDialog(item,link=null) {
 const result=await dialog({title:link?'修改外链':'创建外链',description:item.name||link.fileName,fields:[
 {key:'permission',label:'权限',type:'select',options:[['preview','仅预览'],['download','允许下载']]},
 {key:'password',label:link?'新提取码（留空保持原设置）':'提取码（留空无需提取码）',maxlength:64},
 ...(link?[{key:'clearPassword',label:'清除原提取码',type:'checkbox'}]:[]),
 {key:'expiresAt',label:'有效期（留空长期有效）',type:'date'},
 {key:'downloadLimit',label:'下载次数上限（留空不限）',type:'number',min:1}],
 values:{permission:link?.permission||'download',password:'',clearPassword:false,expiresAt:dateValue(link?.expiresAt),downloadLimit:link?.downloadLimit>0?link.downloadLimit:''},
 onSubmit:v=>{const body={permission:v.permission,expiresAt:expires(v.expiresAt),downloadLimit:v.downloadLimit===''?-1:Number(v.downloadLimit)};
 if(!link||v.password||v.clearPassword)body.password=v.clearPassword?'':v.password;
 return link?api.updateLink(link.id,body):api.createLink({...body,itemId:item.id});}});
 if(result){changed();if(!link)await dialog({title:'外链已创建',description:'请妥善保存链接及设置的提取码。',fields:[{key:'url',label:'访问链接'}],values:{url:result.shareUrl},submitLabel:'复制链接',onSubmit:async v=>{await copyText(v.url);return true;}});}
}
export async function copyText(text) {
 const url=new URL(text,window.location.origin).href;
 try {await navigator.clipboard.writeText(url);notify('链接已复制');}
 catch {await dialog({title:'复制链接',description:'当前浏览器不支持自动复制，请选中下方内容复制。',fields:[{key:'url',label:'链接'}],values:{url}});}
}
export async function linkLogs(link,page=1) {
 const response=await api.getLinkAccessLogs(link.id,page,20);
 await dialog({title:'外链访问日志',description:'第 '+page+' 页',records:(response.items||response.content||[]).map(r=>({id:r.id,name:r.action||r.accessType||'访问',description:[r.createdAt,r.ipAddress,r.result||r.success].filter(v=>v!=null).join(' · ')})),
 ...(response.hasNext?{submitLabel:'下一页',onSubmit:()=>true}:{})}).then(next=>{if(next)void linkLogs(link,page+1);});
}
export async function createSpace(router) {
 const result=await dialog({title:'创建协作空间',fields:[{key:'name',label:'空间名称',required:true,maxlength:255}],values:{name:''},onSubmit:v=>api.createWorkspace({name:v.name})});
 if(result){changed();await router.push('/workspaces/'+result.id);}
}
export async function renameSpace(space){const r=await dialog({title:'重命名空间',fields:[{key:'name',label:'空间名称',required:true}],values:{name:space.name},onSubmit:v=>api.updateWorkspace(space.id,{name:v.name})});if(r)changed();}
export async function archiveSpace(space,router){if(await confirm('归档空间？','归档后停止访问此空间，文件保留。')){await api.deleteWorkspace(space.id);changed();await router.push('/workspaces');}}
export async function members(space) {
 const records=await api.listWorkspaceMembers(space.id),myKey=getSessionPersonKey(ui.session);
 const renderRecords=list=>list.map(m=>({id:m.id,name:m.personName||m.personKey||m.personOaId,description:({OWNER:'所有者',ADMIN:'管理员',MEMBER:'成员',VIEWER:'只读'})[m.role]||m.role,actions:[
 {label:'角色',disabled:!canChangeMemberRole({myRole:space.myRole,targetRole:m.role}),run:async()=>{const options=[['ADMIN','管理员'],['MEMBER','成员'],['VIEWER','只读']];if(canPromoteToOwner({myRole:space.myRole,targetRole:m.role})||m.role==='OWNER')options.unshift(['OWNER','所有者']);
 const r=await dialog({title:'调整成员角色',fields:[{key:'role',label:'角色',type:'select',options}],values:{role:m.role},onSubmit:v=>api.updateWorkspaceMember(space.id,m.id,{role:v.role})});if(r){changed();const parent=ui.dialogs.at(-1);if(parent)parent.records=renderRecords(await api.listWorkspaceMembers(space.id));}}},
 {label:'移除',disabled:!canRemoveMember({myRole:space.myRole,targetRole:m.role,targetPersonKey:m.personKey||m.personOaId,myPersonKey:myKey}),run:async()=>{if(await confirm('移除成员？','该成员将无法继续访问空间文件。')){await api.removeWorkspaceMember(space.id,m.id);changed();const parent=ui.dialogs.at(-1);if(parent)parent.records=renderRecords(await api.listWorkspaceMembers(space.id));}}}
 ]}));
 const result=await dialog({title:'空间成员 · '+space.name,records:renderRecords(records),
 fields:canManageMembers(space.myRole)?[{key:'person',label:'添加人员',type:'org'},{key:'role',label:'角色',type:'select',options:[['MEMBER','成员'],['VIEWER','只读'],['ADMIN','管理员'],...(space.myRole==='OWNER'?[['OWNER','所有者']]:[])]}]:[],
 values:{person:null,role:'MEMBER'},...(canManageMembers(space.myRole)?{submitLabel:'添加成员',onSubmit:v=>{if(!v.person)throw new Error('请选择人员');return api.addWorkspaceMember(space.id,{personKey:v.person.id,personName:v.person.name,role:v.role});}}:{})});
 if(result)changed();
}
export function fileActions(item,router) {
 if(item.resultType==='workspace')return [{label:'打开空间',icon:'people',run:()=>router.push('/workspaces/'+item.workspaceId)}];
 if(item.deleted)return [{label:'查看详情',icon:'info',run:()=>details(item)},{label:'恢复',icon:'restore',run:()=>restore([item])},{label:'永久删除',icon:'trash',danger:true,run:()=>purge([item])}];
 const actions=[{label:item.itemType==='folder'?'打开文件夹':'打开预览',icon:'file',run:()=>openItem(item,router)},{label:'查看详情',icon:'info',run:()=>details(item)},{label:item.favorite?'取消收藏':'收藏文件',icon:'star',run:()=>favorite(item)},{label:'打开所在位置',icon:'folder',run:()=>router.push(itemLocation(item))}];
 if(item.allowEdit&&/\.(doc|docx|xls|xlsx|ppt|pptx)$/i.test(item.name))actions.push({label:'在线编辑',icon:'edit',run:()=>openItem(item,router,true)});
 if(item.allowCopy)actions.push({label:'复制到…',icon:'copy',run:()=>copyOrMove([item])});
 if(item.allowDownload||item.itemType==='folder'&&item.allowCopy)actions.push({label:'下载',icon:'download',run:()=>download([item])});
 if(item.allowManage){actions.push({label:'共享与权限',icon:'shared',run:()=>share([item])});
 if(item.itemType==='file')actions.push({label:'创建外链',icon:'globe',run:()=>linkDialog(item)});
 actions.push({label:'重命名',icon:'edit',run:()=>rename([item])},{label:'移动到…',icon:'move',run:()=>copyOrMove([item],false)},{label:'移入回收站',icon:'trash',danger:true,run:()=>remove([item])});}
 return actions;
}
