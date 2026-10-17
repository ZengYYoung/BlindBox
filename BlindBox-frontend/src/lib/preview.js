// Explicit opt-in catalog samples, never used by a transaction or account API.
export const previewEnabled = import.meta.env.VITE_CATALOG_PREVIEW === "true";
export const seriesNames = {
  sea: "海洋系列",
  forest: "森林系列",
  space: "太空系列",
  candy: "糖果系列",
};
const config = [
  [
    "sea",
    "深海来信",
    "把海洋的温柔，装进小小的收藏。",
    5900,
    ["海豚", "海马", "水母", "海龟", "美人鱼"],
    ["dolphin", "seahorse", "jellyfish", "turtle", "mermaid"],
  ],
  [
    "forest",
    "森林奇遇",
    "穿过树影，与森林里的朋友相遇。",
    5900,
    ["小鹿", "狐狸", "猫头鹰", "松鼠", "独角兽"],
    ["deer", "fox", "owl", "squirrel", "unicorn"],
  ],
  [
    "space",
    "星际漫游",
    "把遥远的星河，留在你的书桌。",
    7900,
    ["小行星", "火箭", "月球车", "黑洞", "银河"],
    ["asteroid", "rocket", "moon-rover", "BlackHole", "theMilkyWay"],
  ],
  [
    "candy",
    "糖果日记",
    "为平凡的一天，添一点甜。",
    4900,
    ["棒棒糖", "棉花糖", "冰淇淋", "小熊软糖", "蛋糕"],
    ["lollipop", "marshmallow", "ice-cream", "gummy-bear", "cake"],
  ],
];
export const previewBoxes = config.map(
  ([series, name, description, price], i) => ({
    id: i + 1,
    series,
    name,
    description,
    price,
    stock: 100,
    image: "/images/" + series + ".png",
    status: "ON_SALE",
  }),
);
export function previewDetail(id) {
  const box = previewBoxes.find((b) => b.id === Number(id));
  if (!box) throw new Error("样例商品不存在");
  const spec = config[box.id - 1],
    counts = [30, 30, 20, 15, 5],
    rarities = ["common", "common", "rare", "rare", "secret"];
  return {
    box,
    totalStock: 100,
    asOf: "离线样例",
    rarityCounts: { common: 60, rare: 35, secret: 5 },
    prizes: spec[4].map((name, i) => ({
      id: box.id * 10 + i,
      prizeName: name,
      prizeImg: "/images/" + spec[5][i] + ".png",
      stock: counts[i],
      rarity: rarities[i],
      probability: counts[i] / 100,
      value: 0,
    })),
  };
}
