import { describe, expect, it } from 'vitest';
import { oaReturnUrl, ssoRecoveryMessage } from '../ssoRecovery';

describe('SSO recovery', () => {
  it('shows Chinese messages for sync, identity and consumed tickets', () => {
    expect(ssoRecoveryMessage('DIRECTORY_SYNC_PENDING')).toContain('后台同步会继续');
    expect(ssoRecoveryMessage('DIRECTORY_SYNC_FAILED')).toContain('同步失败');
    expect(ssoRecoveryMessage('IDENTITY_MAPPING_PENDING')).toContain('待确认');
    expect(ssoRecoveryMessage('TICKET_INVALID')).toContain('过期或失效');
  });
  it('never reflects unknown codes, upstream messages or prototype keys', () => {
    for (const code of ['<script>secret-ticket</script>', 'toString', '__proto__', ['TICKET_INVALID']]) {
      expect(ssoRecoveryMessage(code)).toBe('免登服务暂不可用，请稍后重试。');
    }
  });
  it('returns to configured OA without repeating a consumed ticket or auto-login', () => {
    expect(oaReturnUrl('https://oa.test/?cloudnextSso=1&returnPath=/x&ticket=secret&module=home#section'))
      .toBe('https://oa.test/?module=home');
    expect(oaReturnUrl('javascript:alert(1)')).toBe('');
    expect(oaReturnUrl('https://user:password@oa.test/')).toBe('');
    expect(oaReturnUrl('')).toBe('');
  });
});
