import { parse, stringify } from './json-numbers.js';

export class ApiError extends Error {
  constructor(message, status = null, timestamp = null) { super(message); this.status = status; this.timestamp = timestamp; }
}
export function httpsBase(value, fallback = '') {
  const url = new URL(value || fallback);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash)
    throw new Error('В конфигурации нужен HTTPS URL без логина, query и fragment');
  return url.href.replace(/\/+$/, '');
}
export function requestUrl(base, path, parameters = []) {
  const url = new URL(base + path);
  for (const [key, value] of parameters) url.searchParams.append(key, value);
  return url;
}
export function createApi(config, origin, fetcher = globalThis.fetch) {
  const flats = httpsBase(config.flatsBaseUrl, origin);
  const agency = config.agencyBaseUrl ? httpsBase(config.agencyBaseUrl) : null;
  const timeout = config.requestTimeoutMillis ?? 20000;
  if (!Number.isFinite(timeout) || timeout < 1) throw new Error('Некорректный тайм-аут клиента');
  async function send(url, method = 'GET') {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetcher(url, { method, headers: { Accept: 'application/json' },
        credentials: 'omit', redirect: 'follow', cache: 'no-store', signal: controller.signal });
      const text = await response.text(); let data = null;
      if (text) {
        try { data = parse(text); }
        catch {
          if (response.ok) throw new ApiError('Сервис вернул некорректный JSON', response.status);
        }
      }
      if (!response.ok) throw new ApiError(typeof data?.message === 'string' ? data.message : 'Сервис вернул ошибку без JSON-описания', response.status, data?.timestamp);
      return { data, status: response.status, total: response.headers.get('X-Total-Count'),
        location: response.headers.get('Location'), url: response.url || String(url), redirected: response.redirected };
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (controller.signal.aborted) throw new ApiError('Время ожидания истекло. Результат изменения мог сохраниться; проверьте коллекцию перед повтором.');
      throw new ApiError('Не удалось связаться с сервисом. Проверьте его доступность, доверие HTTPS-сертификатам и настройки CORS.');
    } finally { clearTimeout(timer); }
  }
  async function request(service, method, path, parameters = []) {
    const base = service === 'agency' ? agency : flats;
    if (!base) throw new Error('Не задан публичный HTTPS URL агентства. Настройте AGENCY_PUBLIC_BASE_URL и повторите подготовку Jetty.');
    return send(requestUrl(base, path, parameters), method);
  }
  return {
    flats, agency, request,
    async create(value) {
      const result = await request('flats', 'POST', '/flats', [['flat', stringify(value)]]);
      if (!result.location) throw new ApiError('Квартира создана, но сервис не передал Location. Обновите коллекцию.', result.status);
      const target = new URL(result.location, result.url);
      if (target.origin !== new URL(flats).origin || !target.pathname.startsWith(new URL(flats).pathname.replace(/\/$/, '') + '/flats/'))
        throw new ApiError('Квартира создана, но получен неожиданный Location. Обновите коллекцию.', result.status);
      const created = await send(target); return { ...created, status: result.status };
    }
  };
}
