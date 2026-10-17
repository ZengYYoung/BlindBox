const { sequelize, BlindBox, Prize } = require('./models');
const { themes, items } = require('./seedData');

(async () => {
    await sequelize.sync({ force: true }); // 清空重建
    for (const theme of themes) {
        const box = await BlindBox.create({
            name: theme.name,
            series: theme.key,
            image: theme.image,
            description: theme.description,
            price: 59,
            stock: 50
        });
        for (const item of items[theme.key]) {
            await Prize.create({
                blindboxId: box.id,
                name: item.name,
                rarity: item.rarity,
                value: item.value,
                image: item.image
            });
        }
    }
    console.log('初始化完成');
    process.exit(0);
})();
