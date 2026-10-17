const { DataTypes } = require('sequelize');
const sequelize = require('./db');

const User = sequelize.define('User', {
    account: DataTypes.STRING,
    password: DataTypes.STRING,
    balance: { type: DataTypes.INTEGER, defaultValue: 0 },
});

const BlindBox = sequelize.define('BlindBox', {
    name: DataTypes.STRING,
    series: DataTypes.STRING, // sea, forest, space, candy
    image: DataTypes.STRING,  // 封面图片
    description: DataTypes.STRING,
    price: DataTypes.INTEGER,
    stock: DataTypes.INTEGER,
});

const Prize = sequelize.define('Prize', {
    blindboxId: DataTypes.INTEGER,
    prizeName: DataTypes.STRING,  // 统一使用 prizeName
    prizeImg: DataTypes.STRING,   // 统一使用 prizeImg
    rarity: DataTypes.STRING,     // common, rare, secret
    value: DataTypes.INTEGER,     // 价值
    probability: DataTypes.FLOAT, // 概率
});

const Order = sequelize.define('Order', {
    userId: DataTypes.INTEGER,
    blindboxId: DataTypes.INTEGER,
    prizeId: DataTypes.INTEGER,
    price: DataTypes.INTEGER,
    status: DataTypes.STRING,
});

const PlayerShow = sequelize.define('PlayerShow', {
    userId: DataTypes.INTEGER,
    content: DataTypes.TEXT,
    images: DataTypes.TEXT, // JSON字符串
    likes: { type: DataTypes.INTEGER, defaultValue: 0 },
});

const Comment = sequelize.define('Comment', {
    playershowId: DataTypes.INTEGER,
    userId: DataTypes.INTEGER,
    content: DataTypes.TEXT,
});

// 关联关系
BlindBox.hasMany(Prize, { foreignKey: 'blindboxId' });
Prize.belongsTo(BlindBox, { foreignKey: 'blindboxId' });

User.hasMany(Order, { foreignKey: 'userId' });
Order.belongsTo(User, { foreignKey: 'userId' });

BlindBox.hasMany(Order, { foreignKey: 'blindboxId' });
Order.belongsTo(BlindBox, { foreignKey: 'blindboxId' });

Prize.hasMany(Order, { foreignKey: 'prizeId' });
Order.belongsTo(Prize, { foreignKey: 'prizeId' });

User.hasMany(PlayerShow, { foreignKey: 'userId' });
PlayerShow.belongsTo(User, { foreignKey: 'userId' });

PlayerShow.hasMany(Comment, { foreignKey: 'playershowId' });
Comment.belongsTo(PlayerShow, { foreignKey: 'playershowId' });

User.hasMany(Comment, { foreignKey: 'userId' });
Comment.belongsTo(User, { foreignKey: 'userId' });

module.exports = { sequelize, User, BlindBox, Prize, Order, PlayerShow, Comment };
