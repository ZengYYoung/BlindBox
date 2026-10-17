const { BlindBox, Prize } = require('./models');

const seedData = [
    {
        name: "海洋系列",
        series: "sea",
        image: "/images/seabutton.png",
        description: "探索神秘的海洋世界，收集可爱的海洋生物",
        price: 59,
        stock: 50,
        prizes: [
            { prizeName: "海豚", prizeImg: "/images/dolphin.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "海马", prizeImg: "/images/seahorse.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "水母", prizeImg: "/images/jellyfish.png", rarity: "rare", value: 15, probability: 0.20 },
            { prizeName: "海龟", prizeImg: "/images/turtle.png", rarity: "rare", value: 15, probability: 0.15 },
            { prizeName: "美人鱼", prizeImg: "/images/mermaid.png", rarity: "secret", value: 50, probability: 0.05 }
        ]
    },
    {
        name: "森林系列",
        series: "forest",
        image: "/images/forestbutton.png",
        description: "走进神秘的森林，遇见可爱的森林动物",
        price: 59,
        stock: 50,
        prizes: [
            { prizeName: "小鹿", prizeImg: "/images/deer.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "狐狸", prizeImg: "/images/fox.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "猫头鹰", prizeImg: "/images/owl.png", rarity: "rare", value: 15, probability: 0.20 },
            { prizeName: "松鼠", prizeImg: "/images/squirrel.png", rarity: "rare", value: 15, probability: 0.15 },
            { prizeName: "独角兽", prizeImg: "/images/unicorn.png", rarity: "secret", value: 50, probability: 0.05 }
        ]
    },
    {
        name: "太空系列",
        series: "space",
        image: "/images/spacebutton.png",
        description: "遨游浩瀚宇宙，收集神秘的太空元素",
        price: 79,
        stock: 30,
        prizes: [
            { prizeName: "小行星", prizeImg: "/images/asteroid.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "火箭", prizeImg: "/images/rocket.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "月球车", prizeImg: "/images/moon-rover.png", rarity: "rare", value: 15, probability: 0.20 },
            { prizeName: "黑洞", prizeImg: "/images/BlackHole.png", rarity: "rare", value: 15, probability: 0.15 },
            { prizeName: "银河", prizeImg: "/images/theMilkyWay.png", rarity: "secret", value: 50, probability: 0.05 }
        ]
    },
    {
        name: "糖果系列",
        series: "candy",
        image: "/images/candybutton.png",
        description: "甜蜜的糖果世界，收集各种美味糖果",
        price: 49,
        stock: 60,
        prizes: [
            { prizeName: "棒棒糖", prizeImg: "/images/lollipop.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "棉花糖", prizeImg: "/images/marshmallow.png", rarity: "common", value: 5, probability: 0.30 },
            { prizeName: "冰淇淋", prizeImg: "/images/ice-cream.png", rarity: "rare", value: 15, probability: 0.20 },
            { prizeName: "小熊软糖", prizeImg: "/images/gummy-bear.png", rarity: "rare", value: 15, probability: 0.15 },
            { prizeName: "蛋糕", prizeImg: "/images/cake.png", rarity: "secret", value: 50, probability: 0.05 }
        ]
    }
];

async function seed() {
    try {
        // 只清空盲盒和奖品数据，保留用户数据
        await Prize.destroy({ where: {} });
        await BlindBox.destroy({ where: {} });

        // 创建盲盒和奖品
        for (const boxData of seedData) {
            const { prizes, ...boxInfo } = boxData;
            const box = await BlindBox.create(boxInfo);

            for (const prizeData of prizes) {
                await Prize.create({
                    ...prizeData,
                    blindboxId: box.id
                });
            }
        }

        console.log('种子数据创建成功！');
    } catch (error) {
        console.error('种子数据创建失败:', error);
    }
}

module.exports = { seed };
