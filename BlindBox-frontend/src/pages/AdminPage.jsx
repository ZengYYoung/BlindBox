import { useCallback, useEffect, useState } from "react";
import api, { requestId } from "../services/api";
import { cents, money, rarityName, statusName } from "../lib/format.mjs";
import { seriesNames } from "../lib/preview";
import { useAuth } from "../auth";
import { Modal, Notice, Loading, Empty, Pagination } from "../components/UI";
const assets = [
  "dolphin",
  "seahorse",
  "jellyfish",
  "turtle",
  "mermaid",
  "deer",
  "fox",
  "owl",
  "squirrel",
  "unicorn",
  "asteroid",
  "rocket",
  "moon-rover",
  "BlackHole",
  "theMilkyWay",
  "lollipop",
  "marshmallow",
  "ice-cream",
  "gummy-bear",
  "cake",
  "panda",
  "whale",
  "alien",
  "chocolate",
];
function StockEditor({ box, onDone }) {
  const { user } = useAuth();
  const storageKey = "blindbox:pending-restock:" + user.id + ":" + box.id;
  const readTicket = () => {
    try {
      return JSON.parse(localStorage.getItem(storageKey));
    } catch {
      return null;
    }
  };
  const [data, setData] = useState(null),
    [error, setError] = useState(""),
    [prize, setPrize] = useState(""),
    [quantity, setQuantity] = useState("1"),
    [busy, setBusy] = useState(false),
    [ticket, setTicket] = useState(readTicket);
  useEffect(() => {
    api
      .get("/blindboxes/" + box.id)
      .then((value) => {
        setData(value);
        setPrize(String(value.prizes[0]?.id || ""));
      })
      .catch((e) => setError(e.message));
  }, [box.id]);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    const intent = ticket || {
      prizeId: Number(prize),
      quantity: Number(quantity),
      key: requestId(),
    };
    setTicket(intent);
    localStorage.setItem(storageKey, JSON.stringify(intent));
    try {
      await api.post(
        "/admin/blindboxes/" + box.id + "/restock",
        { prizeId: intent.prizeId, quantity: intent.quantity },
        { headers: { "Idempotency-Key": intent.key } },
      );
      setTicket(null);
      localStorage.removeItem(storageKey);
      setData(await api.get("/blindboxes/" + box.id));
      await onDone();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="stock-editor">
      {error && <Notice danger>{error}</Notice>}
      {!data ? (
        !error && <Loading />
      ) : (
        <>
          <div className="inventory-table">
            {data.prizes.map((p) => (
              <div key={p.id}>
                <img src={p.prizeImg} alt="" />
                <span>{p.prizeName}</span>
                <span>{rarityName[p.rarity]}</span>
                <strong>{p.stock} 份</strong>
              </div>
            ))}
          </div>
          <form className="inline-form" onSubmit={submit}>
            <label>
              补货奖品
              <select
                value={ticket ? String(ticket.prizeId) : prize}
                disabled={!!ticket}
                onChange={(e) => setPrize(e.target.value)}
              >
                {data.prizes.map((p) => (
                  <option value={p.id} key={p.id}>
                    {p.prizeName}
                  </option>
                ))}
              </select>
            </label>
            <label>
              数量
              <input
                type="number"
                required
                min={1}
                max={1000000}
                value={ticket ? ticket.quantity : quantity}
                disabled={!!ticket}
                onChange={(e) => setQuantity(e.target.value)}
              />
            </label>
            <button className="button small" disabled={busy || !prize}>
              {busy ? "确认中…" : ticket ? "重试原补货" : "确认补货"}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
export default function AdminPage() {
  const { user } = useAuth();
  const createStorage = "blindbox:pending-create:" + user.id;
  const [pendingCreate, setPendingCreate] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(createStorage));
    } catch {
      return null;
    }
  });
  const [data, setData] = useState(null),
    [page, setPage] = useState(1),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [open, setOpen] = useState(false),
    [editing, setEditing] = useState(null),
    [expanded, setExpanded] = useState(null),
    [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    name: "",
    series: "sea",
    description: "",
    price: "59.00",
  });
  const [prizes, setPrizes] = useState([
    {
      prizeName: "海豚",
      prizeImg: "/images/dolphin.png",
      rarity: "common",
      stock: 30,
      value: 0,
    },
  ]);
  const load = useCallback(
    () =>
      api
        .get("/admin/blindboxes", { params: { page } })
        .then(setData)
        .catch((e) => setError(e.message)),
    [page],
  );
  useEffect(() => {
    load();
  }, [load]);
  const status = async (box, next) => {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      if (next === "ARCHIVED") {
        if (!window.confirm("归档后不可恢复上架，历史订单仍保留。确认归档？"))
          return;
        await api.delete("/admin/blindboxes/" + box.id);
      } else
        await api.patch("/admin/blindboxes/" + box.id + "/status", {
          status: next,
        });
      setNotice("商品状态已更新。");
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const create = async (e) => {
    e?.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (editing) {
        await api.put("/admin/blindboxes/" + editing.id, {
          ...form,
          price: cents(form.price),
          image: form.image || "/images/" + form.series + ".png",
        });
        setOpen(false);
        setEditing(null);
        setNotice("商品信息已更新，历史订单快照保持不变。");
        await load();
        return;
      }
      const intent = pendingCreate || {
        key: requestId(),
        payload: {
          ...form,
          price: cents(form.price),
          image: "/images/" + form.series + ".png",
          prizes: prizes.map((p) => ({
            ...p,
            stock: Number(p.stock),
            value: Number(p.value),
          })),
        },
      };
      localStorage.setItem(createStorage, JSON.stringify(intent));
      setPendingCreate(intent);
      await api.post("/admin/blindboxes", intent.payload, {
        headers: { "Idempotency-Key": intent.key },
      });
      localStorage.removeItem(createStorage);
      setPendingCreate(null);
      setOpen(false);
      setNotice("商品已保存为待上架，校验配货后可上架。");
      await load();
    } catch (err) {
      if (err.response?.status === 400 && !editing) {
        localStorage.removeItem(createStorage);
        setPendingCreate(null);
      }
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  const editPrize = (i, key, value) =>
    setPrizes((current) =>
      current.map((p, n) => (n === i ? { ...p, [key]: value } : p)),
    );
  return (
    <div className="container">
      <div className="community-heading">
        <div>
          <span className="eyebrow">COLLECTION OPERATIONS</span>
          <h1>管理每一份惊喜。</h1>
          <p>配置真实奖品数量，概率自动计算；无需填写固定中奖率。</p>
        </div>
        <button
          className="button"
          disabled={!!pendingCreate}
          onClick={() => {
            setEditing(null);
            setForm({
              name: "",
              series: "sea",
              description: "",
              price: "59.00",
            });
            setOpen(true);
          }}
        >
          新增盲盒 ＋
        </button>
      </div>
      {error && <Notice danger>{error}</Notice>}
      {notice && <Notice>{notice}</Notice>}
      {pendingCreate && (
        <Notice>
          商品创建结果待确认：{pendingCreate.payload.name}。
          <button
            className="button small secondary"
            disabled={busy}
            onClick={create}
          >
            使用原请求确认创建
          </button>
        </Notice>
      )}
      {!data ? (
        !error && <Loading />
      ) : !data.list.length ? (
        <Empty title="还没有商品，先配置第一个奖池。" />
      ) : (
        <div className="admin-list">
          {data.list.map((box) => (
            <article key={box.id} className="admin-card">
              <div className="admin-summary">
                <img src={box.image} alt={box.name} />
                <div>
                  <span className={"status-badge " + box.status}>
                    {statusName[box.status]}
                  </span>
                  <h3>{box.name}</h3>
                  <p>
                    {seriesNames[box.series]} · {money(box.price)} · 剩余{" "}
                    {box.stock} 份
                  </p>
                </div>
                <div className="button-row">
                  {box.status !== "ARCHIVED" && (
                    <>
                      <button
                        className="button small secondary"
                        disabled={busy || !!pendingCreate}
                        onClick={() => {
                          setEditing(box);
                          setForm({
                            name: box.name,
                            series: box.series,
                            image: box.image,
                            description: box.description || "",
                            price: (box.price / 100).toFixed(2),
                          });
                          setOpen(true);
                        }}
                      >
                        编辑信息
                      </button>
                      <button
                        className="button small secondary"
                        onClick={() =>
                          setExpanded(expanded === box.id ? null : box.id)
                        }
                      >
                        奖品 / 补货
                      </button>
                      <button
                        className="button small"
                        disabled={busy}
                        onClick={() =>
                          status(
                            box,
                            box.status === "ON_SALE" ? "OFF_SALE" : "ON_SALE",
                          )
                        }
                      >
                        {box.status === "ON_SALE" ? "下架" : "上架"}
                      </button>
                      <button
                        className="text-button danger-text"
                        disabled={busy}
                        onClick={() => status(box, "ARCHIVED")}
                      >
                        归档
                      </button>
                    </>
                  )}
                </div>
              </div>
              {expanded === box.id && <StockEditor box={box} onDone={load} />}
            </article>
          ))}
        </div>
      )}
      {data && (
        <Pagination page={page} pages={data.totalPages} onChange={setPage} />
      )}
      {open && (
        <Modal onClose={() => setOpen(false)} locked={busy}>
          <section
            className="modal wide"
            role="dialog"
            aria-modal="true"
            aria-label="新增盲盒"
          >
            <button
              className="modal-close"
              disabled={busy}
              onClick={() => setOpen(false)}
              aria-label="关闭"
            >
              ×
            </button>
            <span className="eyebrow">CREATE A NEW COLLECTION</span>
            <h2>
              {editing ? "更新这份收藏的介绍。" : "把你的新世界，装进奖池。"}
            </h2>
            <form onSubmit={create}>
              <div className="form-grid">
                <label>
                  商品名称
                  <input
                    required
                    maxLength={100}
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                  />
                </label>
                <label>
                  系列
                  <select
                    value={form.series}
                    onChange={(e) =>
                      setForm({ ...form, series: e.target.value })
                    }
                  >
                    {Object.entries(seriesNames).map(([key, name]) => (
                      <option key={key} value={key}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  价格（元）
                  <input
                    required
                    inputMode="decimal"
                    value={form.price}
                    onChange={(e) =>
                      setForm({ ...form, price: e.target.value })
                    }
                  />
                </label>
                <label>
                  描述
                  <input
                    maxLength={500}
                    value={form.description}
                    onChange={(e) =>
                      setForm({ ...form, description: e.target.value })
                    }
                  />
                </label>
              </div>
              {!editing && (
                <>
                  <div className="section-heading">
                    <h3>奖品配货</h3>
                    <span className="caption">
                      共 {prizes.reduce((n, p) => n + Number(p.stock), 0)} 份
                    </span>
                  </div>
                  <div className="prize-editor">
                    {prizes.map((p, i) => (
                      <div key={i}>
                        <img src={p.prizeImg} alt="奖品预览" />
                        <label>
                          奖品名称
                          <input
                            required
                            maxLength={100}
                            value={p.prizeName}
                            onChange={(e) =>
                              editPrize(i, "prizeName", e.target.value)
                            }
                          />
                        </label>
                        <label>
                          图片
                          <select
                            value={p.prizeImg}
                            onChange={(e) =>
                              editPrize(i, "prizeImg", e.target.value)
                            }
                          >
                            {assets.map((a) => (
                              <option key={a} value={"/images/" + a + ".png"}>
                                {a}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label>
                          稀有度
                          <select
                            value={p.rarity}
                            onChange={(e) =>
                              editPrize(i, "rarity", e.target.value)
                            }
                          >
                            {Object.entries(rarityName).map(([k, name]) => (
                              <option key={k} value={k}>
                                {name}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label>
                          数量
                          <input
                            type="number"
                            required
                            min={0}
                            max={1000000}
                            value={p.stock}
                            onChange={(e) =>
                              editPrize(i, "stock", e.target.value)
                            }
                          />
                        </label>
                        <button
                          type="button"
                          className="text-button"
                          disabled={prizes.length <= 1}
                          aria-label="移除奖品"
                          onClick={() =>
                            setPrizes((current) =>
                              current.filter((_, n) => n !== i),
                            )
                          }
                        >
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    className="button secondary small"
                    disabled={prizes.length >= 50}
                    onClick={() =>
                      setPrizes([
                        ...prizes,
                        {
                          prizeName: "",
                          prizeImg: "/images/dolphin.png",
                          rarity: "common",
                          stock: 0,
                          value: 0,
                        },
                      ])
                    }
                  >
                    添加奖品 ＋
                  </button>
                </>
              )}
              {error && <Notice danger>{error}</Notice>}
              <button className="button full" disabled={busy}>
                {busy
                  ? "保存中…"
                  : editing
                    ? "保存商品信息 ↗"
                    : "保存奖池，待上架 ↗"}
              </button>
            </form>
          </section>
        </Modal>
      )}
    </div>
  );
}
