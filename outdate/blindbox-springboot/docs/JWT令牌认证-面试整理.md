> 已归档（2026-10-19）：本文件保留历史内容，不作为当前实现或容量承诺。请阅读 [当前关键技术文档](../../../docs/关键技术文档.md)。

> 历史版本资料：本次升级后的权威说明在根目录 docs/项目介绍与面试指南.md。旧固定概率、Redis库存预扣、CAS重试等描述不适用于当前源码。

# JWT 令牌认证 — 面试知识点 + 项目实战

## 一、JWT 基础

### 1.1 什么是 JWT

JWT（JSON Web Token）是一种**无状态**的令牌格式，由三部分组成，用 `.` 连接：

```
header.payload.signature
```

| 部分 | 内容 | 示例 |
|------|------|------|
| **Header** | 令牌类型 + 签名算法 | `{"alg":"HS256","typ":"JWT"}` |
| **Payload** | 声明（数据） | `{"id":1,"iat":... ,"exp":...}` |
| **Signature** | 签名（防篡改） | 对 header + payload 用密钥签名 |

### 1.2 JWT 的工作流程

```
客户端                             服务端
  │                                   │
  │── POST /api/auth/login ──────────→│  登录
  │                                   │── 验证账号密码
  │←──── { "token": "xxx" } ──────────│── JwtUtil.generateToken(userId)
  │                                   │
  │── GET /api/orders ───────────────→│  后续请求
  │   Authorization: Bearer xxx       │── JwtInterceptor 验证令牌
  │                                   │── 提取 userId → request 属性
  │←──── 返回订单列表 ─────────────────│── Controller 用 userId 查数据
```

**关键特征**：服务端不存储令牌，全靠签名验证真伪 — 这就是"无状态认证"。

### 1.3 Session vs JWT

| | Session 认证 | JWT 认证 |
|--|-------------|----------|
| 存储位置 | 服务端内存/Redis | 客户端持有 |
| 扩展性 | 需要共享 Session（Redis） | 天然支持水平扩展 |
| 撤销能力 | 可以（删掉 session 即可） | 难（需要黑名单） |
| 性能 | 每次查 Redis | 本地验签，更快 |

---

## 二、项目中的 JWT 实现

### 2.1 令牌生成 — JwtUtil

```java
// src/main/java/com/blindbox/util/JwtUtil.java

private static final String SECRET = "BlindBox2024SecretKeyForJWTTokenGeneration!@#$%";
private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
private static final long EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7天

public static String generateToken(Integer userId) {
    return Jwts.builder()
            .claim("id", userId)           // 自定义声明：用户 ID
            .issuedAt(new Date())           // 签发时间
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION))  // 过期时间
            .signWith(KEY)                  // HMAC-SHA256 签名
            .compact();
}
```

- 使用 **jjwt 0.12.6** 库
- 签名算法：**HmacSHA256**（对称加密）
- Payload 只存了一个字段：`id`（用户 ID）
- 有效期：**7 天**

### 2.2 令牌验证 — JwtInterceptor

```java
// src/main/java/com/blindbox/config/JwtInterceptor.java

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. OPTIONS 预检请求放行
        if ("OPTIONS".equals(request.getMethod())) return true;

        // 2. 从 Authorization 头提取 Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new BusinessException(401, "未登录");

        // 3. 解析 token，获取 userId
        String token = authHeader.substring(7);  // 去掉 "Bearer "
        Integer userId = JwtUtil.getUserId(token);
        request.setAttribute("userId", userId);  // 传递给 Controller
        return true;
    }
}
```

**工作流程**：

```
HTTP 请求
  ↓
① WebMvcConfig 匹配 /api/** → 走拦截器
② JwtInterceptor.preHandle()
  ├─ OPTIONS → 直接放行（CORS 预检）
  ├─ 无 Authorization 头 → 抛 401
  ├─ token 无效/过期 → 抛 401
  └─ 有效 → 设置 request.setAttribute("userId", id) → 放行
  ↓
③ Controller 通过 request.getAttribute("userId") 获取当前用户
```

### 2.3 注册与登录 — AuthController + UserServiceImpl

**注册**（`POST /api/auth/register`）：

```java
public void register(String account, String password) {
    // 1. 检查账号是否已存在
    if (userMapper.selectByAccount(account) != null)
        throw new BusinessException("账号已存在");
    // 2. 创建用户（密码明文存储 — 待改进）
    User user = new User();
    user.setAccount(account);
    user.setPassword(password);  // 实际应使用 BCryptPasswordEncoder
    user.setBalance(0);
    userMapper.insert(user);
}
```

**登录**（`POST /api/auth/login`）：

```java
public String login(String account, String password) {
    User user = userMapper.selectByAccount(account);
    // 账号不存在 或 密码不匹配
    if (user == null || !user.getPassword().equals(password))
        throw new BusinessException("账号或密码错误");
    // 生成 JWT 返回
    return JwtUtil.generateToken(user.getId());
}
```

### 2.4 拦截器注册与公开路径 — WebMvcConfig

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/**")                      // 拦截所有 /api/ 开头路径
            .excludePathPatterns(
                    "/api/auth/**",                          // 登录注册（不需要 token）
                    "/api/blindboxes",                       // 盲盒列表
                    "/api/blindboxes/*",                     // 盲盒详情
                    "/api/playershows/*/comments",           // 查看评论
                    "/api/images/**"                         // 图片上传
            );
}
```

**公开路径（无需登录即可访问）**：

| 路径 | 说明 | 理由 |
|------|------|------|
| `/api/auth/**` | 登录、注册 | 没登录才能注册登录 |
| `/api/blindboxes` | 盲盒列表 | 游客也能浏览 |
| `/api/blindboxes/*` | 盲盒详情 | 同上 |
| `/api/playershows/*/comments` | 查看评论 | 公开内容 |
| `/api/images/**` | 图片上传 | 上传图片不需要登录（有安全争议） |

### 2.5 下游消费 userId 的典型模式

以 OrderController 为例：

```java
@PostMapping("/blindboxes/{id}/draw")
public Result<Map<String, Object>> draw(@PathVariable Integer id, HttpServletRequest request) {
    // 从拦截器设置的属性中获取当前用户 ID
    Integer userId = (Integer) request.getAttribute("userId");
    // 传入 service 层，无需再考虑"当前是谁"的问题
    Map<String, Object> result = orderService.draw(userId, id);
    return Result.success(result);
}
```

这样设计的好处是：
- **Controller 层不需要关心 token 解析逻辑**，拦截器已经做完了
- **Service 层只需要接受 userId 参数**，不依赖 HttpServletRequest，易于单元测试
- **职责清晰**：拦截器干认证的事，Controller 干路由的事，Service 干业务的事

### 2.6 异常处理 — GlobalExceptionHandler

拦截器抛出 `BusinessException(401, "未登录")` 后，被全局异常处理器捕获：

```java
@ExceptionHandler(BusinessException.class)
public Result<?> handleBusinessException(BusinessException e, HttpServletResponse response) {
    response.setStatus(e.getCode());  // 设置 HTTP 状态码为 401
    return Result.error(e.getCode(), e.getMessage());
}
```

返回给前端：

```json
HTTP 401
{
  "code": 401,
  "message": "未登录"
}
```

---

## 三、认证数据流全景

```
                    ┌─────────────────────────────┐
                    │    前端 localStorage 存 token │
                    └─────────────────────────────┘
                              │
                    Authorization: Bearer xxx
                              ↓
                    ┌─────────────────────────────┐
                    │  JwtInterceptor.preHandle()  │
                    │  ① 检查 Authorization 头     │
                    │  ② 解析 token → userId       │
                    │  ③ request.setAttribute()    │
                    └─────────────────────────────┘
                              ↓
                    ┌─────────────────────────────┐
                    │  Controller 层               │
                    │  request.getAttribute("userId")│
                    └─────────────────────────────┘
                              ↓
                    ┌─────────────────────────────┐
                    │  Service 层                  │
                    │  用 userId 执行业务逻辑       │
                    └─────────────────────────────┘
```

---

## 四、当前实现的安全问题（面试主动指出）

| 问题 | 位置 | 风险 | 改进方案 |
|------|------|------|---------|
| **明文存密码** | UserServiceImpl:29 | 数据库泄露则所有账号密码暴露 | BCryptPasswordEncoder 哈希 |
| **硬编码密钥** | JwtUtil:13 | 源码泄露则任何人都能伪造 token | 环境变量 `${JWT_SECRET}` |
| **无 token 撤销** | — | token 泄露后 7 天内无法失效 | Redis 黑名单 / 短时效 + refresh token |
| **7 天过期太长** | JwtUtil:15 | 泄露后窗口期过长 | 缩短到 2h + refresh token |
| **图片上传无需登录** | WebMvcConfig:41 | 匿名用户可以上传任意文件 | 移出 exclude 列表 |

### 4.1 密码加密改进

```java
// 引入 Spring Security 的 BCryptPasswordEncoder
private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

// 注册时加密
public void register(String account, String password) {
    // ...
    user.setPassword(passwordEncoder.encode(password));
    // ...
}

// 登录时校验
public String login(String account, String password) {
    User user = userMapper.selectByAccount(account);
    if (user == null || !passwordEncoder.matches(password, user.getPassword()))
        throw new BusinessException("账号或密码错误");
    return JwtUtil.generateToken(user.getId());
}
```

**为什么用 BCrypt 而不是 MD5/SHA256？**
- MD5 已经被彩虹表攻破，计算速度太快易暴力破解
- BCrypt 内置 **salt（盐）**，且可调迭代次数，暴力破解成本极高

### 4.2 密钥从代码移到配置

```java
// ❌ 当前：硬编码
private static final String SECRET = "BlindBox2024...";

// ✅ 改进：从环境变量/配置文件读取
@Value("${jwt.secret}")
private String secret;
```

### 4.3 引入 Refresh Token 机制

```
access token（短时效，2h）  ← 真正用于认证
refresh token（长时效，7天） ← 用于获取新的 access token

流程：
登录 → 返回 { accessToken, refreshToken }
accessToken 过期 → 用 refreshToken 换新的 accessToken
refreshToken 过期 → 重新登录
```

优点：access token 泄露也只在 2 小时内有效，refresh token 可以配合黑名单做撤销。

---

## 五、面试可能追问的问题

### Q1: JWT 能加密用户密码吗？

不能。JWT 的 Payload 虽然用 Base64 编码，但**不是加密**，任何人都可以解码看到内容（只是防篡改）。永远不要把密码放在 JWT 里。JWT 中只放**非敏感标识**（如用户 ID）。

### Q2: 无状态 JWT 如何做到 token 撤销？

JWT 的设计初衷就是无状态，服务端不保存任何 token 信息。如果要撤销，常用方案：

| 方案 | 说明 | 缺点 |
|------|------|------|
| **黑名单** | 把要撤销的 token 的 jti（JWT ID）放到 Redis，拦截器检查 | 引入了有状态组件 |
| **短时效 + refresh** | access token 2h 过期，refresh token 配合黑名单 | 架构复杂一些 |
| **版本号** | 用户表中加 `tokenVersion`，JWT 里存该字段 | 改密码后所有登录都失效 |

### Q3: 对称签名（HMAC-SHA256）和非对称签名（RSA/ECDSA）的区别？

| | HMAC（当前项目） | RSA/ECDSA |
|--|-----------------|-----------|
| 密钥 | 一个密钥既签名又验签 | 私钥签名，公钥验签 |
| 适用场景 | 单体应用，一个服务 | 微服务，认证服务签名，其他服务验签 |
| 安全性 | 密钥必须保密 | 公钥可以公开 |

微服务架构下通常用**非对称**：认证中心持有私钥签发 token，其他业务服务只需要公钥验签，不需要拿到私钥。

### Q4: 本项目把 JwtInterceptor 放在 config 包下是什么设计思路？

JwtInterceptor 本质是一个**基础设施组件**，和 WebMvcConfig 一起完成请求拦截的配置工作。放在 `config` 包下表示"这个项目的配置"，而不是一个工具类。这算是一种常见的组织方式，也可以放在 `filter` 或 `interceptor` 包下更清晰。

### Q5: request.setAttribute() 设置 userId 是线程安全的吗？

**安全**。`request` 对象是每个请求独有的，不同线程之间的 request 不共享。所以用 `request.setAttribute("userId", userId)` 是天然的线程安全，不需要额外同步。

### Q6: 如果要做角色权限控制（如 admin 和普通用户），当前架构怎么扩展？

在 `JwtUtil` 生成 token 时把角色也放到 claim 里：

```java
.claim("id", userId)
.claim("role", user.getRole())  // "admin" 或 "user"
```

然后创建**角色拦截器**或**注解 + 切面**：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String value();  // "admin"
}

// 在 AdminController 上使用
@PostMapping("/blindboxes")
@RequireRole("admin")
public Result<?> addBlindBox(@RequestBody AddBlindBoxRequest req) { ... }
```

### Q7: 为什么要用 Bearer Token 而不是自定义 Header？

`Authorization: Bearer <token>` 是 **HTTP 标准规范（RFC 6750）**，优点：
- 标准通用，前端工具（axios、Postman）原生支持
- 中间件、网关可以统一识别和处理
- 符合 OpenAPI/Swagger 规范，生成文档时自动识别

---

## 六、总结一句话

```
登录 → 生成 JWT（用户ID + 7天过期 + HMAC签名）
请求 → 拦截器验签 → request.setAttribute("userId")
Controller → 拿 userId 执行业务
```
