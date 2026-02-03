const userAgent =
  typeof navigator !== 'undefined' ? navigator.userAgent || '' : '';

export const isAndroid = /Android/i.test(userAgent);
export const isAndroidWebView =
  isAndroid &&
  (/\bwv\b/i.test(userAgent) ||
    /; wv\)/i.test(userAgent) ||
    (/\bVersion\/\d+\.\d+\b/i.test(userAgent) &&
      /\bChrome\/\d+/i.test(userAgent)));
export const isAndroidX86 =
  isAndroid && /(x86_64|x86|i686|i386)/i.test(userAgent);

export const isFileProtocol =
  typeof window !== 'undefined' && window.location.protocol === 'file:';

const getRuntimeOverride = () => {
  const envRuntime = (process.env.VUE_APP_RUNTIME || '').toLowerCase();
  if (typeof window === 'undefined') return envRuntime;
  const queryRuntime = new URLSearchParams(window.location.search).get(
    'runtime'
  );
  return (queryRuntime || envRuntime || '').toLowerCase();
};

const runtimeOverride = getRuntimeOverride();
export const runtimeMode = runtimeOverride
  ? runtimeOverride
  : isAndroidWebView
  ? 'webview'
  : 'web';

export const isWebView = runtimeMode === 'webview';

export function isLandscape() {
  if (typeof window === 'undefined') return false;
  if (window.matchMedia) {
    return window.matchMedia('(orientation: landscape)').matches;
  }
  return window.innerWidth > window.innerHeight;
}

export function isCarUi() {
  if (typeof window === 'undefined') return false;
  const width = window.innerWidth || 0;
  const height = window.innerHeight || 0;
  if (!width || !height) return false;
  const ratio = width / height;
  const forceCar =
    typeof window.location !== 'undefined' &&
    new URLSearchParams(window.location.search).get('car') === '1';
  const disableCar =
    typeof window.location !== 'undefined' &&
    new URLSearchParams(window.location.search).get('car') === '0';
  if (disableCar) return false;
  if (forceCar) return true;
  return isAndroid && ratio >= 1.3 && height <= 720;
}

export function applyRuntimeClasses() {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.dataset.android = isAndroid ? 'true' : 'false';
  root.dataset.webview = isWebView ? 'true' : 'false';
  root.dataset.runtime = runtimeMode;
  root.dataset.androidX86 = isAndroidX86 ? 'true' : 'false';
  root.dataset.landscape = isLandscape() ? 'true' : 'false';
  root.dataset.carUi = isCarUi() ? 'true' : 'false';
}
