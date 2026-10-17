const axios = require('axios');

async function testApi() {
    try {
        // 模拟登录获取token
        const loginResponse = await axios.post('http://localhost:3000/api/auth/login', {
            account: '845565988@qq.com',
            password: '123456'
        });

        const token = loginResponse.data.token;
        console.log('登录成功，token:', token);

        // 测试获取订单API
        const ordersResponse = await axios.get('http://localhost:3000/api/orders', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        console.log('订单API响应:', JSON.stringify(ordersResponse.data, null, 2));

        // 检查稀有度数据
        const orders = ordersResponse.data.list || [];
        console.log('\n=== 稀有度检查 ===');
        orders.forEach(order => {
            console.log(`订单${order.id}: ${order.prizeName} - 稀有度: ${order.rarity}`);
        });

        // 统计稀有度
        const rarityCount = {
            common: orders.filter(o => o.rarity === 'common').length,
            rare: orders.filter(o => o.rarity === 'rare').length,
            secret: orders.filter(o => o.rarity === 'secret').length
        };

        console.log('\n=== 稀有度统计 ===');
        console.log('普通:', rarityCount.common);
        console.log('稀有:', rarityCount.rare);
        console.log('神秘:', rarityCount.secret);

    } catch (error) {
        console.error('API测试失败:', error.response?.data || error.message);
    }
}

testApi();
