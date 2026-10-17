import { Link, NavLink } from "react-router-dom";
import { useAuth } from "../auth";
import { money } from "../lib/format.mjs";
export default function Navbar() {
  const { user, logout } = useAuth();
  return (
    <header className="header">
      <Link className="brand" to="/">
        <span className="brand-mark">
          B<span>·</span>
        </span>
        <span>
          BlindBox<small>一点未知，一份惊喜</small>
        </span>
      </Link>
      <nav aria-label="主导航">
        <NavLink to="/" end>
          探索盲盒
        </NavLink>
        <NavLink to="/community">收藏社区</NavLink>
        <NavLink to="/orders">我的订单</NavLink>
        {user?.role === "ADMIN" && <NavLink to="/admin">管理工作台</NavLink>}
      </nav>
      <div className="nav-user">
        {user ? (
          <>
            <Link to="/account" className="account-link">
              {money(user.balance)} <span>{user.account.slice(0, 10)}</span>
            </Link>
            <button className="text-button" onClick={logout}>
              退出
            </button>
          </>
        ) : (
          <Link className="button small" to="/login">
            登录 / 注册 <span>↗</span>
          </Link>
        )}
      </div>
    </header>
  );
}
