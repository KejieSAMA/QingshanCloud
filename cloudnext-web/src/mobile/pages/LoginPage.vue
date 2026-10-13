<template>
  <section class="auth-page" :class="{ 'auth-page-mobile': true }">

    <div class="auth-card">
      <div  class="m-auth-brand">
        <span class="auth-badge">青山云盘</span>
        <span class="m-auth-brand-sub">企业文件协作平台</span>
      </div>
      <div class="auth-head">
        <h1>{{ recoveryCode ? '云盘免登未完成' : '登录' }}</h1>
        <p v-if="!recoveryCode">使用 Form 工号或手机号进入青山云盘工作台。</p>
      </div>

      <section v-if="recoveryCode" class="auth-sso-recovery" aria-label="免登帮助">
        <p role="alert">{{ ssoRecoveryMessage(recoveryCode) }}</p>
        <button class="primary-button" type="button" :disabled="submitting || !status.ssoEnabled" @click="onSsoLogin">
          {{ submitting ? '正在返回 OA…' : '重新免登' }}
        </button>
        <a v-if="oaUrl" class="auth-sso-button auth-return-oa" :href="oaUrl">返回 OA</a>
        <p v-if="!status.ssoEnabled" class="status-note">免登服务状态暂不可用，请稍后刷新页面。</p>
      </section>

      <form v-else class="auth-form" autocomplete="on" @submit.prevent="onSubmit">
        <label class="auth-field" for="login-credential">
          <span>账号</span>
          <input
            id="login-credential"
            v-model.trim="form.credential"
            name="username"
            type="text"
            autocomplete="username"
            placeholder="工号 / 手机号"
            required
          />
        </label>
        <div class="auth-field">
          <label for="login-password"><span>密码</span></label>
          <span class="auth-password-control">
            <input
              id="login-password"
              v-model="form.password"
              name="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="请输入密码"
              required
            />
            <button
              class="auth-password-toggle"
              type="button"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </span>
        </div>
        <div class="auth-login-options">
          <label class="auth-remember" for="login-remember-password">
            <input
              id="login-remember-password"
              v-model="form.rememberPassword"
              name="remember-password"
              type="checkbox"
            />
            <span>记住密码</span>
          </label>
          <span class="auth-remember-note">
            浏览器安全保存，{{ status.rememberSessionTtlDays || 30 }} 天内免登录
          </span>
        </div>
        <p v-if="error" class="status-error auth-form-error" role="alert">{{ error }}</p>
        <button class="primary-button auth-submit" type="submit" :disabled="submitting">
          <span v-if="submitting" class="auth-spinner" aria-hidden="true"></span>
          {{ submitting ? '登录中…' : '登录' }}
        </button>
        <p class="auth-password-help">忘记密码请联系 Form 管理员重置。</p>
      </form>

      <div v-if="status.ssoEnabled && !recoveryCode" class="auth-sso">
        <span>或</span>
        <button class="auth-sso-button" type="button" :disabled="submitting" @click="onSsoLogin">
          从 Form 一键登录
        </button>
      </div>

      <p v-if="message" class="status-note status-ok auth-feedback">{{ message }}</p>

      <div class="auth-status-row" aria-label="服务状态">
        <div class="auth-status-pill">
          <span>认证模式</span>
          <strong>{{ status.provider || status.mode || '--' }}</strong>
        </div>
        <div class="auth-status-pill">
          <span>Form 认证服务</span>
          <strong :class="status.configured ? 'auth-status-ok' : 'auth-status-warn'">
            {{ status.configured ? '已连接' : '未配置' }}
          </strong>
        </div>
      </div>
    </div>
  </section>
</template>
<script setup>
import { useLoginForm } from '../../auth/useLoginController';
const { status, form, showPassword, error, message, submitting, recoveryCode, oaUrl, onSubmit, onSsoLogin, ssoRecoveryMessage } = useLoginForm();
</script>
<style scoped>

.auth-sso-recovery { display: grid; gap: 16px; }
.auth-sso-recovery p { margin: 0; line-height: 1.65; overflow-wrap: anywhere; }
.auth-return-oa { display: flex; align-items: center; justify-content: center; text-decoration: none; }

.auth-page-mobile {
  flex-direction: column;
  padding: 12px;
  gap: 0;
  min-height: 100vh;
  min-height: 100dvh;
  justify-content: center;
}

.auth-page-mobile .auth-card {
  flex: 0 1 auto;
  width: 100%;
  max-width: 420px;
  margin: 0 auto;
  padding: 20px 18px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.m-auth-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.m-auth-brand .auth-badge {
  margin: 0;
}

.m-auth-brand-sub {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.auth-page-mobile .auth-head h1 {
  font-size: 22px;
}

.auth-page-mobile .auth-head p {
  font-size: 13px;
}

.auth-sso {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12px;
}

.auth-password-control {
  position: relative;
  display: block;
}

.auth-password-control input {
  height: 44px;
  padding-right: 62px;
}

.auth-password-toggle {
  position: absolute;
  top: 0;
  right: 0;
  min-width: 54px;
  min-height: 44px;
  padding: 0 12px;
  border: 0;
  background: transparent;
  color: var(--bg-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.auth-login-options {
  display: grid;
  gap: 4px;
  margin-top: -4px;
}

.auth-form .auth-remember {
  display: flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  min-height: 44px;
  cursor: pointer;
}

.auth-form .auth-remember input[type='checkbox'] {
  width: 18px;
  height: 18px;
  margin: 0;
  accent-color: var(--bg-primary);
}

.auth-remember-note,
.auth-password-help {
  color: var(--text-tertiary) !important;
  font-size: 12px !important;
  font-weight: 400 !important;
}

.auth-form-error,
.auth-password-help {
  margin: 0;
}

.auth-form-error {
  font-size: 13px;
}

.auth-spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, 0.55);
  border-top-color: #fff;
  border-radius: 50%;
  animation: auth-spin 0.7s linear infinite;
}

@keyframes auth-spin {
  to { transform: rotate(360deg); }
}

.auth-sso-button {
  min-height: 44px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  color: var(--text-primary);
  font-weight: 600;
  cursor: pointer;
}

.auth-sso-button:hover:not(:disabled) {
  border-color: var(--bg-primary);
  color: var(--bg-primary);
}

</style>
