import axios from "axios";
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || "/api",
  timeout: 12000,
});
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = "Bearer " + token;
  return config;
});
api.interceptors.response.use(
  (response) => {
    if (response.data.code !== 200)
      throw new Error(response.data.msg || "操作失败");
    return response.data.data;
  },
  (error) => {
    if (error.response?.status === 401)
      window.dispatchEvent(new Event("auth-expired"));
    const fallback = {
      401: "请重新登录",
      403: "没有操作权限",
      429: "操作过于频繁，请稍后重试",
      500: "服务器内部错误，请保留原请求 ID",
      502: "后端暂不可用",
      503: "系统繁忙，请稍后使用原请求重试",
    };
    error.message =
      error.response?.data?.msg ||
      fallback[error.response?.status] ||
      (error.code === "ECONNABORTED"
        ? "请求超时，请保留原请求 ID 确认结果"
        : "无法连接后端，请检查服务和代理配置");
    return Promise.reject(error);
  },
);
export const requestId = () => crypto.randomUUID();
export const message = (error) => error.message || "操作失败，请稍后重试";
export default api;
