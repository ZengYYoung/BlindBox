const jwt = require("jsonwebtoken");
const { User } = require("./models");

async function auth(req, res, next) {
    const token = req.headers.authorization?.split(" ")[1];
    if (!token) return res.status(401).json({ msg: "未登录" });
    try {
        const data = jwt.verify(token, "secret");
        req.user = await User.findByPk(data.id);
        if (!req.user) throw new Error();
        next();
    } catch {
        res.status(401).json({ msg: "token无效" });
    }
}

module.exports = { auth };
