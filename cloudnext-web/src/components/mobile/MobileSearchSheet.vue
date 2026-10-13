<template>
  <Teleport to="body">
    <transition name="m-search-fade">
      <div v-if="open" class="m-search-sheet" role="dialog" aria-modal="true" aria-label="搜索">
        <header class="m-search-head">
          <button class="m-search-back m-tap" type="button" aria-label="返回" @click="onClose">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="19" y1="12" x2="5" y2="12" />
              <polyline points="12 19 5 12 12 5" />
            </svg>
          </button>
          <label class="m-search-input-wrap">
            <svg class="m-search-input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="11" cy="11" r="7" />
              <line x1="16.5" y1="16.5" x2="21" y2="21" />
            </svg>
            <input
              ref="inputRef"
              v-model.trim="keyword"
              class="m-search-input"
              type="search"
              :placeholder="placeholder"
              enterkeyhint="search"
              autocomplete="off"
            />
            <button
              v-if="keyword"
              class="m-search-clear m-tap"
              type="button"
              aria-label="清除"
              @click="onClear"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 10.59 17.59 5 19 6.41 13.41 12 19 17.59 17.59 19 12 13.41 6.41 19 5 17.59 10.59 12 5 6.41 6.41 5 12 10.59z" />
              </svg>
            </button>
          </label>
        </header>

        <div class="m-search-chips">
          <button
            v-for="opt in fileTypeOptions"
            :key="opt.key"
            type="button"
            class="m-search-chip m-tap"
            :class="{ 'is-active': activeFileType === opt.key }"
            @click="activeFileType = opt.key"
          >
            {{ opt.label }}
          </button>
        </div>

        <div class="m-search-body">
          <div v-if="!keyword" class="m-search-tips">
            <h3>搜索文件、文件夹</h3>
            <p>输入关键词，按类型 chip 快速筛选；结果会即时在下方展示。</p>
          </div>
          <div v-else-if="loading" class="m-search-state">搜索中…</div>
          <div v-else-if="errorText" class="m-search-state m-search-state-error">{{ errorText }}</div>
          <div v-else-if="!items.length" class="m-search-state">未找到匹配「{{ keyword }}」的项目。</div>
          <div v-else class="m-search-results">
            <MobileFileCard
              v-for="item in items"
              :key="item.id"
              :item="item"
              :selected="false"
              :selecting="false"
              @click="onPickItem(item)"
              @more="onPickItem(item)"
            />
            <div v-if="hasMore" class="m-search-more">
              <button class="m-tap m-search-more-btn" type="button" :disabled="loading" @click="loadMore">加载更多</button>
            </div>
            <div v-else class="m-search-end">已加载全部</div>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { searchFiles } from '../../api/cloudnext';
import { formatFileSize, formatTime } from '../../utils/formatters';
import MobileFileCard from './MobileFileCard.vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  placeholder: { type: String, default: '搜索文件、文件夹' }
});

const emit = defineEmits(['update:open', 'submit']);

const fileTypeOptions = [
  { key: '', label: '全部' },
  { key: 'word', label: '文档' },
  { key: 'excel', label: '表格' },
  { key: 'ppt', label: '演示' },
  { key: 'pdf', label: 'PDF' },
  { key: 'image', label: '图片' },
  { key: 'video', label: '视频' }
];

const router = useRouter();
const inputRef = ref(null);
const keyword = ref('');
const activeFileType = ref('');
const items = ref([]);
const loading = ref(false);
const errorText = ref('');
const page = ref(1);
const pageSize = 30;
const totalPages = ref(0);

const hasMore = computed(() => page.value < totalPages.value);

let debounceTimer = null;

const enrichItem = (raw) => ({
  ...raw,
  modifiedLabel: raw.updatedAt ? formatTime(raw.updatedAt) : '',
  sizeLabel: raw.itemType === 'folder' ? '' : formatFileSize(raw.sizeBytes || 0)
});

const runSearch = async (reset = true) => {
  const kw = keyword.value.trim();
  if (!kw) {
    items.value = [];
    totalPages.value = 0;
    return;
  }
  if (reset) {
    page.value = 1;
    items.value = [];
  }
  loading.value = true;
  errorText.value = '';
  try {
    const resp = await searchFiles({
      scope: 'personal',
      keyword: kw,
      fileType: activeFileType.value || '',
      page: page.value,
      size: pageSize
    });
    const list = Array.isArray(resp?.items) ? resp.items : [];
    if (reset) {
      items.value = list.map(enrichItem);
    } else {
      items.value = items.value.concat(list.map(enrichItem));
    }
    totalPages.value = Number(resp?.totalPages || 0);
  } catch (e) {
    if (e?.status === 404 || /404/.test(e?.message || '')) {
      errorText.value = '高级搜索未启用或后端不可用，请联系管理员';
    } else if (e?.status === 401) {
      errorText.value = '会话已过期，请重新登录';
    } else {
      errorText.value = e?.message || '搜索失败';
    }
  } finally {
    loading.value = false;
  }
};

const scheduleSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    void runSearch(true);
  }, 300);
};

const loadMore = () => {
  if (!hasMore.value || loading.value) return;
  page.value += 1;
  void runSearch(false);
};

const onClose = () => {
  emit('update:open', false);
};

const onClear = () => {
  keyword.value = '';
  items.value = [];
  totalPages.value = 0;
  nextTick(() => inputRef.value?.focus());
};

const onPickItem = (item) => {
  if (!item) return;
  emit('submit', item);
  if (item.itemType === 'folder') {
    router.push({ name: 'my-files', query: { parentId: item.id } });
  } else {
    // 文件：跳到 my-files 父目录，让用户在原位置看到上下文（更稳的方式是触发 preview，但需要 session 等）
    router.push({ name: 'my-files', query: { parentId: item.parentId || '' } });
  }
  emit('update:open', false);
};

watch(
  () => props.open,
  (next) => {
    if (next) {
      keyword.value = '';
      activeFileType.value = '';
      items.value = [];
      page.value = 1;
      totalPages.value = 0;
      errorText.value = '';
      nextTick(() => inputRef.value?.focus());
    }
  }
);

watch([keyword, activeFileType], () => {
  scheduleSearch();
});
</script>

<style scoped>
.m-search-sheet {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
  padding-top: var(--m-safe-top);
}

.m-search-fade-enter-active,
.m-search-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.m-search-fade-enter-from,
.m-search-fade-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

.m-search-head {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  padding: var(--m-sp-2) var(--m-sp-2);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.m-search-back {
  width: var(--m-touch-h);
  height: var(--m-touch-h);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-primary);
  border-radius: var(--radius-md);
  flex-shrink: 0;
}

.m-search-back:active {
  background: var(--bg-nav-hover);
}

.m-search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  height: 38px;
  padding: 0 var(--m-sp-3);
  background: var(--bg-panel-soft);
  border-radius: 19px;
  min-width: 0;
}

.m-search-input-icon {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.m-search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: var(--m-fs-body);
}

.m-search-input::placeholder {
  color: var(--text-tertiary);
}

.m-search-clear {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.16);
  color: #fff;
  border-radius: 50%;
  flex-shrink: 0;
}

.m-search-chips {
  display: flex;
  gap: var(--m-sp-2);
  padding: var(--m-sp-2) var(--m-sp-3);
  overflow-x: auto;
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
  scrollbar-width: none;
}

.m-search-chips::-webkit-scrollbar {
  display: none;
}

.m-search-chip {
  height: 30px;
  padding: 0 12px;
  font-size: var(--m-fs-meta);
  background: var(--bg-panel-soft);
  color: var(--text-secondary);
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;
  border: 1px solid transparent;
}

.m-search-chip.is-active {
  background: var(--bg-primary-soft);
  color: var(--bg-primary);
  border-color: rgba(47, 107, 255, 0.2);
  font-weight: 500;
}

.m-search-body {
  flex: 1;
  overflow-y: auto;
  background: var(--bg-page);
}

.m-search-tips {
  padding: var(--m-sp-4);
  color: var(--text-secondary);
}

.m-search-tips h3 {
  margin: 0 0 var(--m-sp-2);
  font-size: var(--m-fs-section);
  color: var(--text-primary);
}

.m-search-tips p {
  margin: 0 0 var(--m-sp-1);
  font-size: var(--m-fs-body);
}

.m-search-state {
  padding: var(--m-sp-5) var(--m-sp-4);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--m-fs-body);
}

.m-search-state-error {
  color: #b42318;
}

.m-search-results {
  background: #fff;
}

.m-search-more {
  padding: var(--m-sp-3);
  text-align: center;
}

.m-search-more-btn {
  background: transparent;
  color: var(--bg-primary);
  font-size: var(--m-fs-body);
  padding: var(--m-sp-2) var(--m-sp-4);
}

.m-search-end {
  padding: var(--m-sp-3);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--m-fs-meta);
}
</style>
