import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../services/api";
import { money } from "../lib/format.mjs";
import { previewEnabled, previewBoxes, seriesNames } from "../lib/preview";
import { Notice, Empty, Loading, Pagination, SafeImage } from "../components/UI";
import { saveCatalogSnapshot, readCatalogSnapshot } from "../lib/degradation.mjs";

function sessionStore() {
  try { return window.sessionStorage; } catch { return null; }
}
export default function BlindBoxListPage() {
  const [series, setSeries] = useState(""),
    [search, setSearch] = useState(""),
    [query, setQuery] = useState("");
  const [page, setPage] = useState(1),
    [data, setData] = useState(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    const snapshotQuery = JSON.stringify([series, query, page]);
    const load = previewEnabled
      ? Promise.resolve({
          list: previewBoxes.filter(
            (b) => (!series || b.series === series) && b.name.includes(query),
          ),
          totalPages: 1,
        })
      : api.get("/blindboxes", {
          params: { series, search: query, page, pageSize: 12 },
        });
    load
      .then((value) => {
        if (active) {
          setData(value);
          if (!previewEnabled) saveCatalogSnapshot(sessionStore(), snapshotQuery, value);
        }
      })
      .catch((e) => {
        if (active) {
          setData(previewEnabled ? null : readCatalogSnapshot(sessionStore(), snapshotQuery));
          setError(e.message);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [series, query, page, reload]);
  return (
    <div className="container">
      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow">
            <span className="dot" /> THE LITTLE JOY OF DISCOVERY
          </div>
          <h1>
            打开一点未知，
            <br />
            收藏一份<span className="serif green">惊喜。</span>
          </h1>
          <p>
            从深海到星河，与你喜欢的小世界相遇。
            <br />
            每一只盲盒，都藏着下一份期待。
          </p>
          <a className="button" href="#catalog">
            探索所有系列 <span>↗</span>
          </a>
          <div className="hero-note">
            <span>◇</span>
            <div>
              概率来自真实剩余库存
              <small>每抽走一份，下一份的可能性也会改变。</small>
            </div>
          </div>
        </div>
        <div className="hero-art">
          <img
            src="/images/sea.png"
            alt="海洋系列蓝色盲盒"
            fetchPriority="high"
          />
          <div className="floating-label">
            <span>NEW DISCOVERY</span>
            <strong>海洋的秘密，等你打开</strong>
            <small>Ocean Collection / 海洋系列</small>
          </div>
          <span className="art-stamp">
            KEEP
            <br />
            THE JOY.
          </span>
        </div>
      </section>
      {previewEnabled && (
        <Notice>
          离线界面预览：商品和库存为样例，仅用于查看图片与布局；抽奖、账户及社区需要连接后端。
        </Notice>
      )}
      <section id="catalog" className="catalog">
        <div className="section-heading">
          <div>
            <span className="eyebrow">CURATED COLLECTIONS</span>
            <h2>
              找到你的心动系列<span className="green"> /</span>
            </h2>
          </div>
          <p>小小的收藏，大大的快乐。</p>
        </div>
        <div className="catalog-toolbar">
          <div className="tabs">
            <button
              className={!series ? "active" : ""}
              onClick={() => {
                setSeries("");
                setPage(1);
              }}
            >
              全部系列
            </button>
            {Object.entries(seriesNames).map(([key, name]) => (
              <button
                key={key}
                className={series === key ? "active" : ""}
                onClick={() => {
                  setSeries(key);
                  setPage(1);
                }}
              >
                {name}
              </button>
            ))}
          </div>
          <form
            className="search"
            onSubmit={(e) => {
              e.preventDefault();
              setQuery(search.trim());
              setPage(1);
            }}
          >
            <input
              aria-label="搜索盲盒"
              placeholder="寻找你的下一份收藏"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button aria-label="搜索">⌕</button>
          </form>
        </div>
        {error && (
          <Notice danger>
            商品实时信息暂不可用。{data?.stale ? "以下是最近一次的商品展示信息，价格与库存暂不展示。" : error}
            {data?.stale && <span> 保存时间：{new Date(data.savedAt).toLocaleString("zh-CN")}。</span>}
            <div className="button-row"><button className="button small secondary" onClick={() => setReload((n) => n + 1)}>重新加载</button></div>
          </Notice>
        )}
        {loading ? (
          <Loading />
        ) : error && !data?.stale ? null : !data?.list.length ? (
          <Empty title="还没有找到这个系列">
            <p>{data?.stale ? "上次没有展示商品，请稍后重新加载。" : "试试其他关键词，或等待管理员上架。"}</p>
          </Empty>
        ) : (
          <div className="product-grid">
            {data.list.map((box) => {
              const Card = data.stale ? "div" : Link;
              return (
              <Card
                key={box.id}
                className="product-card"
                {...(data.stale ? {} : { to: "/blindboxes/" + box.id })}
              >
                <div className={"product-image " + box.series}>
                  <SafeImage src={box.image} alt={box.name} loading="lazy" />
                  <span className="product-tag">
                    {seriesNames[box.series] || box.series}
                  </span>
                  <span className="card-arrow">↗</span>
                </div>
                <div className="product-info">
                  <div>
                    <h3>{box.name}</h3>
                    <p>{box.description}</p>
                  </div>
                  <div className="product-bottom">
                    <strong>
                      {data.stale ? "价格暂不可用" : money(box.price)}
                      {!data.stale && <small> / 盒</small>}
                    </strong>
                    <span>
                      {data.stale ? "实时库存暂不可用" : box.stock > 0
                        ? "剩余 " + box.stock + " 份"
                        : "本期已售罄"}
                    </span>
                  </div>
                </div>
              </Card>
            );})}
          </div>
        )}
        {data && !data.stale && !error && (
          <Pagination page={page} pages={data.totalPages} onChange={setPage} />
        )}
        <p className="caption">
          列表库存为查询快照；可抽数量与概率以抽奖处理时的库存为准。
        </p>
      </section>
      <section className="principles">
        <div>
          <span>01 / REAL INVENTORY</span>
          <h3>每一份，都在奖池里</h3>
          <p>按剩余数量抽取，售罄的奖品不再参与。</p>
        </div>
        <div>
          <span>02 / YOUR COLLECTION</span>
          <h3>让喜欢，有迹可循</h3>
          <p>抽奖结果进入订单，收藏也有了自己的故事。</p>
        </div>
        <div>
          <span>03 / SHARE THE JOY</span>
          <h3>把惊喜，分享出去</h3>
          <p>在收藏社区，遇见和你一样的收藏家。</p>
        </div>
      </section>
    </div>
  );
}
