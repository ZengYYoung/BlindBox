import { useEffect, useState } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import api from "../services/api";
import { useAuth } from "../auth";
import { Notice } from "../components/UI";
export default function LoginRegisterPage() {
  const location = useLocation(),
    navigate = useNavigate(),
    { login, user } = useAuth();
  const [register, setRegister] = useState(location.pathname === "/register");
  const [account, setAccount] = useState(""),
    [password, setPassword] = useState(""),
    [repeat, setRepeat] = useState("");
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    setRegister(location.pathname === "/register");
    setError("");
  }, [location.pathname]);
  const submit = async (e) => {
    e.preventDefault();
    setError("");
    if (register && password !== repeat) {
      setError("两次密码不一致");
      return;
    }
    setBusy(true);
    try {
      if (register) await api.post("/auth/register", { account, password });
      await login(account, password);
      navigate(location.state?.from || "/account", { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  if (user)
    return (
      <div className="container">
        <Notice>
          你已登录。<Link to="/account">进入我的收藏</Link>
        </Notice>
      </div>
    );
  return (
    <div className="container auth-layout">
      <div className="auth-art">
        <img src="/images/forest.png" alt="森林系列盲盒" />
        <div>
          <span className="eyebrow">MAKE ROOM FOR A LITTLE JOY</span>
          <h2>
            给生活，
            <br />
            留一点惊喜。
          </h2>
        </div>
      </div>
      <section className="auth-form">
        <span className="eyebrow">WELCOME TO BLINDBOX</span>
        <h1>{register ? "开启你的收藏故事" : "欢迎回来，收藏家。"}</h1>
        <p>登录后管理收藏、查询订单，与朋友分享惊喜。</p>
        {error && <Notice danger>{error}</Notice>}
        <form onSubmit={submit}>
          <label>
            账号
            <input
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              minLength={register ? 3 : 1}
              maxLength={50}
              autoComplete="username"
              required
              placeholder={register ? "3–50 位账号" : "请输入账号"}
            />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={register ? 8 : 1}
              maxLength={64}
              autoComplete={register ? "new-password" : "current-password"}
              required
              placeholder={register ? "8–64 位密码" : "请输入密码"}
            />
          </label>
          {register && (
            <label>
              确认密码
              <input
                type="password"
                value={repeat}
                onChange={(e) => setRepeat(e.target.value)}
                autoComplete="new-password"
                required
              />
            </label>
          )}
          <button className="button full" disabled={busy}>
            {busy ? "正在处理…" : register ? "注册并登录 ↗" : "登录 ↗"}
          </button>
        </form>
        <p className="caption">
          {register ? "已经有账号？" : "还没有账号？"}{" "}
          <Link to={register ? "/login" : "/register"} state={location.state}>
            {register ? "直接登录" : "加入收藏"}
          </Link>
        </p>
      </section>
    </div>
  );
}
