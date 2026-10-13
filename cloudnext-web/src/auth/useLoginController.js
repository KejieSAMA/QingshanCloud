
import { navigateAfterLogin } from '../authNavigation';
import { computed, inject, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { buildSsoStartUrl, completeSsoLogin, getAuthStatus, login, logout } from '../api/cloudnext';
import { clearStoredSession, getStoredToken, setStoredSession } from '../session';
import { sanitizeReturnPath } from '../authNavigation';
import { oaReturnUrl, ssoRecoveryMessage } from '../ssoRecovery';
import {
  loadBrowserPassword,
  loadLoginPreference,
  preventSilentPasswordAccess,
  saveLoginPreference,
  storeBrowserPassword
} from '../loginPreference';


export const LOGIN_FORM_KEY = Symbol('cloudnext-login-form');
export function useLoginForm() {
  const form = inject(LOGIN_FORM_KEY);
  if (!form) throw new Error('Login form must be inside LoginRoute');
  return form;
}
export function useLoginController() {
let disposed = false;
onUnmounted(() => { disposed = true; form.password = ''; });
const router = useRouter();
const route = useRoute();
const status = ref({
  mode: '',
  provider: '',
  configured: false,
  o2Configured: false,
  ssoEnabled: false,
  rememberSessionTtlDays: 30
});
const form = reactive({
  credential: '',
  password: '',
  rememberPassword: false
});
const showPassword = ref(false);
const error = ref('');
const message = ref('');
const submitting = ref(false);
const recoveryCode = ref(route.query.sso === 'error' ? (route.query.code || 'SSO_UNAVAILABLE') : '');
const oaUrl = computed(() => oaReturnUrl(status.value.formSsoStartUrl));

const loadStatus = async () => {
  try {
    const value = await getAuthStatus();
    status.value = {
      ...value,
      provider: value?.provider || value?.mode || '',
      configured:
        typeof value?.configured === 'boolean' ? value.configured : Boolean(value?.o2Configured),
      ssoEnabled: Boolean(value?.ssoEnabled)
    };
  } catch (e) {
    error.value = e.message || '认证状态获取失败';
  }
};

const onSubmit = async () => {
  if (submitting.value || disposed) return;
  error.value = '';
  message.value = '';
  if (!form.credential || !form.password) {
    error.value = '请输入账号和密码。';
    return;
  }
  submitting.value = true;
  try {
    const expectedToken = getStoredToken();
    const session = await login({
      credential: form.credential,
      password: form.password,
      rememberMe: form.rememberPassword
    });
    if (disposed || getStoredToken() !== expectedToken) return;
    saveLoginPreference({
      rememberPassword: form.rememberPassword,
      credential: form.credential
    });
    if (form.rememberPassword) {
      await storeBrowserPassword(form.credential, form.password).catch(() => false);
    } else {
      await preventSilentPasswordAccess();
    }
    if (disposed || getStoredToken() !== expectedToken) return;
    setStoredSession(session);
    message.value = '登录成功，正在进入工作台。';
    await navigateAfterLogin(router, route.query.returnPath);
  } catch (e) {
    error.value = e?.status === 412
      ? '该账号需要先在 Form 完成密码变更，请联系 Form 管理员。'
      : e.message || '登录失败';
  } finally {
    submitting.value = false;
  }
};

const onSsoLogin = () => {
  if (submitting.value) return;
  submitting.value = true;
  error.value = '';
  window.location.assign(buildSsoStartUrl(sanitizeReturnPath(route.query.returnPath, '/')));
};

const tryCompleteSso = async () => {
  if (route.query.sso !== 'complete') {
    return;
  }
  submitting.value = true;
  try {
    const previousToken = getStoredToken();
    if (previousToken) {
      try {
        await logout();
      } catch {
        // The old CloudNext session may already be invalid; continue with the one-time Form bootstrap.
      }
      if (disposed || (getStoredToken() && getStoredToken() !== previousToken)) return;
      clearStoredSession();
    }
    const expectedToken = getStoredToken();
    const session = await completeSsoLogin();
    if (disposed || getStoredToken() !== expectedToken) return;
    setStoredSession(session);
    await navigateAfterLogin(router, route.query.returnPath);
  } catch (e) {
    recoveryCode.value = e?.data?.code || (e?.status === 401 ? 'TICKET_INVALID' : 'SSO_UNAVAILABLE');
  } finally {
    submitting.value = false;
  }
};

onMounted(async () => {
  const preference = loadLoginPreference();
  form.rememberPassword = preference.rememberPassword;
  form.credential = preference.credential;
  if (preference.rememberPassword) {
    const saved = await loadBrowserPassword(preference.credential);
    if (saved) {
      form.credential = saved.credential;
      form.password = saved.password;
    }
  }
  await loadStatus();
  if (disposed) return;
  await tryCompleteSso();
});

return { status, form, showPassword, error, message, submitting, recoveryCode, oaUrl, onSubmit, onSsoLogin, ssoRecoveryMessage };
}
