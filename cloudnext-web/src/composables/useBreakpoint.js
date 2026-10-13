import { computed, ref } from 'vue';

const MOBILE_MAX = 559;
const TABLET_MAX = 959;

const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1280);

let listenerAttached = false;
function ensureListener() {
  if (listenerAttached || typeof window === 'undefined') {
    return;
  }
  listenerAttached = true;
  window.addEventListener(
    'resize',
    () => {
      width.value = window.innerWidth;
    },
    { passive: true }
  );
}

export function useBreakpoint() {
  ensureListener();
  const isMobile = computed(() => width.value <= MOBILE_MAX);
  const isTablet = computed(() => width.value > MOBILE_MAX && width.value <= TABLET_MAX);
  const isDesktop = computed(() => width.value > TABLET_MAX);
  return { width, isMobile, isTablet, isDesktop };
}
