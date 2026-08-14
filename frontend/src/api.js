const AUTH_KEY = 'audit-api-credentials';

export function getCredentials() {
  return sessionStorage.getItem(AUTH_KEY) || '';
}

export function setCredentials(username, password) {
  sessionStorage.setItem(AUTH_KEY, btoa(`${username}:${password}`));
}

export function clearCredentials() {
  sessionStorage.removeItem(AUTH_KEY);
}

export async function authorizedFetch(url, options = {}) {
  const headers = new Headers(options.headers || {});
  const credentials = getCredentials();
  if (credentials) headers.set('Authorization', `Basic ${credentials}`);
  return fetch(url, { ...options, headers });
}
