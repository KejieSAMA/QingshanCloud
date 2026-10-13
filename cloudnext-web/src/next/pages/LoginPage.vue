<template>
  <main class="desktop-login">
    <section class="login-intro" aria-label="青山云盘">
      <div class="login-brand"><span class="brand-symbol"><Icon name="folder"/></span><div><strong>青山云盘</strong><span>企业文件工作台</span></div></div>
      <div><h1>让文件井然有序，<br>让协作自然发生。</h1><p>个人资料、共享文件与团队空间，一处连接日常工作。</p></div>
      <ul class="login-features"><li v-for="feature in features" :key="feature.title"><Icon :name="feature.icon"/><div><strong>{{feature.title}}</strong><p>{{feature.text}}</p></div></li></ul>
    </section>
    <section class="login-panel" aria-labelledby="login-title">
      <h2 id="login-title">{{recoveryCode ? '免登未完成' : '登录青山云盘'}}</h2>
      <p class="form-note">使用 Form 工号或手机号登录。</p>
      <section v-if="recoveryCode" class="login-recovery">
        <p class="login-error" role="alert">{{ssoRecoveryMessage(recoveryCode)}}</p>
        <button class="btn primary" :disabled="submitting || !status.ssoEnabled" @click="onSsoLogin">{{submitting?'正在连接…':'重新免登'}}</button>
        <a v-if="oaUrl" class="btn" :href="oaUrl">返回 Form</a>
      </section>
      <form v-else class="login-form" autocomplete="on" @submit.prevent="onSubmit">
        <label for="desktop-account">账号<input id="desktop-account" class="input" v-model.trim="form.credential" name="username" autocomplete="username" required placeholder="工号 / 手机号"></label>
        <label for="desktop-password">密码<span class="login-password"><input id="desktop-password" class="input" v-model="form.password" name="password" :type="showPassword?'text':'password'" autocomplete="current-password" required placeholder="请输入密码"><button type="button" :aria-pressed="showPassword" :aria-label="showPassword?'隐藏密码':'显示密码'" @click="showPassword=!showPassword">{{showPassword?'隐藏':'显示'}}</button></span></label>
        <div class="login-options"><label><input type="checkbox" v-model="form.rememberPassword" name="remember-password">记住密码</label><span>{{status.rememberSessionTtlDays||30}} 天内免登录</span></div>
        <p v-if="error" class="login-error" role="alert">{{error}}</p>
        <button class="btn primary login-submit" :disabled="submitting">{{submitting?'登录中…':'登录'}}</button>
        <p class="login-help">忘记密码？请联系 Form 管理员重置。</p>
      </form>
      <template v-if="status.ssoEnabled && !recoveryCode"><div class="login-separator"><span>或</span></div><button class="btn login-submit" :disabled="submitting" @click="onSsoLogin"><Icon name="arrow"/>从 Form 一键登录</button></template>
      <p v-if="message" class="form-note" role="status">{{message}}</p>
      <div class="login-service"><span class="login-service-dot" :class="{warn:!status.configured}"></span><span>{{status.provider||'FORM'}} 认证</span><span>{{status.configured?'已配置':'未配置'}}</span></div>
    </section>
  </main>
</template>
<script setup>
import Icon from '../components/Icon.vue';
import { useLoginForm } from '../../auth/useLoginController';
const { status, form, showPassword, error, message, submitting, recoveryCode, oaUrl, onSubmit, onSsoLogin, ssoRecoveryMessage } = useLoginForm();
const features = [
  {icon:'folder',title:'统一文件入口',text:'集中整理个人文件，快速找到所需资料。'},
  {icon:'people',title:'团队协作空间',text:'让部门与项目协作拥有清晰的访问边界。'},
  {icon:'globe',title:'可控分享',text:'使用共享权限、提取码与期限保护工作资料。'}
];
</script>
<style scoped>
.desktop-login{height:100dvh;overflow:auto;display:grid;grid-template-columns:minmax(0,1fr) 410px;gap:80px;align-items:center;max-width:1160px;padding:60px 48px;margin:auto}
.login-intro{display:flex;flex-direction:column;gap:48px}.login-brand{display:flex;gap:12px;align-items:center}.login-brand strong{display:block;font-size:20px;font-weight:600}.login-brand span:not(.brand-symbol){display:block;color:var(--muted);font-size:12px;margin-top:3px}
.login-intro h1{font-size:36px;font-weight:600;line-height:1.5;letter-spacing:-1px;margin:0 0 18px}.login-intro p{font-size:14px;line-height:1.8;color:var(--muted);margin:0}
.login-features{list-style:none;padding:0;margin:0;display:grid;gap:22px}.login-features li{display:flex;gap:14px;align-items:flex-start}.login-features li>.ic{color:var(--accent);margin-top:3px}.login-features strong{font-size:14px;font-weight:600}.login-features p{font-size:13px;margin-top:4px}
.login-panel{border:1px solid var(--line);border-radius:var(--radius);padding:32px;background:var(--surface)}.login-panel h2{font-size:22px;font-weight:600;margin:0 0 10px}.login-form{display:grid;gap:20px;margin-top:28px}.login-form>label{display:grid;gap:8px;font-size:13px;color:var(--text)}.login-form .input{width:100%;height:42px}.login-password{display:block;position:relative}.login-password input{padding-right:58px}.login-password button{position:absolute;right:10px;top:0;height:42px;color:var(--muted);font-size:12px}.login-options{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px;color:var(--muted)}.login-options label{display:flex;align-items:center;gap:8px;color:var(--text)}.login-options input{accent-color:var(--accent);width:15px;height:15px}.login-submit{width:100%;justify-content:center;min-height:42px}.login-help{font-size:12px;line-height:1.6;color:var(--muted);margin:0}.login-error{color:var(--red);font-size:13px;line-height:1.7;margin:0;overflow-wrap:anywhere}.login-separator{display:flex;align-items:center;gap:12px;color:var(--faint);font-size:12px;margin:22px 0}.login-separator:before,.login-separator:after{content:"";flex:1;height:1px;background:var(--line)}.login-service{border-top:1px solid var(--line);padding-top:18px;margin-top:24px;display:flex;align-items:center;gap:8px;font-size:12px;color:var(--muted)}.login-service span:last-child{margin-left:auto}.login-service-dot{width:6px;height:6px;border-radius:50%;background:var(--green)}.login-service-dot.warn{background:var(--orange)}.login-recovery{display:grid;gap:16px;margin-top:24px}.login-recovery a{text-align:center;justify-content:center;text-decoration:none}
@media(max-width:1050px){.desktop-login{gap:36px;padding:40px 28px;grid-template-columns:minmax(0,1fr) 360px}.login-intro h1{font-size:28px}}
@media(max-width:760px){.desktop-login{display:flex;flex-direction:column;gap:28px;padding:32px 24px}.login-intro{width:100%;max-width:410px;gap:24px}.login-intro h1{font-size:25px;margin-bottom:10px}.login-features{display:none}.login-panel{width:100%;max-width:410px}}
</style>
