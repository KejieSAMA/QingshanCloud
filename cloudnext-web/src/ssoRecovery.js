const messages = Object.freeze({
  DIRECTORY_SYNC_PENDING: '人员正在同步，请稍后重新免登。后台同步会继续进行。',
  DIRECTORY_SYNC_FAILED: '人员同步失败，请稍后重试；若持续失败，请联系管理员检查目录同步。',
  DIRECTORY_BINDING_MISSING: '人员目录尚未建立云盘绑定，请稍后重试或联系管理员核对。',
  IDENTITY_MAPPING_PENDING: '身份映射待确认或已归档，请联系管理员核对后再登录。',
  IDENTITY_UNAVAILABLE: '当前身份不可用，请联系管理员核对。',
  ACCOUNT_DISABLED: '账号已停用，请联系管理员。',
  TICKET_INVALID: '免登票据已过期或失效，请重新免登。',
  PASSWORD_CHANGE_REQUIRED: '请先返回 OA 修改密码，再进入云盘。',
  SSO_REQUEST_INVALID: '免登请求无效，请重新从 OA 进入云盘。',
  SSO_UNAVAILABLE: '免登服务暂不可用，请稍后重试。'
});

export function ssoRecoveryMessage(code) {
  return typeof code === 'string' && Object.hasOwn(messages, code) ? messages[code] : messages.SSO_UNAVAILABLE;
}

// Only the configured OA origin is used, never an error-query redirect URL.
export function oaReturnUrl(configuredStartUrl) {
  try {
    const url = new URL(configuredStartUrl);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return '';
    for (const key of ['cloudnextSso', 'returnPath', 'ticket']) url.searchParams.delete(key);
    url.hash = '';
    return url.toString();
  } catch { return ''; }
}
