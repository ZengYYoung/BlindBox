const fs = require('fs');
const path = require('path');
const { User, BlindBox, Prize, Order, PlayerShow, Comment } = require('./models');

// 备份数据
async function backupData() {
    try {
        console.log('开始备份数据...');

        const backup = {
            users: await User.findAll({ raw: true }),
            blindBoxes: await BlindBox.findAll({ raw: true }),
            prizes: await Prize.findAll({ raw: true }),
            orders: await Order.findAll({ raw: true }),
            playerShows: await PlayerShow.findAll({ raw: true }),
            comments: await Comment.findAll({ raw: true }),
            timestamp: new Date().toISOString()
        };

        const backupPath = path.join(__dirname, 'backup.json');
        fs.writeFileSync(backupPath, JSON.stringify(backup, null, 2));

        console.log(`数据备份成功！备份文件：${backupPath}`);
        console.log(`用户：${backup.users.length} 个`);
        console.log(`盲盒：${backup.blindBoxes.length} 个`);
        console.log(`奖品：${backup.prizes.length} 个`);
        console.log(`订单：${backup.orders.length} 个`);
        console.log(`晒单：${backup.playerShows.length} 个`);
        console.log(`评论：${backup.comments.length} 个`);

    } catch (error) {
        console.error('备份失败:', error);
    }
}

// 恢复数据
async function restoreData() {
    try {
        const backupPath = path.join(__dirname, 'backup.json');
        if (!fs.existsSync(backupPath)) {
            console.log('没有找到备份文件');
            return;
        }

        console.log('开始恢复数据...');
        const backup = JSON.parse(fs.readFileSync(backupPath, 'utf8'));

        // 清空现有数据
        await Comment.destroy({ where: {} });
        await PlayerShow.destroy({ where: {} });
        await Order.destroy({ where: {} });
        await Prize.destroy({ where: {} });
        await BlindBox.destroy({ where: {} });
        await User.destroy({ where: {} });

        // 恢复数据
        for (const user of backup.users) {
            await User.create(user);
        }

        for (const blindBox of backup.blindBoxes) {
            await BlindBox.create(blindBox);
        }

        for (const prize of backup.prizes) {
            await Prize.create(prize);
        }

        for (const order of backup.orders) {
            await Order.create(order);
        }

        for (const playerShow of backup.playerShows) {
            await PlayerShow.create(playerShow);
        }

        for (const comment of backup.comments) {
            await Comment.create(comment);
        }

        console.log('数据恢复成功！');

    } catch (error) {
        console.error('恢复失败:', error);
    }
}

// 如果直接运行此文件
if (require.main === module) {
    const command = process.argv[2];
    if (command === 'backup') {
        backupData();
    } else if (command === 'restore') {
        restoreData();
    } else {
        console.log('使用方法:');
        console.log('  node backupData.js backup  - 备份数据');
        console.log('  node backupData.js restore - 恢复数据');
    }
}

module.exports = { backupData, restoreData };
