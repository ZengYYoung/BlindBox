import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../services/api";
import { money, date, rarityName } from "../lib/format.mjs";
import { Loading, Notice, Empty, Pagination } from "../components/UI";
export default function OrderPage() {
  const [data, setData] = useState(null),
    [page, setPage] = useState(1),
    [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    setData(null);
    setError("");
    api
      .get("/orders", { params: { page } })
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [page]);
  return (
    <div className="container narrow">
      <div className="page-heading">
        <span className="eyebrow">YOUR DISCOVERY JOURNAL</span>
        <h1>每一次惊喜，都有记录。</h1>
        <p>这里保存你的抽奖结果与购买时的商品信息。</p>
      </div>
      {error ? (
        <Notice danger>{error}</Notice>
      ) : !data ? (
        <Loading />
      ) : !data.list.length ? (
        <Empty title="你的第一份惊喜还在路上">
          <Link className="button" to="/">
            去探索盲盒 ↗
          </Link>
        </Empty>
      ) : (
        <div className="order-list">
          {data.list.map((o) => (
            <article className="order-card" key={o.id}>
              <img src={o.prizeImg} alt={o.prizeName} />
              <div className="order-main">
                <div>
                  <span className={"rarity " + o.rarity}>
                    {rarityName[o.rarity]}
                  </span>
                  <span className="order-state">{o.status}</span>
                </div>
                <h3>
                  {o.prizeName}
                  <small>{o.blindBoxName}</small>
                </h3>
                <p>
                  订单 #{o.id} · {date(o.createdAt)}
                </p>
                <code>请求 ID：{o.requestId}</code>
              </div>
              <strong>{money(o.price)}</strong>
            </article>
          ))}
        </div>
      )}
      {data && (
        <Pagination page={page} pages={data.totalPages} onChange={setPage} />
      )}
    </div>
  );
}
