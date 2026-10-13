import { computed, onUnmounted, reactive, ref, watch } from 'vue';
import { searchAllItems, isAbortError } from '../../api/cloudnext';
import { searchDateBoundary } from './searchResults';

const defaults = { keyword: '', scope: 'all', itemType: 'all', fileType: '', owner: '', from: '', to: '', sortBy: 'relevance', sortOrder: 'desc' };
export function useSearchResults(route, router, isMobile) {
  const filters = reactive({ ...defaults });
  const items = ref([]), owners = ref([]), total = ref(0), totalPages = ref(0);
  const loading = ref(false), error = ref('');
  const page = computed(() => Math.max(1, Number.parseInt(route.query.page, 10) || 1));
  let timer, controller, generation = 0, syncing = false;
  const commit = (nextPage = 1, push = false) => {
    clearTimeout(timer);
    const query = {};
    for (const [key, fallback] of Object.entries(defaults)) {
      const value = String(filters[key]).trim();
      if (value !== fallback) query[key] = value;
    }
    if (nextPage > 1) query.page = String(nextPage);
    return router[push ? 'push' : 'replace']({ name: 'search', query });
  };
  const load = async () => {
    const request = ++generation;
    controller?.abort(); controller = new AbortController();
    error.value = '';
    if (!filters.keyword.trim()) { items.value = []; total.value = 0; totalPages.value = 0; owners.value = []; loading.value = false; return; }
    loading.value = true;
    try {
      if (filters.from && filters.to && filters.from > filters.to) throw new Error('开始日期不能晚于结束日期');
      const params = { ...filters, updatedFrom: searchDateBoundary(filters.from), updatedTo: searchDateBoundary(filters.to, true), size: 30 };
      delete params.from; delete params.to;
      const target = page.value;
      let response;
      const results = [];
      // Rebuild up to the URL page on mobile so back/refresh restores loaded results.
      for (let current = isMobile.value ? 1 : target; current <= target; current++) {
        response = await searchAllItems({ ...params, page: current }, { signal: controller.signal });
        if (request !== generation) return;
        results.push(...response.items);
        if (!response.hasNext) break;
      }
      items.value = [...new Map(results.map(item => [`${item.resultType}:${item.id}`, item])).values()];
      owners.value = response.owners || [];
      total.value = response.total; totalPages.value = response.totalPages;
      if (response.page !== target) void commit(response.page);
    } catch (e) {
      if (request !== generation || isAbortError(e)) return;
      error.value = e.status === 404 ? '搜索服务尚未部署或未启用，请联系管理员。' : e.message || '搜索失败，请重试';
    } finally { if (request === generation) loading.value = false; }
  };
  watch(() => route.query, async () => {
    clearTimeout(timer);
    syncing = true;
    for (const [key, fallback] of Object.entries(defaults)) filters[key] = typeof route.query[key] === 'string' ? route.query[key] : fallback;
    syncing = false;
    await load();
  }, { immediate: true });
  watch(filters, () => {
    if (syncing) return;
    // Invalidate immediately, before the debounce, so stale results cannot replace newer input.
    generation++; controller?.abort(); loading.value = false; error.value = ''; items.value = []; total.value = 0; totalPages.value = 0;
    clearTimeout(timer); timer = setTimeout(() => void commit(), 300);
  }, { flush: 'sync' });
  watch(isMobile, () => void load());
  const submit = async () => { await commit(); await load(); };
  onUnmounted(() => { clearTimeout(timer); generation++; controller?.abort(); });
  return { filters, items, owners, total, totalPages, loading, error, page, commit, load, submit };
}
