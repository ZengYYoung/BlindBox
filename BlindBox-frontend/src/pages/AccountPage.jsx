import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth";
import api, { requestId } from "../services/api";
import { cents, money, rarityName } from "../lib/format.mjs";
import { Loading, Notice, Empty, Pagination } from "../components/UI";
export default function AccountPage() {
  const { user, refresh } = useAuth();
  const [data, setData] = useState(null),
    [page, setPage] = useState(1),
    [error, setError] = useState("");
  const [amount, setAmount] = useState(""),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState("");
  const [notifications, setNotifications] = useState(null);
  const [notificationError, setNotificationError] = useState("");
  const loadNotifications = useCallback(() => {
    setNotificationError("");
    api.get("/user/notifications").then(setNotifications).catch(() => setNotificationError("通知暂时不可用"));
  }, []);
  const storageKey = "blindbox:pending-recharge:" + user.id;
  const [pending, setPending] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(storageKey));
    } catch {
      return null;
    }
  });
  const load = useCallback(
    () =>
      api
        .get("/user/collection", { params: { page } })
        .then(setData)
        .catch((e) => setError(e.message)),
    [page],
  );
  useEffect(() => {
    load();
    refresh();
  }, [load, refresh]);
  useEffect(() => { loadNotifications(); }, [loadNotifications]);
  const recharge = async (e) => {
    e.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const ticket = pending || {
        requestId: requestId(),
        amount: cents(amount),
      };
      localStorage.setItem(storageKey, JSON.stringify(ticket));
      setPending(ticket);
      await api.post(
        "/user/recharge",
        { amount: ticket.amount },
        { headers: { "Idempotency-Key": ticket.requestId } },
      );
      localStorage.removeItem(storageKey);
      setPending(null);
      setAmount("");
      await refresh();
      setNotice("开发余额已入账。");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="container">
      <div className="account-heading">
        <div>
          <span className="eyebrow">YOUR PERSONAL COLLECTION</span>
          <h1>你好，{user.account}。</h1>
          <p>喜欢的事物，值得好好收藏。</p>
        </div>
        <Link className="button secondary" to="/orders">
          查看订单 ↗
        </Link>
      </div>
      {error && <Notice danger>{error}</Notice>}
      {notice && <Notice>{notice}</Notice>}
      <section className="notification-panel">
        <div className="section-heading"><h2>抽奖通知</h2><button className="button secondary" onClick={loadNotifications}>刷新</button></div>
        {notificationError ? <Notice danger>{notificationError}</Notice> : notifications === null ? <Loading /> :
          notifications.length ? <ul>{notifications.map((item) => <li key={item.id}>{item.content} <small>{String(item.createdAt || "").replace("T", " ")}</small></li>)}</ul> :
          <p className="caption">暂无通知。抽奖成功后通知可能稍有延迟。</p>}
      </section>
      <div className="account-grid">
        <section className="wallet-card">
          <span className="eyebrow">MY BALANCE</span>
          <h2>{money(user.balance)}</h2>
          <p>账户余额</p>
          {user.demoRechargeEnabled ? (
            <form onSubmit={recharge}>
              <Notice>开发充值：用于本地演示，不代表真实支付。</Notice>
              {pending && (
                <p className="caption">
                  有待确认入账 {money(pending.amount)}，重试使用原请求 ID。
                </p>
              )}
              <label>
                充值金额（元）
                <input
                  inputMode="decimal"
                  value={amount}
                  disabled={!!pending || busy}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="例如 100.00"
                  required={!pending}
                />
              </label>
              <button className="button full" disabled={busy}>
                {busy ? "确认中…" : pending ? "确认原充值" : "增加开发余额"}
              </button>
            </form>
          ) : (
            <p className="caption">充值入口尚未开放，接入支付服务后可用。</p>
          )}
        </section>
        <div className="collection-area">
          <div className="stat-grid">
            <div>
              <strong>{data?.stats.totalItems || 0}</strong>
              <span>已开启盲盒</span>
            </div>
            <div>
              <strong>{data?.stats.uniqueItems || 0}</strong>
              <span>不同收藏</span>
            </div>
            <div>
              <strong>{money(data?.stats.totalSpent)}</strong>
              <span>累计消费</span>
            </div>
          </div>
          <div className="section-heading">
            <h2>我的收藏</h2>
            <span className="caption">按奖品汇总</span>
          </div>
          {!data ? (
            !error && <Loading />
          ) : !data.list.length ? (
            <Empty title="收藏柜，等你来填满">
              <Link to="/" className="button">
                发现第一份收藏 ↗
              </Link>
            </Empty>
          ) : (
            <div className="collection-grid">
              {data.list.map((p) => (
                <article className="collection-card" key={p.prizeId}>
                  <img src={p.prizeImg} alt={p.prizeName} loading="lazy" />
                  <span className={"rarity " + p.rarity}>
                    {rarityName[p.rarity]}
                  </span>
                  <h3>{p.prizeName}</h3>
                  <small>已收藏 × {p.quantity}</small>
                </article>
              ))}
            </div>
          )}{" "}
          {data && (
            <Pagination
              page={page}
              pages={data.totalPages}
              onChange={setPage}
            />
          )}
        </div>
      </div>
    </div>
  );
}
