import test from 'node:test';
import assert from 'node:assert/strict';
import { shouldClearPendingDraw, retryAfterSeconds, drawFailureMessage, saveCatalogSnapshot, readCatalogSnapshot } from '../src/lib/degradation.mjs';

const error = (status, errorCode, headers = {}) => ({ message: '服务返回错误', response: { status, data: { errorCode }, headers } });
test('only server-confirmed terminal draw outcomes release the pending request', () => {
  for (const code of ['DRAW_SOLD_OUT', 'DRAW_NOT_ON_SALE', 'DRAW_INSUFFICIENT_BALANCE'])
    assert.equal(shouldClearPendingDraw(error(409, code), false), true);
  assert.equal(shouldClearPendingDraw(error(404, 'DRAW_BOX_NOT_FOUND'), false), true);
  for (const value of [error(409, 'REQUEST_CONFLICT'), error(409), error(503), error(500, 'DRAW_SOLD_OUT'), {}])
    assert.equal(shouldClearPendingDraw(value), false);
  assert.equal(shouldClearPendingDraw(error(409, "DRAW_SOLD_OUT"), true), false);
  assert.match(drawFailureMessage(error(409, "DRAW_SOLD_OUT"), true), /保留原请求/);
  assert.match(drawFailureMessage(error(503)), /结果暂未确认/);
});
test('retry delay handles response headers and is bounded', () => {
  assert.equal(retryAfterSeconds(error(429, null, { 'retry-after': '60' })), 60);
  assert.equal(retryAfterSeconds(error(503, null, { get: () => '1' })), 1);
  assert.equal(retryAfterSeconds(error(503, null, { 'retry-after': '999999' })), 300);
  assert.equal(retryAfterSeconds(error(503, null, { 'retry-after': 'invalid' })), 0);
  assert.equal(retryAfterSeconds({}), 0);
});
test('stale snapshot contains only public metadata and is limited by query and age', () => {
  const data = new Map(); const storage = { setItem: (k, v) => data.set(k, v), getItem: (k) => data.get(k) };
  const item = { id: 1, name: '海洋', series: 'sea', image: '/images/sea.png', description: '介绍', price: 5900, stock: 7, probability: .7 };
  saveCatalogSnapshot(storage, 'query-a', { list: [item] }, 1000);
  const snapshot = readCatalogSnapshot(storage, 'query-a', 2000);
  assert.equal(snapshot.stale, true);
  assert.deepEqual(snapshot.list[0], { id: 1, name: '海洋', series: 'sea', image: '/images/sea.png', description: '介绍' });
  assert.equal(readCatalogSnapshot(storage, 'query-b', 2000), null);
  assert.equal(readCatalogSnapshot(storage, 'query-a', 1000 + 15 * 60 * 1000 + 1), null);
  assert.equal(readCatalogSnapshot(storage, 'query-a', 999), null);
  assert.doesNotThrow(() => saveCatalogSnapshot({ setItem() { throw new Error('blocked'); } }, 'a', { list: [item] }));
  assert.equal(readCatalogSnapshot({ getItem() { throw new Error('blocked'); } }, 'a'), null);
  saveCatalogSnapshot(storage, 'bad', { list: [null] }, 1000);
  assert.equal(readCatalogSnapshot(storage, 'bad', 2000), null);
});
