<template>
  <Teleport to="body">
    <transition name="m-drawer-fade">
      <div v-if="open" class="m-drawer-mask" @click="$emit('close')"></div>
    </transition>
    <aside class="m-drawer" :class="{ 'is-open': open }" role="dialog" aria-modal="true" aria-label="主导航">
      <div class="m-drawer-header">
        <div class="m-drawer-avatar">{{ avatarText }}</div>
        <div class="m-drawer-user">
          <strong>{{ displayName }}</strong>
          <span>{{ unitName }}</span>
        </div>
      </div>
      <nav class="m-drawer-nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="m-drawer-item"
          @click="$emit('close')"
        >
          <span class="m-drawer-item-icon">
            <AppIcon :name="item.icon" :size="18" />
          </span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="m-drawer-footer">
        <RouterLink to="/admin" class="m-drawer-link" @click="$emit('close')">查看系统状态</RouterLink>
        <button class="m-drawer-logout" type="button" @click="onLogout">退出登录</button>
      </div>
    </aside>
  </Teleport>
</template>

<script setup>
import { busyUploads, confirm } from '../../next/state';
import { computed } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import AppIcon from '../icons/AppIcon.vue';
import { logout } from '../../api/cloudnext';
import { clearStoredSession, getSessionPersonKey, getStoredSession, getStoredToken } from '../../session';

defineProps({
  open: { type: Boolean, default: false }
});
const emit = defineEmits(['close']);

const router = useRouter();

const session = computed(() => getStoredSession());
const displayName = computed(() => session.value?.personName || getSessionPersonKey(session.value) || '当前用户');
const avatarText = computed(() => displayName.value.slice(0, 1) || '云');
const unitName = computed(() => session.value?.identityList?.[0]?.unitName || '组织信息未同步');

const navItems = [
  { to: '/', label: '首页', icon: 'home' },
  { to: '/my-files', label: '我的文件', icon: 'file' },
  { to: '/shared', label: '共享文件', icon: 'share' },
  { to: '/workspaces', label: '协作空间', icon: 'workspace' },
  { to: '/links', label: '外链管理', icon: 'globe' },
  { to: '/trash', label: '回收站', icon: 'trash' }
];

const onLogout = async () => {
  if (busyUploads() && !await confirm('退出登录？', '退出将中断当前上传任务。')) return;
  const token = getStoredToken();
  try {
    await logout();
  } catch (error) {
    /* swallow */
  }
  if (token !== getStoredToken()) return;
  clearStoredSession();
  emit('close');
  await router.replace('/login');
};
</script>

<style scoped>
.m-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  z-index: 40;
}

.m-drawer-fade-enter-active,
.m-drawer-fade-leave-active {
  transition: opacity 0.18s ease;
}

.m-drawer-fade-enter-from,
.m-drawer-fade-leave-to {
  opacity: 0;
}

.m-drawer {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  width: 80vw;
  max-width: 320px;
  z-index: 41;
  background: #fff;
  transform: translateX(-100%);
  transition: transform 0.22s ease;
  display: flex;
  flex-direction: column;
  padding-top: env(safe-area-inset-top, 0px);
  visibility: hidden;
}

.m-drawer.is-open {
  transform: translateX(0);
  visibility: visible;
}

.m-drawer-header {
  display: flex;
  align-items: center;
  gap: var(--m-sp-3);
  padding: var(--m-sp-4);
  background: var(--bg-primary-soft);
}

.m-drawer-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--bg-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--m-fs-title);
  font-weight: 600;
  flex-shrink: 0;
}

.m-drawer-user {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.m-drawer-user strong {
  font-size: var(--m-fs-body);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-drawer-user span {
  font-size: var(--m-fs-meta);
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-drawer-nav {
  flex: 1;
  overflow-y: auto;
  padding: var(--m-sp-2) 0;
}

.m-drawer-item {
  display: flex;
  align-items: center;
  gap: var(--m-sp-3);
  height: var(--m-tap-h);
  padding: 0 var(--m-sp-4);
  color: var(--text-primary);
  text-decoration: none;
  font-size: var(--m-fs-body);
}

.m-drawer-item.router-link-active {
  color: var(--bg-primary);
  background: var(--bg-nav-active);
}

.m-drawer-item:active {
  background: var(--bg-nav-hover);
}

.m-drawer-item-icon {
  display: inline-flex;
  width: 22px;
  justify-content: center;
}

.m-drawer-footer {
  padding: var(--m-sp-4);
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  display: grid;
  gap: var(--m-sp-2);
  padding-bottom: calc(var(--m-sp-4) + env(safe-area-inset-bottom, 0px));
}

.m-drawer-link {
  font-size: var(--m-fs-body);
  color: var(--bg-primary);
  text-decoration: none;
  text-align: center;
  padding: var(--m-sp-2) 0;
}

.m-drawer-logout {
  width: 100%;
  height: var(--m-tap-h);
  background: transparent;
  color: #b42318;
  border-radius: var(--radius-md);
  font-size: var(--m-fs-body);
}

.m-drawer-logout:active {
  background: var(--bg-danger-soft);
}
</style>
