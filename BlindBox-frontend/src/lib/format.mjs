export function money(cents) {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
  }).format(Number(cents || 0) / 100);
}
export function cents(input) {
  const value = String(input).trim();
  if (!/^(0|[1-9]\d*)(\.\d{1,2})?$/.test(value))
    throw new Error("金额最多保留两位小数");
  const [whole, fraction = ""] = value.split(".");
  const result = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(result) || result < 1 || result > 10000000)
    throw new Error("金额须在 0.01 至 100000 元之间");
  return result;
}
export const date = (value) =>
  value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "";
export const rarityName = {
  common: "普通款",
  rare: "稀有款",
  secret: "隐藏款",
};
export const statusName = {
  DRAFT: "待上架",
  ON_SALE: "销售中",
  OFF_SALE: "已下架",
  ARCHIVED: "已归档",
};
