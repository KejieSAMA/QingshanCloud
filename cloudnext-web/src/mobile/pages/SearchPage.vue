<template>
  <section class="search-page">
    <form class="search-form" role="search" @submit.prevent="submit">
      <label class="search-input"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg>
        <input v-model="filters.keyword" type="search" maxlength="100" enterkeyhint="search" autofocus
          aria-label="搜索名称" placeholder="搜索文件、文件夹或协作空间名称" />
      </label>
      <button class="primary-button" type="submit">搜索</button>
    </form>
    <div class="search-filters">
      <label>范围<select v-model="filters.scope"><option value="all">全部可访问内容</option><option value="personal">我的文件</option><option value="shared">收到的共享</option><option value="workspace">协作空间</option></select></label>
      <label>类别<select v-model="filters.itemType"><option value="all">全部</option><option value="file">文件</option><option value="folder">文件夹</option><option value="workspace">空间</option></select></label>
      <label>类型<select v-model="filters.fileType"><option value="">全部类型</option><option v-for="(label, key) in fileTypes" :key="key" :value="key">{{ label }}</option></select></label>
      <label>人员<select v-model="filters.owner"><option value="">全部人员</option><option v-for="owner in owners" :key="owner.id" :value="owner.id">{{ owner.name || owner.id }}</option><option v-if="filters.owner && !owners.some(o => o.id === filters.owner)" :value="filters.owner">已选人员</option></select></label>
      <label>修改日期<input v-model="filters.from" type="date" aria-label="修改开始日期" /></label>
      <label>至<input v-model="filters.to" type="date" aria-label="修改结束日期" /></label>
      <button class="clear-filters" type="button" @click="resetFilters">重置筛选</button>
    </div>
    <div class="search-summary">
      <span aria-live="polite">{{ loading ? '搜索中…' : filters.keyword.trim() ? `找到 ${total} 项` : '搜索你有权限访问的名称' }}</span>
      <select v-model="sortValue" aria-label="搜索结果排序"><option value="relevance:desc">相关程度</option><option value="name:asc">名称 ↑</option><option value="name:desc">名称 ↓</option><option value="updatedAt:desc">修改时间 ↓</option><option value="updatedAt:asc">修改时间 ↑</option></select>
    </div>
    <div v-if="error" class="search-state search-error" role="alert">{{ error }} <button type="button" @click="load">重试</button></div>
    <p v-if="actionError" class="search-error" role="alert">{{ actionError }}</p>
    <div v-if="!filters.keyword.trim()" class="search-state">输入名称关键词，例如“档案”或“2026 合同”。</div>
    <div v-else-if="!loading && !error && !items.length" class="search-state">未找到匹配项目，可更换关键词或放宽筛选条件。</div>
    <div v-if="items.length" class="search-results" :aria-busy="loading">
      <article v-for="item in items" :key="`${item.resultType}:${item.id}`" class="search-result">
        <FileTypeIcon :kind="item.resultType !== 'file' ? 'folder' : item.fileType" :from-name="item.name" :content-type="item.contentType" :size="32" />
        <div class="search-result-copy">
          <button type="button" class="result-name" @click="openItem(item)"><template v-for="(part, i) in highlightSearchName(item.name, filters.keyword)" :key="i"><mark v-if="part.match">{{ part.text }}</mark><span v-else>{{ part.text }}</span></template></button>
          <div class="result-path"><span class="source-tag">{{ sourceLabels[item.source] }}</span><span :title="item.displayPath">{{ item.displayPath }}</span></div>
          <div class="result-meta"><span>{{ item.ownerName || '—' }}</span><span>{{ formatTime(item.updatedAt) }}</span><span v-if="item.resultType === 'file'">{{ formatFileSize(item.sizeBytes) }}</span></div>
        </div>
        <div class="result-actions">
          <button v-if="item.allowPreview" type="button" @click="runAction(() => previewDriveItem(item))">预览</button>
          <button v-if="item.allowDownload" type="button" @click="runAction(() => downloadDriveItem(item))">下载</button>
          <button v-if="item.resultType !== 'workspace' && item.allowCopy" type="button" @click="openCopy(item)">复制到</button>
          <button v-if="item.allowLocate" type="button" @click="router.push(searchResultLocation(item))">{{ item.resultType === 'workspace' ? '进入空间' : '所在位置' }}</button>
        </div>
      </article>
    </div>
    <footer v-if="filters.keyword.trim() && totalPages > 0" class="search-pagination">
      <button v-if="page < totalPages" type="button" :disabled="loading" @click="commit(page + 1, true)">加载更多</button><span v-else>已加载全部</span>
      <button type="button" :disabled="loading || page <= 1" @click="commit(page - 1, true)">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button type="button" :disabled="loading || page >= totalPages" @click="commit(page + 1, true)">下一页</button>
    </footer>
    <DriveCopyDialog :open="copyDialogOpen" :items="copyDialogItems" @close="closeCopy" @copied="closeCopy" />
  </section>
</template>
<script setup>
const mobileOnly = { value: true };

import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSearchResults } from '../../composables/drive/useSearchResults';
import { highlightSearchName, searchResultLocation } from '../../composables/drive/searchResults';
import { previewDriveItem, downloadDriveItem } from '../../api/cloudnext';
import { formatFileSize, formatTime } from '../../utils/formatters';
import FileTypeIcon from '../../components/drive/FileTypeIcon.vue';
import DriveCopyDialog from '../../components/drive/DriveCopyDialog.vue';
const router = useRouter(), route = useRoute();

const { filters, items, owners, total, totalPages, loading, error, page, commit, load, submit } = useSearchResults(route, router, mobileOnly);
const sourceLabels = { personal: '我的文件', shared: '收到的共享', workspace: '协作空间' };
const fileTypes = { word: '文档', excel: '表格', ppt: '演示', pdf: 'PDF', image: '图片', video: '视频', audio: '音频', archive: '压缩包', other: '其他' };
const actionError = ref('');
const copyDialogOpen = ref(false);
const copyDialogItems = ref([]);
const sortValue = computed({ get: () => `${filters.sortBy}:${filters.sortOrder}`, set: value => { [filters.sortBy, filters.sortOrder] = value.split(':'); } });
const resetFilters = () => Object.assign(filters, { scope: 'all', itemType: 'all', fileType: '', owner: '', from: '', to: '' });
const runAction = async (action) => { actionError.value = ''; try { await action(); } catch (e) { actionError.value = e.message || '操作失败，请重试'; } };
const openItem = item => item.resultType === 'file' ? runAction(() => previewDriveItem(item)) : router.push(searchResultLocation(item, true));
const openCopy = (item) => { copyDialogItems.value = [item]; copyDialogOpen.value = true; };
const closeCopy = () => { copyDialogOpen.value = false; copyDialogItems.value = []; };
</script>
<style scoped>

.search-page { display: grid; gap: 16px; }
.search-form { display: flex; gap: 10px; }
.search-input { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; background: var(--bg-panel); border: 1px solid var(--border-color); border-radius: 10px; padding: 0 12px; }
.search-input input { width: 100%; min-width: 0; height: 44px; border: 0; background: transparent; color: var(--text-primary); }
.search-input svg { flex-shrink: 0; color: var(--text-secondary); }
.search-input:focus-within { border-color: var(--bg-primary); box-shadow: 0 0 0 2px var(--bg-primary-soft); }
.search-input input:focus-visible { outline: none; }
.search-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.search-filters label { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }
select,
input[type=date] { min-height: 34px; max-width: 160px; padding: 0 8px; border: 1px solid var(--border-color); border-radius: 7px; background: var(--bg-panel); color: var(--text-primary); font: inherit; font-size: 12px; }
.clear-filters,
.result-actions button,
.search-pagination button,
.search-error button { color: var(--bg-primary); border: 0; background: transparent; padding: 8px; font-size: 13px; }
.search-summary { display: flex; align-items: center; justify-content: space-between; color: var(--text-secondary); font-size: 13px; }
.search-results { border: 1px solid var(--border-color); border-radius: 12px; background: var(--bg-panel); }
.search-result { display: flex; align-items: center; gap: 12px; padding: 16px; min-width: 0; }
.search-result + .search-result { border-top: 1px solid var(--border-color); }
.search-result-copy { min-width: 0; flex: 1; }
.result-name { display: block; max-width: 100%; padding: 0; border: 0; background: transparent; color: var(--text-primary); font: inherit; text-align: left; overflow-wrap: anywhere; }
mark { color: inherit; background: #fff0b8; border-radius: 2px; }
.result-path,
.result-meta { display: flex; gap: 10px; align-items: center; margin-top: 7px; color: var(--text-tertiary); font-size: 12px; }
.result-path > span:last-child { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.source-tag { flex-shrink: 0; color: var(--text-secondary); }
.result-actions { display: flex; flex-wrap: wrap; }
.search-state { padding: 48px 16px; text-align: center; color: var(--text-secondary); }
.search-error { color: #b42318; }
.search-pagination { display: flex; justify-content: center; align-items: center; gap: 16px; font-size: 13px; color: var(--text-secondary); }
button { cursor: pointer; } button:disabled { opacity: .45; cursor: default; }
button:focus-visible,
input:focus-visible,
select:focus-visible { outline: 2px solid var(--bg-primary); outline-offset: 2px; }
@media(max-width: 768px) {
  .search-page { padding: 12px; gap: 12px; }
  .search-form .primary-button { padding: 0 12px; }
  .search-filters { gap: 8px; }
  .search-filters label { flex-wrap: wrap; }
  .search-result { flex-wrap: wrap; align-items: flex-start; padding: 12px; }
  .search-result-copy { flex-basis: calc(100% - 48px); }
  .result-actions { width: 100%; justify-content: flex-end; gap: 8px; }
  .result-actions button { min-height: 36px; padding: 4px 8px; }
  .result-meta { flex-wrap: wrap; gap: 6px; }
}

</style>
