import { computed, ref } from 'vue';
import { nativeMobile } from '../embedded/context.js';

const MOBILE_MAX = 559;
const TABLET_MAX = 959;

const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1280);
function syncExperience() {
  if (typeof document !== 'undefined') document.documentElement.dataset.experience = nativeMobile || width.value <= MOBILE_MAX ? 'mobile' : 'desktop';
}

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
      syncExperience();
    },
    { passive: true }
  );
}

export function useBreakpoint() {
  ensureListener();
  syncExperience();
  const isMobile = computed(() => nativeMobile || width.value <= MOBILE_MAX);
  const isTablet = computed(() => !nativeMobile && width.value > MOBILE_MAX && width.value <= TABLET_MAX);
  const isDesktop = computed(() => !nativeMobile && width.value > TABLET_MAX);
  return { width, isMobile, isTablet, isDesktop };
}
