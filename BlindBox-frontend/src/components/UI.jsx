import { useEffect, useRef } from "react";
export function Modal({ children, onClose, locked = false }) {
  const root = useRef(null),
    callback = useRef(onClose),
    blocked = useRef(locked);
  useEffect(() => {
    callback.current = onClose;
    blocked.current = locked;
  }, [onClose, locked]);
  useEffect(() => {
    const previous = document.activeElement,
      overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const controls = () => [
      ...root.current.querySelectorAll(
        'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
      ),
    ];
    controls()[0]?.focus();
    const keydown = (event) => {
      if (event.key === "Escape" && !blocked.current) callback.current();
      if (event.key !== "Tab") return;
      const items = controls(),
        first = items[0],
        last = items.at(-1);
      if (!first) {
        event.preventDefault();
        return;
      }
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      }
      if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", keydown);
    return () => {
      document.removeEventListener("keydown", keydown);
      document.body.style.overflow = overflow;
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  return (
    <div className="modal-backdrop" ref={root}>
      {children}
    </div>
  );
}
export function Notice({ children, danger = false }) {
  return (
    <div
      className={"notice" + (danger ? " danger" : "")}
      role={danger ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
export function Empty({ title = "这里还没有内容", children }) {
  return (
    <div className="empty">
      <span>◇</span>
      <h3>{title}</h3>
      {children}
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      正在加载…
    </div>
  );
}
export function Pagination({ page, pages, onChange }) {
  if (pages <= 1) return null;
  return (
    <div className="pagination">
      <button
        className="button secondary small"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
      >
        上一页
      </button>
      <span>
        {page} / {pages}
      </span>
      <button
        className="button secondary small"
        disabled={page >= pages}
        onClick={() => onChange(page + 1)}
      >
        下一页
      </button>
    </div>
  );
}

export function SafeImage({ src, alt, ...props }) {
  return (
    <img {...props} src={src || "/placeholder.svg"} alt={alt} onError={(event) => {
      if (event.currentTarget.getAttribute("src") !== "/placeholder.svg")
        event.currentTarget.src = "/placeholder.svg";
    }} />
  );
}
