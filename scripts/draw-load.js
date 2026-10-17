import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

if (__ENV.RUN_LOAD_TEST !== 'YES') throw new Error('Set RUN_LOAD_TEST=YES only for an isolated test database.');
if (!/^[A-Za-z0-9_-]{1,24}$/.test(__ENV.RUN_ID || '')) throw new Error('Provide a unique RUN_ID of 1-24 letters, digits, _ or -.');
const tokens = (__ENV.USER_TOKENS || '').split(',').map(s => s.trim()).filter(Boolean);
const pools = (__ENV.BOX_IDS || __ENV.BOX_ID || '').split(',').map(s => Number(s.trim()));
if (!tokens.length || pools.some(id => !Number.isSafeInteger(id) || id < 1 || id > 2147483647))
  throw new Error('Provide USER_TOKENS and BOX_ID or BOX_IDS.');
const vus = Number(__ENV.VUS || 10);
const iterations = Number(__ENV.ITERATIONS || 100);
if (!Number.isInteger(vus) || vus < 1 || vus > 2000 || !Number.isInteger(iterations) || iterations < 1 || iterations > 10000000)
  throw new Error('Invalid VUS or ITERATIONS.');

const outcomes = new Counter('draw_attempts');
const successes = new Counter('draw_successes');
const committedLatency = new Trend('draw_success_duration', true);
const replayOutcomes = new Counter('draw_replay_attempts');
const unexpected = new Counter('draw_unexpected');
export const options = {
  vus, iterations,
  thresholds: { checks: ['rate==1'], draw_unexpected: ['count==0'] },
};

function payload(response) {
  try { return response.json(); } catch (_) { return null; }
}
function classify(response, body) {
  if (response.status === 200 && body?.code === 200 && body?.data?.orderId) return 'success';
  if (response.status === 429) return 'rate_limited';
  if (response.status === 503) return 'busy';
  if (response.status === 409) {
    const message = body?.msg || '';
    if (message.includes('售罄')) return 'sold_out';
    if (message.includes('余额不足')) return 'insufficient_balance';
    if (message.includes('库存校验')) return 'inventory_inconsistent';
    return 'conflict';
  }
  return 'unexpected';
}
export default function () {
  const token = tokens[(__VU - 1) % tokens.length];
  const pool = pools[(__VU + __ITER - 1) % pools.length];
  const key = 'load-' + __ENV.RUN_ID + '-' + __VU + '-' + __ITER;
  const url = (__ENV.BASE_URL || 'http://127.0.0.1:8080') + '/api/blindboxes/' + pool + '/draw';
  const params = {
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json', 'Idempotency-Key': key },
    tags: { name: 'draw' }, timeout: '15s',
  };
  unexpected.add(0); // Ensure zero-error runs still export this metric.
  const response = http.post(url, '{}', params);
  const body = payload(response);
  const outcome = classify(response, body);
  outcomes.add(1, { outcome });
  check(response, { 'recognized result': () => outcome !== 'unexpected' });
  if (outcome === 'unexpected' || outcome === 'inventory_inconsistent') unexpected.add(1);
  if (outcome !== 'success') return;
  successes.add(1);
  committedLatency.add(response.timings.duration);
  // Successful first attempts include possible replays if RUN_ID was reused: use a fresh RUN_ID.
  if (__ITER % 5 === 0) {
    const replay = http.post(url, '{}', { ...params, tags: { name: 'draw_replay' } });
    const replayBody = payload(replay);
    const replayOutcome = classify(replay, replayBody);
    replayOutcomes.add(1, { outcome: replayOutcome });
    check(replay, {
      'replay succeeds or is temporarily protected': () => ['success', 'busy', 'rate_limited'].includes(replayOutcome),
      'successful replay returns same order': () => replayOutcome !== 'success' || replayBody.data.orderId === body.data.orderId,
    });
    if (!['success', 'busy', 'rate_limited'].includes(replayOutcome)) unexpected.add(1);
  }
}
