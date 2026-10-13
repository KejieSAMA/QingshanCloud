import { afterEach, describe, expect, it, vi } from 'vitest';
import { effectScope, nextTick, reactive, ref } from 'vue';
vi.mock('../api/cloudnext', () => ({ searchAllItems: vi.fn(), isAbortError: e => e.name === 'AbortError' }));
import { searchAllItems } from '../api/cloudnext';
import { useSearchResults } from '../composables/drive/useSearchResults';
const response = (name, page = 1, hasNext = false) => ({ items: [{ id: name, name, resultType: 'file' }], owners: [], page, total: hasNext ? 31 : 1, totalPages: hasNext ? 2 : page, hasNext });
const scopes = [];
function setup(query = {}, mobile = false) {
  const route = reactive({ query });
  const navigate = vi.fn(async value => { route.query = value.query; await nextTick(); });
  const scope = effectScope(); scopes.push(scope);
  const state = scope.run(() => useSearchResults(route, { replace: navigate, push: navigate }, ref(mobile)));
  return { route, state, navigate };
}
afterEach(() => { scopes.forEach(s => s.stop()); scopes.length = 0; vi.useRealTimers(); vi.resetAllMocks(); });
describe('unified search requests', () => {
  it('does not query blank input', async () => {
    setup(); await nextTick(); expect(searchAllItems).not.toHaveBeenCalled();
  });
  it('discards stale responses while the new query is debouncing', async () => {
    vi.useFakeTimers(); let finish;
    searchAllItems.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; })).mockResolvedValue(response('new'));
    const { state } = setup({ keyword: 'old' });
    state.filters.keyword = 'new'; finish(response('old')); await nextTick();
    expect(state.items.value).toEqual([]);
    await vi.advanceTimersByTimeAsync(350);
    expect(state.items.value[0].name).toBe('new');
    expect(searchAllItems.mock.calls.at(-1)[0]).toMatchObject({ keyword: 'new', scope: 'all', size: 30, page: 1 });
  });
  it('restores mobile loaded pages from URL', async () => {
    searchAllItems.mockImplementation(async q => response(`page${q.page}`, q.page, q.page === 1));
    const { state } = setup({ keyword: 'page', page: '2' }, true);
    await state.load();
    expect(state.items.value.map(x => x.name)).toEqual(['page1', 'page2']);
  });
  it('shows an unavailable API as an error, and can retry', async () => {
    searchAllItems.mockRejectedValue(Object.assign(new Error('missing'), { status: 404 }));
    const { state } = setup({ keyword: '合同' });
    await state.load(); expect(state.error.value).toContain('尚未部署');
    searchAllItems.mockResolvedValue(response('合同')); await state.load();
    expect(state.error.value).toBe(''); expect(state.items.value).toHaveLength(1);
  });
});
