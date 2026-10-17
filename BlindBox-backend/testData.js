const { User, BlindBox, Prize, Order } = require('./models');

async function testData() {
    try {
        console.log('=== 检查数据库数据 ===');

        // 检查用户
        const users = await User.findAll();
        console.log(`用户数量: ${users.length}`);
        users.forEach(user => {
            console.log(`用户: ${user.account}, 余额: ${user.balance}`);
        });

        // 检查盲盒
        const boxes = await BlindBox.findAll();
        console.log(`盲盒数量: ${boxes.length}`);

        // 检查奖品
        const prizes = await Prize.findAll();
        console.log(`奖品数量: ${prizes.length}`);
        prizes.forEach(prize => {
            console.log(`奖品: ${prize.prizeName}, 稀有度: ${prize.rarity}, 盲盒ID: ${prize.blindboxId}`);
        });

        // 检查订单
        const orders = await Order.findAll({
            include: [
                { model: BlindBox, attributes: ['name'] },
                { model: Prize, attributes: ['prizeName', 'prizeImg', 'rarity'] }
            ]
        });
        console.log(`订单数量: ${orders.length}`);
        orders.forEach(order => {
            console.log(`订单ID: ${order.id}, 用户ID: ${order.userId}`);
            console.log(`  盲盒: ${order.BlindBox?.name}`);
            console.log(`  奖品: ${order.Prize?.prizeName}, 稀有度: ${order.Prize?.rarity}`);
        });

    } catch (error) {
        console.error('测试数据失败:', error);
    }
}

testData();
