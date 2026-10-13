<template><component :is="isMobile ? MobileLogin : DesktopLogin" /></template>
<script setup>
import { defineAsyncComponent, provide } from 'vue';
import { useBreakpoint } from '../composables/useBreakpoint';
import { useLoginController, LOGIN_FORM_KEY } from './useLoginController';
const DesktopLogin = defineAsyncComponent(() => import('../next/pages/LoginPage.vue'));
const MobileLogin = defineAsyncComponent(() => import('../mobile/pages/LoginPage.vue'));
const { isMobile } = useBreakpoint();
provide(LOGIN_FORM_KEY, useLoginController());
</script>
