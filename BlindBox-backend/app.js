const express = require("express");
const cors = require("cors");
const bodyParser = require("body-parser");
const { sequelize } = require("./models");
const routes = require("./routes");
const { seed } = require("./seedData");
const path = require("path");
const fs = require("fs");

const app = express();

// CORS配置
app.use(cors({
    origin: 'http://localhost:5173', // 前端开发服务器地址
    credentials: true
}));

app.use(bodyParser.json());

// 静态文件服务
if (!fs.existsSync("images")) fs.mkdirSync("images");
app.use("/images", express.static(path.join(__dirname, "images")));

// API路由
app.use("/api", routes);

sequelize.sync().then(async () => {
    // 检查数据库中是否已有数据，如果没有才执行种子数据
    const { User, BlindBox } = require("./models");
    const userCount = await User.count();
    const blindBoxCount = await BlindBox.count();

    if (userCount === 0 && blindBoxCount === 0) {
        console.log("数据库为空，正在初始化种子数据...");
        await seed();
    } else {
        console.log(`数据库已有数据：用户 ${userCount} 个，盲盒 ${blindBoxCount} 个`);
    }

    app.listen(3000, () => {
        console.log("Server started at http://localhost:3000");
    });
});
