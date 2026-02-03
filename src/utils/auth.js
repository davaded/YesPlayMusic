import Cookies from 'js-cookie';
import { logout } from '@/api/auth';
import store from '@/store';

const cookieStoragePrefix = 'cookie-';
const cookieKeyListStorage = 'cookie-keys';
const cookieAttributeKeys = new Set([
  'path',
  'domain',
  'expires',
  'max-age',
  'secure',
  'httponly',
  'samesite',
  'priority',
]);

function sanitizeCookieString(cookieString) {
  if (!cookieString) return '';
  return cookieString
    .replace(/;\s*HttpOnly/gi, '')
    .replace(/\s+HttpOnly/gi, '')
    .trim();
}

function extractCookiePairs(cookieString) {
  if (!cookieString) return [];
  const pairs = [];
  const parts = cookieString.split(';');
  for (const part of parts) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex <= 0) continue;
    const key = trimmed.slice(0, eqIndex);
    const lowerKey = key.toLowerCase();
    if (cookieAttributeKeys.has(lowerKey)) continue;
    const value = trimmed.slice(eqIndex + 1);
    pairs.push({ key, value });
  }
  return pairs;
}

function refreshCookieKeyList() {
  const keys = Object.keys(localStorage)
    .filter(
      key => key.startsWith(cookieStoragePrefix) && key !== cookieKeyListStorage
    )
    .map(key => key.slice(cookieStoragePrefix.length));
  localStorage.setItem(cookieKeyListStorage, JSON.stringify(keys));
}

export function setCookies(string) {
  if (!string) return;
  const parts = string.includes(';;') ? string.split(';;') : [string];
  let storedAny = false;
  parts.forEach(cookie => {
    if (!cookie) return;
    const sanitized = sanitizeCookieString(cookie);
    if (sanitized) {
      document.cookie = sanitized;
      const pairs = extractCookiePairs(sanitized);
      pairs.forEach(({ key, value }) => {
        if (!key) return;
        localStorage.setItem(`${cookieStoragePrefix}${key}`, value);
        storedAny = true;
      });
    }
  });
  if (!storedAny) {
    const pairs = extractCookiePairs(string);
    pairs.forEach(({ key, value }) => {
      if (!key) return;
      localStorage.setItem(`${cookieStoragePrefix}${key}`, value);
    });
  }
  refreshCookieKeyList();
}

export function getCookie(key) {
  return (
    Cookies.get(key) ?? localStorage.getItem(`${cookieStoragePrefix}${key}`)
  );
}

export function removeCookie(key) {
  Cookies.remove(key);
  localStorage.removeItem(`${cookieStoragePrefix}${key}`);
  refreshCookieKeyList();
}

// MUSIC_U 只有在账户登录的情况下才有
export function isLoggedIn() {
  return getCookie('MUSIC_U') !== undefined;
}

// 账号登录
export function isAccountLoggedIn() {
  return (
    getCookie('MUSIC_U') !== undefined &&
    store.state.data.loginMode === 'account'
  );
}

// 用户名搜索（用户数据为只读）
export function isUsernameLoggedIn() {
  return store.state.data.loginMode === 'username';
}

// 账户登录或者用户名搜索都判断为登录，宽松检查
export function isLooseLoggedIn() {
  return isAccountLoggedIn() || isUsernameLoggedIn();
}

export function doLogout() {
  logout();
  removeCookie('MUSIC_U');
  removeCookie('__csrf');
  // 更新状态仓库中的用户信息
  store.commit('updateData', { key: 'user', value: {} });
  // 更新状态仓库中的登录状态
  store.commit('updateData', { key: 'loginMode', value: null });
  // 更新状态仓库中的喜欢列表
  store.commit('updateData', { key: 'likedSongPlaylistID', value: undefined });
}

export function rehydrateCookies() {
  if (typeof document === 'undefined') return;
  let keys = [];
  try {
    keys = JSON.parse(localStorage.getItem(cookieKeyListStorage) || '[]');
  } catch (error) {
    keys = [];
  }
  keys.forEach(key => {
    const value = localStorage.getItem(`${cookieStoragePrefix}${key}`);
    if (value !== null && value !== undefined) {
      document.cookie = `${key}=${value}; path=/`;
    }
  });
}
