const { User, BlindBox, Prize, Order, PlayerShow, Comment } = require("./models");
const jwt = require("jsonwebtoken");
const bcrypt = require("bcryptjs");

// 注册
async function register(req, res) {
    try {
        const { account, password } = req.body;
        if (!account || !password) {
            return res.status(400).json({ msg: "缺少参数" });
        }

        // 检查用户是否已存在
        const existingUser = await User.findOne({ where: { account } });
        if (existingUser) {
            return res.status(400).json({ msg: "账号已存在" });
        }

        // 密码加密
        const hash = await bcrypt.hash(password, 10);

        // 创建新用户
        await User.create({ account, password: hash });

        res.json({ msg: "注册成功" });
    } catch (error) {
        console.error('注册错误:', error);
        res.status(500).json({ msg: "注册失败，请稍后重试" });
    }
}

// 登录
async function login(req, res) {
    try {
        const { account, password } = req.body;
        if (!account || !password) {
            return res.status(400).json({ msg: "缺少参数" });
        }

        const user = await User.findOne({ where: { account } });
        if (!user || !(await bcrypt.compare(password, user.password))) {
            return res.status(400).json({ msg: "账号或密码错误" });
        }

        const token = jwt.sign({ id: user.id }, "secret", { expiresIn: "7d" });
        res.json({ token, msg: "登录成功" });
    } catch (error) {
        console.error('登录错误:', error);
        res.status(500).json({ msg: "登录失败，请稍后重试" });
    }
}

// 盲盒列表
async function listBlindBoxes(req, res) {
    const { page = 1, pageSize = 10, search = "" } = req.query;
    const where = search ? { name: { [require("sequelize").Op.like]: `%${search}%` } } : {};
    const { count, rows } = await BlindBox.findAndCountAll({
        where, offset: (page-1)*pageSize, limit: +pageSize
    });
    res.json({ list: rows, total: count });
}

// 盲盒详情
async function getBlindBoxDetail(req, res) {
    const box = await BlindBox.findByPk(req.params.id, { include: Prize });
    if (!box) return res.status(404).json({ msg: "not found" });
    res.json(box);
}

// 抽盒
async function drawBlindBox(req, res) {
    const user = req.user;
    const blindbox = await BlindBox.findByPk(req.params.id);
    if (!blindbox || blindbox.stock <= 0) return res.status(400).json({ msg: "无库存" });
    if (user.balance < blindbox.price) return res.status(400).json({ msg: "余额不足" });
    const prizes = await Prize.findAll({ where: { blindboxId: blindbox.id } });
    let rand = Math.random(), sum = 0, selected;
    for (let p of prizes) {
        sum += p.probability;
        if (rand <= sum) { selected = p; break; }
    }
    if (!selected) selected = prizes[prizes.length - 1];
    await blindbox.decrement("stock", { by: 1 });
    await user.decrement("balance", { by: blindbox.price });
    const order = await Order.create({
        userId: user.id,
        blindboxId: blindbox.id,
        prizeId: selected.id,
        price: blindbox.price,
        status: "已支付"
    });
    res.json({
        msg: "抽盒成功", prizeName: selected.prizeName, prizeImg: selected.prizeImg, orderId: order.id
    });
}

// 我的订单
async function getOrders(req, res) {
    const orders = await Order.findAll({
        where: { userId: req.user.id },
        order: [["id", "DESC"]],
        include: [
            { model: BlindBox, attributes: ["name"] },
            { model: Prize, attributes: ["prizeName", "prizeImg", "rarity"] }
        ]
    });
    res.json({ list: orders.map(o => ({
            id: o.id,
            blindBoxName: o.BlindBox?.name,
            prizeName: o.Prize?.prizeName,
            prizeImg: o.Prize?.prizeImg,
            rarity: o.Prize?.rarity,
            price: o.price,
            status: o.status,
            createdAt: o.createdAt
        })) });
}

// 晒单列表
async function getPlayerShows(req, res) {
    const { page = 1, pageSize = 5 } = req.query;
    const { count, rows } = await PlayerShow.findAndCountAll({
        include: [{ model: User, attributes: ["account"] }],
        order: [["id", "DESC"]],
        offset: (page-1)*pageSize,
        limit: +pageSize
    });
    res.json({ list: rows.map(r => ({
            id: r.id,
            username: r.User?.account,
            content: r.content,
            images: JSON.parse(r.images || "[]"),
            likes: r.likes,
            createdAt: r.createdAt
        })), totalPages: Math.ceil(count/pageSize) });
}

// 发布晒单
async function createPlayerShow(req, res) {
    const { content, images } = req.body;
    await PlayerShow.create({
        userId: req.user.id,
        content,
        images: JSON.stringify(images || [])
    });
    res.json({ msg: "ok" });
}

// 点赞
async function likePlayerShow(req, res) {
    const show = await PlayerShow.findByPk(req.params.id);
    if (!show) return res.status(404).json({ msg: "not found" });
    show.likes += 1;
    await show.save();
    res.json({ msg: "liked" });
}

// 评论
async function commentPlayerShow(req, res) {
    const { content } = req.body;
    await Comment.create({
        playershowId: req.params.id,
        userId: req.user.id,
        content
    });
    res.json({ msg: "评论成功" });
}

// 获取评论
async function getComments(req, res) {
    const list = await Comment.findAll({
        where: { playershowId: req.params.id },
        include: [{ model: User, attributes: ["account"] }],
        order: [["id", "ASC"]]
    });
    res.json({ list: list.map(c => ({
            username: c.User?.account,
            content: c.content,
            createdAt: c.createdAt
        })) });
}

// 获取用户信息
async function getUserInfo(req, res) {
    try {
        const user = await User.findByPk(req.user.id, {
            attributes: ['id', 'account', 'balance', 'createdAt']
        });
        if (!user) {
            return res.status(404).json({ msg: "用户不存在" });
        }
        res.json({
            id: user.id,
            account: user.account,
            balance: user.balance,
            createdAt: user.createdAt
        });
    } catch (error) {
        console.error('获取用户信息错误:', error);
        res.status(500).json({ msg: "获取用户信息失败" });
    }
}

// 充值功能
async function recharge(req, res) {
    try {
        const { amount } = req.body;
        if (!amount || amount <= 0) {
            return res.status(400).json({ msg: "充值金额必须大于0" });
        }

        const user = await User.findByPk(req.user.id);
        if (!user) {
            return res.status(404).json({ msg: "用户不存在" });
        }

        // 更新用户余额
        await user.increment('balance', { by: amount });
        await user.reload(); // 重新加载用户数据以获取最新余额

        res.json({
            msg: "充值成功",
            balance: user.balance
        });
    } catch (error) {
        console.error('充值错误:', error);
        res.status(500).json({ msg: "充值失败，请稍后重试" });
    }
}

module.exports = {
    register, login, listBlindBoxes, getBlindBoxDetail, drawBlindBox,
    getOrders, getPlayerShows, createPlayerShow, likePlayerShow,
    commentPlayerShow, getComments, getUserInfo, recharge
};
