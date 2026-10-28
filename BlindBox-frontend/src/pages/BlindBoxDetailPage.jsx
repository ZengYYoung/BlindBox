import { useCallback, useEffect, useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import api, { requestId } from "../services/api";
import { useAuth } from "../auth";
import { money, rarityName } from "../lib/format.mjs";
import { previewEnabled, previewDetail, seriesNames } from "../lib/preview";
import { Modal, Loading, Notice, SafeImage } from "../components/UI";
import { shouldClearPendingDraw, retryAfterSeconds, drawFailureMessage } from "../lib/degradation.mjs";
export default function BlindBoxDetailPage() {
  const { id } = useParams(),
    navigate = useNavigate(),
    { user, refresh } = useAuth();
  const [data, setData] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null),
    [pending, setPending] = useState(null);
  const [retrySeconds, setRetrySeconds] = useState(0);
  useEffect(() => {
    if (retrySeconds <= 0) return;
    const timer = setTimeout(() => setRetrySeconds((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => clearTimeout(timer);
  }, [retrySeconds]);
  const storageKey = user ? "blindbox:pending-draw:" + user.id : null;
  const load = useCallback(async () => {
    const value = previewEnabled
      ? previewDetail(id)
      : await api.get("/blindboxes/" + id);
    setData(value);
  }, [id]);
  useEffect(() => {
    let active = true;
    setData(null);
    setError("");
    const promise = previewEnabled
      ? Promise.resolve().then(() => previewDetail(id))
      : api.get("/blindboxes/" + id);
    promise
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [id]);
  useEffect(() => {
    try {
      setPending(
        storageKey ? JSON.parse(localStorage.getItem(storageKey)) : null,
      );
    } catch {
      setPending(null);
    }
  }, [storageKey]);
  const clearPending = () => {
    if (storageKey) localStorage.removeItem(storageKey);
    setPending(null);
  };
  const success = async (value) => {
    clearPending();
    setResult(value);
    setError("");
    await Promise.allSettled([load(), refresh()]);
  };
  const draw = async () => {
    if (!user) {
      navigate("/login", { state: { from: "/blindboxes/" + id } });
      return;
    }
    if (previewEnabled || busy || retrySeconds > 0) return;
    let saved;
    try {
      const raw = localStorage.getItem(storageKey);
      saved = raw ? JSON.parse(raw) : null;
      if (saved && (!Number.isSafeInteger(saved.boxId) || saved.boxId < 1 ||
          typeof saved.requestId !== "string" || !/^[A-Za-z0-9_-]{8,64}$/.test(saved.requestId)))
        throw new Error("invalid saved request");
    } catch {
      setError("无法读取本地待确认请求，请恢复浏览器存储后再操作。");
      return;
    }
    const existing = saved || pending;
    if (existing && existing.boxId !== Number(id)) {
      setPending(existing);
      setError("请先确认另一笔抽奖结果。");
      return;
    }
    const isRetry = !!existing;
    const ticket = existing || { boxId: Number(id), requestId: requestId() };
    // Persist before sending: network timeout does not mean the server rolled back.
    try { localStorage.setItem(storageKey, JSON.stringify(ticket)); }
    catch { setError("无法保存请求，请恢复浏览器存储后再操作。"); return; }
    setPending(ticket);
    setBusy(true);
    setError("");
    try {
      await success(
        await api.post(
          "/blindboxes/" + id + "/draw",
          {},
          { headers: { "Idempotency-Key": ticket.requestId } },
        ),
      );
    } catch (e) {
      if (shouldClearPendingDraw(e, isRetry)) clearPending();
      setRetrySeconds(retryAfterSeconds(e));
      setError(drawFailureMessage(e, isRetry));
    } finally {
      setBusy(false);
    }
  };
  const confirm = async () => {
    if (!pending || busy) return;
    setBusy(true);
    try {
      await success(await api.get("/orders/request/" + pending.requestId));
    } catch (e) {
      setError(
        e.response?.status === 404
          ? "暂未确认结果。可以稍后查询，或使用同一请求重试；不会重复扣款。"
          : e.message,
      );
    } finally {
      setBusy(false);
    }
  };
  if (!data)
    return (
      <div className="container">
        {error ? (
          <Notice danger>
            {error} <Link to="/">返回商品列表</Link>
          </Notice>
        ) : (
          <Loading />
        )}
      </div>
    );
  const { box, prizes, totalStock } = data;
  const otherPending = pending && pending.boxId !== Number(id);
  return (
    <div className="container">
      <div className="breadcrumbs">
        <Link to="/">探索盲盒</Link>
        <span>/</span>
        <span>{seriesNames[box.series]}</span>
      </div>
      {previewEnabled && (
        <Notice>
          离线样例：概率由样例库存计算，交易功能在连接后端后可用。
        </Notice>
      )}
      {error && <Notice danger>{error}</Notice>}
      {pending && (
        <Notice>
          有一笔结果待确认的抽奖。请求 ID：<code>{pending.requestId}</code>
          <div className="button-row">
            <button
              className="button small secondary"
              disabled={busy}
              onClick={confirm}
            >
              查询原结果
            </button>
            {otherPending ? (
              <Link
                className="button small"
                to={"/blindboxes/" + pending.boxId}
              >
                回到原盲盒
              </Link>
            ) : (
              <button
                className="button small secondary"
                disabled={busy || previewEnabled || retrySeconds > 0}
                onClick={draw}
              >
                {retrySeconds > 0 ? `请等待 ${retrySeconds} 秒后重试` : "同一请求重试"}
              </button>
            )}
          </div>
        </Notice>
      )}
      <section className="detail-layout">
        <div className={"detail-picture " + box.series}>
          <SafeImage src={box.image} alt={box.name} />
          <span className="picture-caption">
            {seriesNames[box.series]} · KEEP THE JOY
          </span>
        </div>
        <div className="detail-copy">
          <div className="eyebrow">A LITTLE SURPRISE, JUST FOR YOU</div>
          <h1>{box.name}</h1>
          <p>{box.description}</p>
          <div className="detail-price">
            {money(box.price)}
            <span> / 每盒</span>
          </div>
          <div className="detail-facts">
            <div>
              <small>当前奖池</small>
              <strong>
                {totalStock}
                <span> 份</span>
              </strong>
            </div>
            <div>
              <small>可收藏款式</small>
              <strong>
                {prizes.length}
                <span> 款</span>
              </strong>
            </div>
          </div>
          <div className="probability-bars">
            {Object.entries(data.rarityCounts).map(([rarity, count]) => (
              <div key={rarity}>
                <span className={"rarity " + rarity}>{rarityName[rarity]}</span>
                <div className="bar">
                  <i
                    className={rarity}
                    style={{
                      width:
                        (totalStock ? (count / totalStock) * 100 : 0) + "%",
                    }}
                  />
                </div>
                <strong>
                  {(totalStock ? (count / totalStock) * 100 : 0).toFixed(1)}%
                </strong>
              </div>
            ))}
          </div>
          <button
            className="button draw-button"
            disabled={busy || totalStock === 0 || previewEnabled || !!pending}
            onClick={draw}
          >
            {busy
              ? "正在确认你的惊喜…"
              : previewEnabled
                ? "离线预览 · 连接后端后抽奖"
                : totalStock === 0
                  ? "本期奖池已售罄"
                  : "开启我的盲盒"}
            <span>↗</span>
          </button>
          <p className="caption">
            每个剩余实体奖品等机会抽中。展示概率为查询快照，实际概率以处理抽奖时的库存为准。抽奖会扣除账户余额。
          </p>
        </div>
      </section>
      <section className="prize-section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">WHAT COULD BE YOURS</span>
            <h2>下一份收藏，会是谁？</h2>
          </div>
          <span className="caption">概率随剩余库存变化</span>
        </div>
        <div className="prize-grid">
          {prizes.map((p) => (
            <article
              className={"prize-card" + (p.stock === 0 ? " sold-out" : "")}
              key={p.id}
            >
              <SafeImage src={p.prizeImg} alt={p.prizeName} loading="lazy" />
              <span className={"rarity " + p.rarity}>
                {rarityName[p.rarity]}
              </span>
              <h3>{p.prizeName}</h3>
              <div>
                <strong>{(p.probability * 100).toFixed(2)}%</strong>
                <small>{p.stock ? "剩余 " + p.stock + " 份" : "已抽完"}</small>
              </div>
            </article>
          ))}
        </div>
      </section>
      {result && (
        <Modal onClose={() => setResult(null)}>
          <section
            className="modal result-modal"
            role="dialog"
            aria-modal="true"
            aria-label="抽奖结果"
          >
            <button
              className="modal-close"
              aria-label="关闭结果"
              onClick={() => setResult(null)}
            >
              ×
            </button>
            <span className="eyebrow">YOUR LITTLE DISCOVERY</span>
            <h2>这份惊喜，属于你。</h2>
            <SafeImage src={result.prizeImg} alt={result.prizeName} />
            <span className={"rarity " + result.rarity}>
              {rarityName[result.rarity]}
            </span>
            <h3>{result.prizeName}</h3>
            <p className="caption">
              订单 #{result.orderId} · {money(result.price)}
            </p>
            <div className="button-row">
              <button
                className="button secondary"
                onClick={() => setResult(null)}
              >
                继续探索
              </button>
              <Link className="button" to="/orders">
                查看我的订单 ↗
              </Link>
            </div>
          </section>
        </Modal>
      )}
    </div>
  );
}
