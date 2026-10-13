import { describe, expect, it, vi } from 'vitest';
import {
  CLOUDNEXT_LOGIN_PREFERENCE_KEY,
  loadLoginPreference,
  saveLoginPreference
} from '../loginPreference';

function storage() {
  const values = new Map();
  return {
    getItem: vi.fn((key) => values.get(key) ?? null),
    setItem: vi.fn((key, value) => values.set(key, value)),
    removeItem: vi.fn((key) => values.delete(key))
  };
}

describe('loginPreference', () => {
  it('stores only the remember flag and account, never a password', () => {
    const target = storage();

    saveLoginPreference({ rememberPassword: true, credential: 'A1001', password: 'secret' }, target);

    const saved = target.setItem.mock.calls[0][1];
    expect(saved).toContain('A1001');
    expect(saved).not.toContain('secret');
    expect(loadLoginPreference(target)).toEqual({ rememberPassword: true, credential: 'A1001' });
  });

  it('removes the preference when remember password is disabled', () => {
    const target = storage();

    saveLoginPreference({ rememberPassword: false, credential: 'A1001' }, target);

    expect(target.removeItem).toHaveBeenCalledWith(CLOUDNEXT_LOGIN_PREFERENCE_KEY);
  });

  it('discards malformed stored data', () => {
    const target = storage();
    target.getItem.mockReturnValue('{bad json');

    expect(loadLoginPreference(target)).toEqual({ rememberPassword: false, credential: '' });
    expect(target.removeItem).toHaveBeenCalledWith(CLOUDNEXT_LOGIN_PREFERENCE_KEY);
  });
});
