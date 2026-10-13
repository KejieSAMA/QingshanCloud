import test from 'node:test';
import assert from 'node:assert/strict';
import { itemLocation, directoryBreadcrumbs, breadcrumbLocation } from '../navigation.js';
import { sanitizeReturnPath, navigateAfterLogin, loginRouteFor, normalizeEntryLocation } from '../../authNavigation.js';
test('personal, workspace and shared locations retain their scopes', () => {
 assert.deepEqual(itemLocation({id:'f',itemType:'folder',source:'personal'},true),{path:'/my-files',query:{parentId:'f'}});
 assert.deepEqual(itemLocation({id:'f',itemType:'folder',source:'workspace',workspaceId:'w'},true),{path:'/workspaces/w',query:{parentId:'f'}});
 assert.deepEqual(itemLocation({id:'f',itemType:'file',source:'shared',shareId:'s',locationParentId:'r'}),{path:'/shared',query:{tab:'incoming',shareId:'s',parentId:'r'}});
});
test('workspace breadcrumb replaces the unnamed internal root with the workspace name', () => {
 const workspace = { id:'w', rootItemId:'root-w', name:'002行政人事' };
 const path = [{id:'root-w',name:''},{id:'archive',name:'档案室资料'}];
 const crumbs = directoryBreadcrumbs(path,workspace);
 assert.deepEqual(crumbs.map(c=>c.name),['002行政人事','档案室资料']);
 assert.deepEqual(crumbs.map(c=>c.parentId),['','archive']);
 assert.deepEqual(path,[{id:'root-w',name:''},{id:'archive',name:'档案室资料'}]);
});
test('workspace root is shown once even before loading a path or after refreshing the root URL', () => {
 const workspace = { id:'w', rootItemId:'root-w', name:'空间名称' };
 for(const path of [[],[{id:'root-w',name:''}],null]){
  assert.deepEqual(directoryBreadcrumbs(path,workspace),[{id:'root-w',name:'空间名称',parentId:''}]);
 }
 assert.equal(directoryBreadcrumbs([{id:'root-w',name:'旧空间名称'}],workspace)[0].name,'空间名称');
});
test('returning to space root or a parent folder stays in the space and resets pagination', () => {
 const route = {path:'/workspaces/w',query:{parentId:'deep',page:3,size:50,sortBy:'name',sortOrder:'asc'}};
 const crumbs = directoryBreadcrumbs([{id:'root-w',name:''},{id:'archive',name:'档案室资料'},{id:'deep',name:'红头文件'}],
  {id:'w',rootItemId:'root-w',name:'002行政人事'});
 assert.deepEqual(breadcrumbLocation(route,crumbs[0]),{path:'/workspaces/w',query:{page:1,size:50,sortBy:'name',sortOrder:'asc'}});
 assert.deepEqual(breadcrumbLocation(route,crumbs[1]),{path:'/workspaces/w',query:{parentId:'archive',page:1,size:50,sortBy:'name',sortOrder:'asc'}});
 assert.equal(route.query.parentId,'deep');
});
test('personal and shared breadcrumbs do not acquire a workspace root', () => {
 const path=[{id:'allowed',name:'共享入口'},{id:'child',name:'子目录'}];
 const crumbs=directoryBreadcrumbs(path);
 assert.deepEqual(crumbs,path.map(c=>({...c,parentId:c.id})));
 assert.deepEqual(breadcrumbLocation({path:'/shared',query:{shareId:'s',parentId:'child',tab:'incoming'}},crumbs[0]),
  {path:'/shared',query:{shareId:'s',parentId:'allowed',tab:'incoming',page:1}});
});
test('login returns including old HTML bookmarks always use SPA routing', async () => {
 const calls=[],router={replace:p=>calls.push(p)};
 await navigateAfterLogin(router,'/next.html#/shared?parentId=f&shareId=s');
 await navigateAfterLogin(router,'/my-files?parentId=f');
 await navigateAfterLogin(router);
 assert.deepEqual(calls,['/shared?parentId=f&shareId=s','/my-files?parentId=f','/']);
});
test('reject external, encoded external, control, and recursive login destinations', () => {
 for(const bad of ['//evil.test','https://evil.test/x','/login','/login?returnPath=/login','/%6cogin','/next.html#/login','/next.html#//evil.test','/%2f%2fevil.test','/%5cevil','/x/../login','/\\\\evil','/a\u0000']){
  assert.equal(sanitizeReturnPath(bad),'/',bad);
 }
 assert.equal(sanitizeReturnPath('/next.html#/search?keyword=%E6%A1%A3%E6%A1%88&page=2'),'/search?keyword=%E6%A1%A3%E6%A1%88&page=2');
 assert.deepEqual(loginRouteFor('/my-files?parentId=f'),{name:'login',query:{returnPath:'/my-files?parentId=f'}});
});
test('legacy entry is normalized in place without document navigation',()=>{
 const calls=[],history={state:{keep:true},replaceState:(...args)=>calls.push(args)};
 normalizeEntryLocation({pathname:'/next.html',search:'',hash:'#/workspaces/w?parentId=f'},history);
 assert.deepEqual(calls,[[{keep:true},'','/workspaces/w?parentId=f']]);
 normalizeEntryLocation({pathname:'/my-files',search:'',hash:''},history);
 assert.equal(calls.length,1);
});
