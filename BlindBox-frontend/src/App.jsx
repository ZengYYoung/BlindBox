import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
  Link,
  useLocation,
} from "react-router-dom";
import AuthProvider from "./components/AuthProvider";
import Navbar from "./components/Navbar";
import { Loading, Notice } from "./components/UI";
import { useAuth } from "./auth";
import BlindBoxListPage from "./pages/BlindBoxListPage";
import BlindBoxDetailPage from "./pages/BlindBoxDetailPage";
import LoginRegisterPage from "./pages/LoginRegisterPage";
import OrderPage from "./pages/OrderPage";
import AccountPage from "./pages/AccountPage";
import PlayerShowPage from "./pages/PlayerShowPage";
import AdminPage from "./pages/AdminPage";
function Guard({ admin = false }) {
  const { user, loading, error, refresh } = useAuth();
  const location = useLocation();
  if (loading) return <Loading />;
  if (!user && localStorage.getItem("token") && error)
    return (
      <Notice danger>
        {error}{" "}
        <button className="text-button" onClick={refresh}>
          重新连接
        </button>
      </Notice>
    );
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (admin && user.role !== "ADMIN")
    return <Notice danger>此页面需要管理员权限。</Notice>;
  return <Outlet />;
}
function Layout() {
  return (
    <>
      <Navbar />
      <main>
        <Outlet />
      </main>
      <footer>
        <Link className="footer-brand" to="/">
          BlindBox
        </Link>
        <p>每一份收藏，都有自己的故事。</p>
        <span>库存决定概率 · 收藏留住惊喜</span>
      </footer>
    </>
  );
}
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<BlindBoxListPage />} />
            <Route path="draw" element={<Navigate to="/" replace />} />
            <Route path="blindboxes/:id" element={<BlindBoxDetailPage />} />
            <Route path="login" element={<LoginRegisterPage />} />
            <Route path="register" element={<LoginRegisterPage />} />
            <Route path="community" element={<PlayerShowPage />} />
            <Route element={<Guard />}>
              <Route path="account" element={<AccountPage />} />
              <Route path="orders" element={<OrderPage />} />
            </Route>
            <Route element={<Guard admin />}>
              <Route path="admin" element={<AdminPage />} />
            </Route>
            <Route
              path="*"
              element={
                <Notice danger>
                  页面不存在。<Link to="/">返回首页</Link>
                </Notice>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
