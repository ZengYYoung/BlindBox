const express = require("express");
const router = express.Router();
const ctrl = require("./controllers");
const { auth } = require("./middleware");
const multer = require("multer");
const upload = multer({ dest: "images/" });
const { BlindBox, Prize } = require('./models');

// 用户认证
router.post("/auth/register", ctrl.register);
router.post("/auth/login", ctrl.login);

// 盲盒相关
router.get('/blindboxes', ctrl.listBlindBoxes);
router.get('/blindboxes/:id', async (req, res) => {
    const { BlindBox, Prize } = require('./models');
    const box = await BlindBox.findByPk(req.params.id, { include: Prize });
    if (!box) return res.status(404).json({ msg: '盲盒不存在' });
    res.json(box);
});
router.post('/blindboxes/:id/draw', auth, async (req, res) => {
    try {
        const { BlindBox, Prize, Order, User } = require('./models');
        const user = req.user;
        const blindbox = await BlindBox.findByPk(req.params.id);
        if (!blindbox) return res.status(404).json({ msg: '盲盒不存在' });
        if (blindbox.stock <= 0) return res.status(400).json({ msg: '无库存' });
        if (user.balance < blindbox.price) return res.status(400).json({ msg: '余额不足' });
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
    } catch (e) {
        res.status(500).json({ msg: '抽盒失败，请重试' });
    }
});

// 订单
router.get("/orders", auth, ctrl.getOrders);

// 用户信息
router.get("/user/info", auth, ctrl.getUserInfo);
router.post("/user/recharge", auth, ctrl.recharge);

// 晒单
router.get("/playershows", ctrl.getPlayerShows);
router.post("/playershows", auth, ctrl.createPlayerShow);
router.post("/playershows/:id/like", auth, ctrl.likePlayerShow);
router.post("/playershows/:id/comment", auth, ctrl.commentPlayerShow);
router.get("/playershows/:id/comments", ctrl.getComments);

// 图片上传
router.post("/images", auth, upload.single("file"), (req, res) => {
    const url = `/images/${req.file.filename}`;
    res.json({ url });
});

// 管理员API
router.post("/admin/blindboxes", auth, async (req, res) => {
    try {
        const { BlindBox } = require('./models');
        const { name, series, image, description, price, stock } = req.body;

        if (!name || !series || !price || !stock) {
            return res.status(400).json({ msg: '缺少必要参数' });
        }

        const newBox = await BlindBox.create({
            name,
            series,
            image: image || '',
            description: description || '',
            price: parseInt(price),
            stock: parseInt(stock)
        });

        res.json({ msg: '添加成功', box: newBox });
    } catch (error) {
        console.error('添加盲盒失败:', error);
        res.status(500).json({ msg: '添加失败' });
    }
});

router.delete("/admin/blindboxes/:id", auth, async (req, res) => {
    try {
        const { BlindBox, Prize } = require('./models');
        const boxId = req.params.id;

        // 删除相关奖品
        await Prize.destroy({ where: { blindboxId: boxId } });

        // 删除盲盒
        await BlindBox.destroy({ where: { id: boxId } });

        res.json({ msg: '删除成功' });
    } catch (error) {
        console.error('删除盲盒失败:', error);
        res.status(500).json({ msg: '删除失败' });
    }
});

module.exports = router;
