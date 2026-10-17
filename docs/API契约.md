# API 契约

## 通用规则

- 前缀 /api；认证头 Authorization: Bearer <token>。
- 响应 {code,msg,data}，code与HTTP状态一致，成功200。
- 所有金额字段是整数“分”；前端显示元，例如price=5900代表59.00元。
- 分页 page=1 起，pageSize=1..100，返回list/total/page/pageSize/totalPages。
- Idempotency-Key 为8–64位字母、数字、下划线或连字符。重复请求必须保留原key。
- 繁忙503返回Retry-After: 1，额度不足429返回Retry-After: 60；保留原key查询/重试。
- 同时重放可能先503，原事务完成后同key仍返回同一订单。
- 系列 sea/forest/space/candy；稀有度 common/rare/secret。
- 用户和订单ID为整数；库存为非负整数；用户role仅USER/ADMIN。
- 新注册密码至少8字符且UTF-8最多72字节；登录兼容旧短密码，未新增密码重置接口。
- 不返回用户password；JWT不承载可由客户端决定的管理员权限。

## 接口清单

| 方法 | 路径 | 权限 | 参数 / 返回 |
|---|---|---|---|
| POST | /auth/register | 公开 | account(3..50)、password(8..64)；创建USER |
| POST | /auth/login | 公开 | account/password非空；兼容旧密码，返回token与user |
| GET | /user/info | 用户 | id/account/balance/role/createdAt/demoRechargeEnabled |
| POST | /user/recharge | 用户且开发充值开启 | amount=1..10000000，必带幂等key；balance/credited |
| GET | /blindboxes | 公开 | page/pageSize/search/series；分页商品、asOf及metadataAsOf |
| GET | /blindboxes/{id} | 公开；非上架仅ADMIN | box/prizes/totalStock/rarityCounts/asOf |
| POST | /blindboxes/{id}/draw | 用户 | 必带幂等key；orderId/requestId/blindBoxId/prizeName/prizeImg/rarity/price |
| GET | /orders/request/{key} | 用户 | 查询本人原抽奖结果；未观察到已提交订单404 |
| GET | /orders | 用户 | 分页订单快照、requestId与createdAt |
| GET | /user/collection | 用户 | 分页奖品数量，stats(totalItems/uniqueItems/totalSpent) |
| GET | /playershows | 公开 | 分页晒单；登录时附本人liked |
| POST | /playershows | 用户 | content(1..2000)、images最多6个上传URL |
| POST | /playershows/{id}/like | 用户 | 幂等点赞，likes/liked |
| DELETE | /playershows/{id}/like | 用户 | 幂等取消，likes/liked |
| GET | /playershows/{id}/comments | 公开 | 分页评论 |
| POST | /playershows/{id}/comments | 用户 | content(1..1000) |
| POST | /images | 用户 | multipart file，真实PNG/JPEG，≤5MB；url |
| GET | /admin/blindboxes | ADMIN | 包含全部商品状态，分页/search |
| POST | /admin/blindboxes | ADMIN | 创建配置、必带key；商品默认DRAFT |
| PUT | /admin/blindboxes/{id} | ADMIN | name/series/image/description/price；完整设置元数据，不改库存 |
| PATCH | /admin/blindboxes/{id}/status | ADMIN | status=DRAFT/ON_SALE/OFF_SALE |
| POST | /admin/blindboxes/{id}/restock | ADMIN | prizeId、quantity=1..1000000，必带key |
| DELETE | /admin/blindboxes/{id} | ADMIN | 归档，不物理删除 |

## 创建奖池示例

价格按分，配置每款实体数量；不传概率或独立总库存：

~~~json
{
  "name": "深海来信",
  "series": "sea",
  "image": "/images/sea.png",
  "description": "收藏海洋里的朋友",
  "price": 5900,
  "prizes": [
    {"prizeName":"海豚","prizeImg":"/images/dolphin.png","rarity":"common","stock":60,"value":0},
    {"prizeName":"水母","prizeImg":"/images/jellyfish.png","rarity":"rare","stock":30,"value":0},
    {"prizeName":"美人鱼","prizeImg":"/images/mermaid.png","rarity":"secret","stock":10,"value":0}
  ]
}
~~~

总量100；概率由数量推导。value为历史兼容的非负展示值，不作为交易价格。图片使用同源资源路径；Nginx CSP默认不允许任意外站图片。

## 详情与抽奖结果

详情prizes每项包含id/prizeName/prizeImg/rarity/stock/value/probability。概率范围0..1，为查询时快照，库存0则概率0。实际抽奖读取锁内最新库存，不直接采用前端传来的概率。

~~~json
{
  "code":200,
  "msg":"success",
  "data":{
    "orderId":23,
    "requestId":"a-client-generated-request-id",
    "blindBoxId":1,
    "prizeName":"海豚",
    "prizeImg":"/images/dolphin.png",
    "rarity":"common",
    "price":5900
  }
}
~~~

相同用户同key换盲盒409；建池相同key更改配置409；补货相同key更改奖品/数量409；充值相同key更改金额409。修改商品介绍和状态本身为设置语义，重试同值不重复消费库存。

## 错误与恢复

| HTTP | 含义 | 前端处理 |
|---|---|---|
| 400 | 类型/范围/必填/幂等key格式错误 | 修正输入，不继续提交同样错误参数 |
| 401 | 未登录、token过期或用户不存在 | 重新登录，保留待确认交易 |
| 403 | 角色无权限、开发充值关闭 | 不绕过后端授权 |
| 404 | 商品不存在、原请求尚未查到 | 原结果404仍保留key，稍后查询或同key重试 |
| 409 | 售罄/余额不足/状态或幂等参数冲突 | 显示msg；不按所有409统一认定成功 |
| 413 | 上传体积超过限制 | 缩小图片 |
| 429 | 用户/IP操作频繁 | 等待Retry-After，保留未知结果请求 |
| 500/503/超时 | 结果可能未确认 | 保存原key，禁止自动新建另一笔抽奖 |

图片解码并发上限2，繁忙503；宽/高≤4096、总像素≤12000000，重编码PNG≤10MB。文件URL只有一个，不返回本地磁盘路径。

## 契约验证边界

上述为源码当前接口；权限及输入分支经过MockMvc测试，数据库字段映射和真实API链路还需接入后验证。所有历史Node接口返回结构与路径行为不再作为当前契约。

列表的名称等元数据可缓存；库存、价格、状态每次批量查库。详情为同一SQL语句快照，成交按事务内锁定读重算；展示概率不保证稍后成交时仍相同。详见 [并发优化说明](并发优化说明.md)。
