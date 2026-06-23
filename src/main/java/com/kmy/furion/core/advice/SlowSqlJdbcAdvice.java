package com.kmy.furion.core.advice;

import net.bytebuddy.asm.Advice;

/**
 * JDBC 层慢 SQL 拦截 Advice
 * 被 ByteBuddy 内联到 java.sql.Statement 实现类的 execute* 方法中。
 * 只保留最小逻辑，复杂计算委托给 SlowSqlMonitor。
 */
public class SlowSqlJdbcAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.This Object statement,
                            @Advice.AllArguments Object[] args,
                            @Advice.Enter long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        // 提取 SQL 文本
        String sql = null;
        if (args != null && args.length > 0 && args[0] instanceof String) {
            // Statement.execute(String sql) 系列方法
            sql = (String) args[0];
        } else if (statement != null) {
            // PreparedStatement — 大多数驱动的 toString() 包含 SQL 文本
            String str = statement.toString();
            if (str != null && !str.isEmpty()) {
                sql = str;
            }
        }
        if (sql == null) {
            sql = "[unknown]";
        }

        SlowSqlMonitor.onSqlExecute(sql, durationMs);
    }
}
