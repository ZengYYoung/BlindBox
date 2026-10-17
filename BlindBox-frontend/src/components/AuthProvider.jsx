import { useCallback, useEffect, useState } from "react";
import api from "../services/api";
import { AuthContext } from "../auth";
export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(!!localStorage.getItem("token"));
  const [error, setError] = useState("");
  const logout = useCallback(() => {
    localStorage.removeItem("token");
    setUser(null);
    setError("");
  }, []);
  const refresh = useCallback(async () => {
    if (!localStorage.getItem("token")) {
      setLoading(false);
      return;
    }
    try {
      setUser(await api.get("/user/info"));
      setError("");
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    refresh();
    window.addEventListener("auth-expired", logout);
    return () => window.removeEventListener("auth-expired", logout);
  }, [refresh, logout]);
  const login = async (account, password) => {
    const result = await api.post("/auth/login", { account, password });
    localStorage.setItem("token", result.token);
    setUser(result.user);
    setError("");
  };
  return (
    <AuthContext.Provider
      value={{ user, loading, error, login, logout, refresh }}
    >
      {children}
    </AuthContext.Provider>
  );
}
