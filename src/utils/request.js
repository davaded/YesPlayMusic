import router from '@/router';
import { doLogout, getCookie } from '@/utils/auth';
import { isFileProtocol, isWebView } from '@/utils/runtime';
import axios from 'axios';

const webviewApiUrl = process.env.VUE_APP_WEBVIEW_API_URL;
const defaultApiUrl = process.env.VUE_APP_NETEASE_API_URL || '/api';

let baseURL = '';
if (isWebView || isFileProtocol) {
  if (webviewApiUrl) {
    baseURL = webviewApiUrl;
  } else if (defaultApiUrl && defaultApiUrl !== '/api') {
    baseURL = defaultApiUrl;
  } else {
    baseURL = 'http://127.0.0.1:3000';
  }
} else {
  baseURL = defaultApiUrl || '/api';
}

const service = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 15000,
});

service.interceptors.request.use(config => {
  if (!config.params) config.params = {};
  const musicU = getCookie('MUSIC_U');
  const isAbsolute = /^https?:\/\//.test(baseURL);
  const shouldAttachCookie = musicU && !config.url.includes('/login');
  if (shouldAttachCookie && (isAbsolute || isWebView || isFileProtocol)) {
    config.params.cookie = `MUSIC_U=${musicU};`;
  }

  if (!baseURL) {
    console.error("You must set up the baseURL in the service's config");
  }

  return config;
});

service.interceptors.response.use(
  response => response.data,
  async error => {
    /** @type {import('axios').AxiosResponse | null} */
    let response;
    let data;
    if (error === 'TypeError: baseURL is undefined') {
      response = error;
      data = error;
      console.error("You must set up the baseURL in the service's config");
    } else if (error.response) {
      response = error.response;
      data = response.data;
    }

    if (
      response &&
      typeof data === 'object' &&
      data.code === 301 &&
      data.msg === '闇€瑕佺櫥褰?'
    ) {
      console.warn('Token has expired. Logout now!');
      doLogout();
      router.push({ name: 'login' });
    }
  }
);

export default service;
