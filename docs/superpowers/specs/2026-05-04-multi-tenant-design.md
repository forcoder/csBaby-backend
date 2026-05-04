# 多租户架构设计

## 需求
- 多租户数据逻辑隔离
- 手机号 + 密码注册/登录
- 启动时强制登录

## 架构

### 认证流程
```
App 启动 → AuthManager.checkLoginState()
  → 有有效 token → 主页
  → 无 token → LoginScreen

LoginScreen → POST /api/auth/login { phone, password }
  → 后端返回 { token, tenantId, phoneNumber, expiresIn }
  → AuthManager.saveAuth()
  → 跳转主页
```

### 租户 ID 传递
- 后端 token（JWT）中编码 tenant_id
- AuthManager 解析 token 获取 tenant_id
- 所有 API 请求通过 AuthInterceptor 注入 Bearer Token
- 后端从 token 解析 tenant_id 做数据过滤

### 本地数据隔离
- 所有 Entity 加 `tenantId: String = ""` 字段
- 所有 DAO 查询加 `WHERE tenantId = :tenantId`
- Repository 层注入 AuthManager，自动传 tenantId
- 数据库升级 v7→v8：Migration7to8 加列 + 索引

## 新增文件
- `AuthManager.kt` — 认证状态管理
- `AuthInterceptor.kt` — 新认证拦截器
- `LoginScreen.kt` — 登录页面
- `LoginViewModel.kt` — 登录 ViewModel
- `Migration7to8.kt` — 数据库升级

## 修改文件
- 14 个 Entity — 加 tenantId 字段
- 12 个 DAO — 加 tenantId 过滤
- 9 个 Repository 实现 — 注入 AuthManager 传 tenantId
- 3 个 BackendSync — 注入 AuthManager 传 tenantId
- NetworkModule — AuthInterceptor 替代 TokenInterceptor
- DatabaseModule — 提供 AuthManager, 加 Migration7to8
- KefuDatabase — 版本 7→8
- AppNavigation — RootNavigation + MainNavigation 分离
- MainActivity — 注入 AuthManager
- BackendSyncWorker — 检查登录状态
- BackendApi — 新增 login 接口
- BackendClient — 新增 login 方法
- BackendDtos — 新增 LoginRequest/LoginResponse
