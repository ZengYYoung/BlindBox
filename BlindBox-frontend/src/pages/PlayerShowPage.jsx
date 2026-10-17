import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth";
import api from "../services/api";
import { date } from "../lib/format.mjs";
import { Modal, Loading, Notice, Empty, Pagination } from "../components/UI";
function Comments({ id }) {
  const { user } = useAuth(),
    [page, setPage] = useState(1),
    [data, setData] = useState(null);
  const [text, setText] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const load = useCallback(
    () =>
      api
        .get("/playershows/" + id + "/comments", { params: { page } })
        .then(setData)
        .catch((e) => setError(e.message)),
    [id, page],
  );
  useEffect(() => {
    load();
  }, [load]);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.post("/playershows/" + id + "/comment", { content: text });
      setText("");
      setPage(1);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="comments">
      {error && <Notice danger>{error}</Notice>}
      {data?.list.map((c) => (
        <div key={c.id}>
          <strong>{c.username}</strong>
          <span>{date(c.createdAt)}</span>
          <p>{c.content}</p>
        </div>
      ))}
      {data && !data.list.length && (
        <p className="caption">还没有评论，分享你的想法吧。</p>
      )}
      {data && (
        <Pagination page={page} pages={data.totalPages} onChange={setPage} />
      )}
      {user ? (
        <form onSubmit={submit}>
          <input
            aria-label="评论内容"
            placeholder="写下你的想法…"
            required
            maxLength={1000}
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <button className="button small" disabled={busy}>
            发送
          </button>
        </form>
      ) : (
        <Link to="/login">登录后参与讨论</Link>
      )}
    </div>
  );
}
export default function PlayerShowPage() {
  const { user } = useAuth();
  const [data, setData] = useState(null),
    [page, setPage] = useState(1),
    [error, setError] = useState("");
  const [open, setOpen] = useState(false),
    [content, setContent] = useState(""),
    [images, setImages] = useState([]);
  const [busy, setBusy] = useState(false),
    [expanded, setExpanded] = useState({}),
    [liking, setLiking] = useState({});
  const load = useCallback(
    () =>
      api
        .get("/playershows", { params: { page } })
        .then(setData)
        .catch((e) => setError(e.message)),
    [page],
  );
  useEffect(() => {
    load();
  }, [load]);
  const upload = async (e) => {
    const files = Array.from(e.target.files);
    if (
      images.length + files.length > 6 ||
      files.some((f) => f.size > 5 * 1024 * 1024)
    ) {
      setError("最多 6 张图片，每张不能超过 5MB。");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const uploaded = [];
      for (const file of files) {
        const form = new FormData();
        form.append("file", file);
        uploaded.push((await api.post("/images", form)).url);
      }
      setImages((current) => [...current, ...uploaded]);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
      e.target.value = "";
    }
  };
  const publish = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.post("/playershows", { content, images });
      setContent("");
      setImages([]);
      setOpen(false);
      setPage(1);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  const like = async (show) => {
    if (!user || liking[show.id]) return;
    setLiking((current) => ({ ...current, [show.id]: true }));
    try {
      const path = "/playershows/" + show.id + "/like";
      const result = show.liked ? await api.delete(path) : await api.post(path);
      setData((current) => ({
        ...current,
        list: current.list.map((s) =>
          s.id === show.id ? { ...s, ...result } : s,
        ),
      }));
    } catch (err) {
      setError(err.message);
    } finally {
      setLiking((current) => ({ ...current, [show.id]: false }));
    }
  };
  return (
    <div className="container narrow">
      <div className="community-heading">
        <div>
          <span className="eyebrow">LITTLE JOYS, SHARED</span>
          <h1>让惊喜，在这里相遇。</h1>
          <p>分享收藏与故事，认识同样热爱小美好的朋友。</p>
        </div>
        {user ? (
          <button className="button" onClick={() => setOpen(true)}>
            分享我的收藏 ↗
          </button>
        ) : (
          <Link className="button" to="/login">
            登录后分享 ↗
          </Link>
        )}
      </div>
      {error && <Notice danger>{error}</Notice>}
      {!data ? (
        !error && <Loading />
      ) : !data.list.length ? (
        <Empty title="第一篇收藏故事，等你来写。" />
      ) : (
        <div className="show-list">
          {data.list.map((show) => (
            <article className="show-card" key={show.id}>
              <div className="show-author">
                <span className="avatar">
                  {show.username.slice(0, 1).toUpperCase()}
                </span>
                <div>
                  <strong>{show.username}</strong>
                  <small>{date(show.createdAt)}</small>
                </div>
              </div>
              <p className="show-text">{show.content}</p>
              <div className="show-images">
                {show.images.map((url, i) => (
                  <a key={url + i} href={url} target="_blank" rel="noreferrer">
                    <img src={url} alt={"收藏图片 " + (i + 1)} loading="lazy" />
                  </a>
                ))}
              </div>
              <div className="show-actions">
                <button
                  disabled={!user || liking[show.id]}
                  className={show.liked ? "liked" : ""}
                  onClick={() => like(show)}
                >
                  {show.liked ? "♥" : "♡"} {show.likes} 喜欢
                </button>
                <button
                  onClick={() =>
                    setExpanded((current) => ({
                      ...current,
                      [show.id]: !current[show.id],
                    }))
                  }
                >
                  ◌ {expanded[show.id] ? "收起评论" : "查看评论"}
                </button>
              </div>
              {expanded[show.id] && <Comments id={show.id} />}
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
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label="发布晒单"
          >
            <button
              className="modal-close"
              disabled={busy}
              onClick={() => setOpen(false)}
              aria-label="关闭"
            >
              ×
            </button>
            <span className="eyebrow">SHARE YOUR DISCOVERY</span>
            <h2>记录这份小美好。</h2>
            <form onSubmit={publish}>
              <label>
                你的收藏故事
                <textarea
                  required
                  maxLength={2000}
                  rows={5}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="今天抽中了什么？"
                />
              </label>
              <label>
                图片 · PNG / JPEG，最多 6 张
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  multiple
                  disabled={busy}
                  onChange={upload}
                />
              </label>
              <div className="upload-previews">
                {images.map((url, i) => (
                  <button
                    type="button"
                    key={url}
                    aria-label="移除图片"
                    onClick={() =>
                      setImages((current) => current.filter((_, n) => n !== i))
                    }
                  >
                    <img src={url} alt="待发布图片" />
                    <span>×</span>
                  </button>
                ))}
              </div>
              {error && <Notice danger>{error}</Notice>}
              <button className="button full" disabled={busy}>
                {busy ? "正在处理…" : "发布收藏故事 ↗"}
              </button>
            </form>
          </section>
        </Modal>
      )}
    </div>
  );
}
