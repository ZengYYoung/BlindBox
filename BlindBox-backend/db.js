const { Sequelize } = require('sequelize');

// 使用 SQLite 数据库存储在本地文件
const sequelize = new Sequelize({
    dialect: 'sqlite',
    storage: './database.sqlite',
    logging: false, // 可选：关闭SQL日志
});

module.exports = sequelize;
