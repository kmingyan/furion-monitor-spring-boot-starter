# Furion Monitor Spring Boot Starter - 项目记忆

## 项目概述
- **项目名称**: furion-monitor-spring-boot-starter
- **定位**: 基于 ByteBuddy 字节码增强的 Spring Boot 监控 Starter，提供无侵入式监控能力
- **作者**: kmy
- **创建日期**: 2026-06-22
- **当前版本**: 1.0.0

## 技术栈
- **Java**: 17
- **Spring Boot**: 3.3.5（使用 `@AutoConfiguration` 新机制，非 spring.factories）
- **ByteBuddy**: 1.14.12（核心字节码增强框架）
- **Lombok**: 1.18.24
- **SLF4J**: 2.0.9（通过 commons-logging 桥接）

## 项目结构
```
com.kmy.furion
├── annotations/              # 监控注解定义
│   ├── @SlowStack            # 慢方法监控注解（支持 thresholdMs）
│   ├── @SlowSql              # 慢 SQL 监控注解（支持 thresholdMs）
│   ├── @ExceptionMonitor     # 异常监控注解
│   ├── @InvokeStat           # 方法调用统计注解（QPS/耗时/百分位）
│   └── @TraceLog             # 方法追踪日志注解（耗时/入参/出参，支持 logArgs/logResult）
├── config/
│   └── FurionAutoConfiguration  # Spring Boot 自动配置入口
├── properties/
│   └── FurionProperties      # 配置属性（前缀: furion.monitor）
├── core/
│   ├── agent/
│   │   └── FurionAgentInstaller  # ByteBuddy Agent 统一安装器
│   └── advice/               # Advice + Monitor 分离架构
│       ├── SlowStackAdvice + SlowStackMonitor      # 慢方法监控
│       ├── SlowSqlJdbcAdvice + SlowSqlMonitor      # 慢 SQL 监控（JDBC 层拦截）
│       ├── ExceptionMonitorAdvice + ExceptionMonitorService  # 异常监控
│       ├── InvokeStatAdvice + InvokeStatCollector  # 调用统计采集
│       ├── InvokeStatReporter                      # 调用统计定时报告
│       ├── MethodMetrics                           # 单方法指标统计（LongAdder+环形采样）
│       └── TraceLogAdvice + TraceLogMonitor        # 方法追踪日志（耗时/入参/出参）
└── utils/
    └── SpringContextUtil     # ApplicationContext 持有工具类
```

## 核心架构设计

### 1. Advice + Monitor 分离模式
- **Advice 类**: 只包含最小逻辑（`@Advice.OnMethodEnter/Exit`），会被 ByteBuddy 内联到目标方法的字节码中
- **Monitor/Service 类**: 包含所有重逻辑（阈值解析、堆栈分析、异步日志），通过 public static 方法被 Advice 调用
- **原因**: Advice 代码被内联后运行在目标类的 ClassLoader 上下文中，不能访问非 public 方法

### 2. ByteBuddy Agent 安装（FurionAgentInstaller）
- 使用 `ByteBuddyAgent.install()` 获取 Instrumentation 实例（只调用一次）
- 使用 `AgentBuilder` 链式注册五组拦截规则：
  - **链1**: `@SlowStack` → 匹配注解标注的类/方法 → `SlowStackAdvice`
  - **链2**: `Statement` 子类 → 匹配 `execute*` 方法 → `SlowSqlJdbcAdvice`
  - **链3**: `@ExceptionMonitor` → 匹配注解标注的类/方法 → `ExceptionMonitorAdvice`
  - **链4**: `@InvokeStat` → 匹配注解标注的类/方法 → `InvokeStatAdvice`
  - **链5**: `@TraceLog` → 匹配注解标注的类/方法 → `TraceLogAdvice`
- 使用 `RedefinitionStrategy.RETRANSFORMATION` 支持已加载类的重新转换
- 忽略 `sun.*`, `com.sun.*`, `net.bytebuddy.*`, `com.kmy.furion.*` 包

### 3. 阈值优先级
方法级注解 `thresholdMs` > 类级注解 `thresholdMs` > 全局配置 `furion.monitor.slow-threshold-ms`

### 4. 采样与异步
- 全局采样率 `furion.monitor.sample-rate` (0.0~1.0)
- 日志输出通过独立的 daemon 线程池异步执行，不阻塞业务线程
- 堆栈和入参在业务线程中捕获，异步线程中格式化输出

## 配置属性 (FurionProperties)
| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `furion.monitor.enabled` | boolean | true | 总开关 |
| `furion.monitor.slow-threshold-ms` | long | 2000 | 慢方法全局阈值(ms) |
| `furion.monitor.slow-sql-threshold-ms` | long | 1000 | 慢 SQL 全局阈值(ms) |
| `furion.monitor.sample-rate` | double | 1.0 | 采样率 |
| `furion.monitor.log-level` | String | WARN | 日志级别(已声明但未使用) |
| `furion.monitor.invoke-stat-interval-seconds` | long | 60 | 调用统计报告间隔(秒) |

## 已完成的监控能力
1. **@SlowStack** - 慢方法监控：拦截标注 `@SlowStack` 的方法，超过阈值输出 warn 日志 + 调用栈
2. **SlowSql (JDBC层)** - 慢 SQL 监控：拦截 `java.sql.Statement.execute*`，自动提取 SQL 文本，分析调用链定位 DAO 层
3. **@ExceptionMonitor** - 异常监控：拦截标注 `@ExceptionMonitor` 的方法，捕获异常并记录入参和调用栈
4. **@InvokeStat** - 方法调用统计：拦截标注 `@InvokeStat` 的方法，统计 Calls/Avg/P50/P90/P99/Max，定时汇总输出日志（LongAdder 计数 + 环形数组采样 1024 条算百分位）
5. **@TraceLog** - 方法追踪日志：拦截标注 `@TraceLog` 的方法，每次调用记录执行耗时、入参和返回值。支持 `logArgs`/`logResult` 属性控制是否记录入参和出参。注解属性解析带 `ConcurrentHashMap` 缓存（每 1000 次访问清理一次）。`Advice.enter()` 返回 `Object[]`（首元素为 startNanos，后续为方法入参），`Advice.exit()` 通过 `@Advice.Return(readOnly = true)` 捕获返回值

## 待实现的功能（用户目标）
- **Spring Boot 监控**: Actuator 健康指标、请求耗时统计等
- **JVM 监控**: 内存使用、GC 活动、线程状态、CPU 使用率、类加载等

## 注意事项 / 已知问题
1. **缺少 AutoConfiguration 注册文件**（已存在）: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 已正确注册 `FurionAutoConfiguration`
2. **`log-level` 属性未生效**（已修复）: `SpringContextUtil` 新增 `log(Log, level, msg)` 和 `log(Log, level, msg, throwable)` 动态日志路由方法，四个 Monitor/Reporter 已统一调用
3. **线程池未关闭**（已修复）: 各 Monitor 的 ExecutorService 已添加 `shutdown()` 方法，由 `FurionAutoConfiguration.@PreDestroy` 统一调用
4. **慢 SQL 的 SQL 提取**: PreparedStatement 通过 `toString()` 提取 SQL，依赖 JDBC 驱动实现，部分驱动可能不包含完整 SQL
5. **ThresholdCache**（已修复）: `SlowStackMonitor` 添加了基于访问计数的定期清理（每 1000 次访问清除过期条目）；`SlowSqlMonitor` 中的 `THRESHOLD_CACHE` 实际从未被读写，已作为死代码移除

## 构建与发布
- **Maven 命令**: 需通过 `cmd.exe //c` 执行（见全局 CLAUDE.md）
- **发布仓库**: 私有 Maven 仓库 `https://gz01-srdart.srdcloud.cn/maven/composq-tplibrary/ctywyy_cnos-lshare-maven-mc/`
- **无 git 仓库**: 当前项目未初始化 git
- **无测试代码**: 尚未编写单元测试或集成测试
