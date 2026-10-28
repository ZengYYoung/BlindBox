// Only a terminal response to the first attempt can end the pending request.
// A retry may overtake an earlier timed-out request on another instance.
const FINAL_DRAW_REJECTIONS = new Set([
  'DRAW_BOX_NOT_FOUND', 'DRAW_NOT_ON_SALE', 'DRAW_SOLD_OUT', 'DRAW_INSUFFICIENT_BALANCE',
]);
export function shouldClearPendingDraw(error, isRetry = true) {
  const status = error?.response?.status;
  return !isRetry && (status === 404 || status === 409) && FINAL_DRAW_REJECTIONS.has(error?.response?.data?.errorCode);
}
export function retryAfterSeconds(error, now = Date.now()) {
  const headers = error?.response?.headers;
  const value = headers?.get?.('retry-after') ?? headers?.['retry-after'];
  if (value == null) return 0;
  const text = String(value).trim();
  const seconds = /^\d+$/.test(text) ? Number(text) : Math.ceil((Date.parse(text) - now) / 1000);
  return Number.isFinite(seconds) ? Math.max(0, Math.min(seconds, 300)) : 0;
}
export function drawFailureMessage(error, isRetry = true) {
  if (shouldClearPendingDraw(error, isRetry)) return error.message;
  const status = error?.response?.status;
  if (status === 429) return '操作较频繁，本次结果暂未确认。请稍后查询原结果或使用原请求重试。';
  if (status === 503) return '服务繁忙，本次结果暂未确认。请稍后查询原结果或使用原请求重试。';
  return (error?.message || '请求未完成') + '。请保留原请求并查询结果。';
}

const SNAPSHOT_KEY = 'blindbox:catalog:snapshot:v1';
const MAX_AGE_MS = 15 * 60 * 1000;
function publicMetadata(list) {
  if (!Array.isArray(list) || list.length > 12) return null;
  const result = [];
  for (const item of list) {
    if (!item || !Number.isSafeInteger(item.id) || item.id < 1 ||
        typeof item.name !== 'string' || typeof item.series !== 'string' ||
        typeof item.image !== 'string' || (item.description != null && typeof item.description !== 'string')) return null;
    result.push({ id: item.id, name: item.name, series: item.series, image: item.image, description: item.description });
  }
  return result;
}
export function saveCatalogSnapshot(storage, query, response, now = Date.now()) {
  try {
    if (!storage) return;
    const list = publicMetadata(response?.list);
    if (list) storage.setItem(SNAPSHOT_KEY, JSON.stringify({ query, savedAt: now, list }));
  } catch { /* storage is optional */ }
}
export function readCatalogSnapshot(storage, query, now = Date.now()) {
  try {
    const snapshot = JSON.parse(storage?.getItem(SNAPSHOT_KEY) || 'null');
    if (!snapshot || snapshot.query !== query || !Number.isFinite(snapshot.savedAt) ||
        now < snapshot.savedAt || now - snapshot.savedAt > MAX_AGE_MS) return null;
    const list = publicMetadata(snapshot.list);
    return list ? { list, savedAt: snapshot.savedAt, stale: true } : null;
  } catch { return null; }
}
