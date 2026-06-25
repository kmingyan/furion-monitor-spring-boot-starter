package com.kmy.furion.core.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmy.furion.annotations.AiDiagnosis;
import com.kmy.furion.core.extract.ExtractMethodContent;
import com.kmy.furion.core.handlers.AiDiagnosisResult;
import com.kmy.furion.core.handlers.AiDiagnosisResultHandler;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 诊断核心服务
 * 从 Advice 中分离，避免字节码内联问题。
 * 负责：提取方法源码、入参安全序列化、采样控制、异步调用大模型、回调宿主应用。
 *
 * Phase 1：仅打印方法内容和入参
 * Phase 2：调用大模型 API 分析异常原因，通过 AiDiagnosisResultHandler 回调结果
 *
 * @Author: kmy
 */
public class AiDiagnosisService {

    private static final Log log = LogFactory.getLog(AiDiagnosisService.class);

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furion-ai-diagnosis");
        t.setDaemon(true);
        return t;
    });

    /**
     * JDK 内置 HttpClient，全局复用，支持 HTTP/2
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 注解缓存：方法 -> @AiDiagnosis 注解属性
     */
    private static final ConcurrentHashMap<Method, AiDiagnosis> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    /**
     * 方法源码缓存：Method -> 反编译后的源码
     */
    private static final ConcurrentHashMap<Method, String> SOURCE_CODE_CACHE = new ConcurrentHashMap<>();

    /**
     * 冷却缓存：防止同一方法+同一异常类型在短时间内重复调用大模型
     * key: className#methodName#exceptionType
     * value: 上次调用的时间戳（毫秒）
     */
    private static final ConcurrentHashMap<String, Long> COOLDOWN_CACHE = new ConcurrentHashMap<>();

    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
    }

    /**
     * 由 AiDiagnosisAdvice.exit() 在方法抛出异常时调用。
     * 注意：此方法必须是 public static，因为会被 Advice 内联代码从目标类中调用。
     */
    public static void onException(Class<?> declaringClass, Method method,
                                    Object[] args, Throwable throwable) {
        FurionProperties properties = getProperties();
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        if (properties.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() >= properties.getSampleRate()) {
            return;
        }

        // 获取注解属性（带缓存）
        AiDiagnosis annotation = getAnnotation(method, declaringClass);
        if (annotation == null) {
            return;
        }

        // 在当前线程中做入参安全序列化（异步线程中对象状态可能已变）
        String[] argStrings = annotation.logArgs() ? toSafeStrings(args) : null;

        // 在当前线程捕获堆栈（异步线程中堆栈已不完整）
        StackTraceElement[] stackTrace = annotation.logStackTrace()
                ? Thread.currentThread().getStackTrace()
                : null;

        // 提取注解属性（避免在异步线程中访问 Method 对象）
        boolean logSourceCode = annotation.logSourceCode();
        boolean logStackTrace = annotation.logStackTrace();

        // 提取 AI 配置（避免在异步线程中访问 Spring Bean）
        FurionProperties.AiDiagnosis aiConfig = properties.getAiDiagnosis();
        boolean aiEnabled = annotation.enabled()
                && aiConfig != null
                && aiConfig.getApiKey() != null
                && !aiConfig.getApiKey().isBlank();

        // 提取方法源码（在当前线程中完成，避免异步线程 ClassLoader 问题）
        String sourceCode = null;
        if (logSourceCode) {
            try {
                sourceCode = getSourceCode(declaringClass, method);
            } catch (Exception e) {
                sourceCode = "// Failed to extract source code: " + e.getMessage();
            }
        }

        // AI 配置快照（传入异步线程）
        final String aiApiKey = aiEnabled ? aiConfig.getApiKey() : null;
        final String aiBaseUrl = aiEnabled ? aiConfig.getBaseUrl() : null;
        final String aiModelName = aiEnabled ? aiConfig.getModelName() : null;
        final int aiTimeoutSeconds = aiEnabled ? aiConfig.getTimeoutSeconds() : 60;
        final int aiCooldownSeconds = aiEnabled ? aiConfig.getCooldownSeconds() : 60;
        final String finalSourceCode = sourceCode;

        ASYNC_EXECUTOR.execute(() ->
                handleException(declaringClass, method, argStrings, throwable,
                        stackTrace, logSourceCode, logStackTrace, properties.getLogLevel(),
                        aiEnabled, aiApiKey, aiBaseUrl, aiModelName, aiTimeoutSeconds,
                        aiCooldownSeconds, finalSourceCode));
    }

    /**
     * 获取 @AiDiagnosis 注解（方法级 > 类级）
     * 带缓存，每 1000 次访问清理一次过期条目
     */
    private static AiDiagnosis getAnnotation(Method method, Class<?> declaringClass) {
        return ANNOTATION_CACHE.compute(method, (k, existing) -> {
            if (existing != null) {
                return existing;
            }
            // 优先方法级
            AiDiagnosis annotation = method.getAnnotation(AiDiagnosis.class);
            if (annotation == null) {
                // 降级到类级
                annotation = declaringClass.getAnnotation(AiDiagnosis.class);
            }
            return annotation;
        });
    }

    /**
     * 安全地将入参数组转为字符串数组。
     * 基础类型和 String 打印值，复杂对象只打印类型，避免触发懒加载等副作用。
     * 必须是 public static，因为会被 Advice 内联代码间接调用。
     */
    public static String[] toSafeStrings(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String[] result = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = safeToString(args[i]);
        }
        return result;
    }

    public static String safeToString(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof String || arg instanceof Number || arg instanceof Boolean || arg instanceof Character) {
            String s = String.valueOf(arg);
            if (s.length() > 500) {
                return s.substring(0, 500) + "...(truncated)";
            }
            return s;
        }
        if (arg.getClass().isArray()) {
            return arg.getClass().getComponentType().getSimpleName() + "[]";
        }
        return arg.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(arg));
    }

    /**
     * 异步线程中的核心处理逻辑：日志输出 + 大模型调用 + 回调
     */
    private static void handleException(Class<?> declaringClass, Method method,
                                         String[] argStrings, Throwable throwable,
                                         StackTraceElement[] stackTrace,
                                         boolean logSourceCode, boolean logStackTrace,
                                         String logLevel,
                                         boolean aiEnabled, String aiApiKey,
                                         String aiBaseUrl, String aiModelName,
                                         int aiTimeoutSeconds, int aiCooldownSeconds,
                                         String sourceCode) {

        // ========== Phase 1：日志输出（始终执行） ==========
        StringBuilder sb = new StringBuilder(1024);
        sb.append("\n[FURION-AI-DIAGNOSIS] exception caught!\n");
        sb.append("   Class   : ").append(declaringClass.getName()).append('\n');
        sb.append("   Method  : ").append(method.getName()).append('\n');

        // 入参
        if (argStrings != null && argStrings.length > 0) {
            sb.append("   Args    : [\n");
            for (int i = 0; i < argStrings.length; i++) {
                sb.append("      [").append(i).append("] ").append(argStrings[i]).append('\n');
            }
            sb.append("   ]\n");
        }

        // 异常信息
        sb.append("   Exception: ").append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(" - ").append(throwable.getMessage());
        }
        sb.append('\n');

        // 调用栈（过滤框架帧）
        if (logStackTrace && stackTrace != null) {
            sb.append("   Stack   :\n");
            for (StackTraceElement element : stackTrace) {
                String cn = element.getClassName();
                if (!isFrameworkFrame(cn)) {
                    sb.append("      at ").append(element).append('\n');
                }
            }
        }

        // 方法源码
        if (logSourceCode && sourceCode != null) {
            sb.append("\n   Method Source Code:\n");
            sb.append("   =================\n");
            for (String line : sourceCode.split("\n")) {
                sb.append("   ").append(line).append('\n');
            }
            sb.append("   =================\n");
        }

        SpringContextUtil.log(log, logLevel, sb.toString(), throwable);

        // ========== Phase 2：大模型分析 ==========
        if (!aiEnabled) {
            return;
        }

        // 冷却检查：同一方法 + 同一异常类型，在冷却窗口内不重复调用大模型
        long cooldownMs = aiCooldownSeconds * 1000L;
        if (cooldownMs > 0) {
            String cooldownKey = declaringClass.getName() + "#" + method.getName()
                    + "#" + throwable.getClass().getName();
            long now = System.currentTimeMillis();
            Long lastCall = COOLDOWN_CACHE.get(cooldownKey);
            if (lastCall != null && (now - lastCall) < cooldownMs) {
                long remainSeconds = (cooldownMs - (now - lastCall)) / 1000;
                SpringContextUtil.log(log, logLevel,
                        "\n[FURION-AI-DIAGNOSIS] AI 分析冷却中，跳过大模型调用"
                                + "（同一方法+同一异常类型 " + aiCooldownSeconds + "秒内不重复调用）"
                                + "，剩余冷却: " + remainSeconds + "秒"
                                + "，方法: " + cooldownKey + "\n");
                return;
            }
            COOLDOWN_CACHE.put(cooldownKey, now);

            // 清理过期的冷却条目
            if (COOLDOWN_CACHE.size() > 100) {
                COOLDOWN_CACHE.entrySet().removeIf(e -> (now - e.getValue()) >= cooldownMs);
            }
        }

        // 调用大模型 API
        String aiAnalysis = callLlmApi(declaringClass, method, argStrings, throwable,
                stackTrace, sourceCode, aiApiKey, aiBaseUrl, aiModelName, aiTimeoutSeconds);

        // 构建回调结果
        AiDiagnosisResult result = new AiDiagnosisResult();
        result.setClassName(declaringClass.getName());
        result.setMethodName(method.getName());
        result.setExceptionType(throwable.getClass().getName());
        result.setExceptionMessage(throwable.getMessage());
        result.setAiAnalysis(aiAnalysis);

        // 输出 AI 分析结果到日志
        SpringContextUtil.log(log, logLevel,
                "\n[FURION-AI-DIAGNOSIS] AI Analysis Result:\n" + aiAnalysis + "\n");

        // 回调宿主应用的 Handler
        AiDiagnosisResultHandler handler = getHandler();
        if (handler != null) {
            try {
                handler.onResult(result);
            } catch (Exception e) {
                log.warn("[FURION-AI-DIAGNOSIS] Handler callback failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 调用大模型 API（OpenAI 兼容格式）
     * 返回分析结果文本，失败时返回错误描述
     */
    private static String callLlmApi(Class<?> declaringClass, Method method,
                                      String[] argStrings, Throwable throwable,
                                      StackTraceElement[] stackTrace, String sourceCode,
                                      String apiKey, String baseUrl, String modelName,
                                      int timeoutSeconds) {
        try {
            String userPrompt = buildUserPrompt(declaringClass, method, argStrings,
                    throwable, stackTrace, sourceCode);

            // 构建请求体
            String requestBody = OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                    "model", modelName,
                    "messages", java.util.List.of(
                            java.util.Map.of(
                                    "role", "system",
                                    "content", "你是一位资深 Java 异常诊断专家。请根据以下异常信息、方法源码和入参，" +
                                            "分析异常的可能原因，并给出简洁的修复建议。用中文回答，格式清晰。"
                            ),
                            java.util.Map.of(
                                    "role", "user",
                                    "content", userPrompt
                            )
                    ),
                    "temperature", 0.3
            ));

            // 确保 baseUrl 格式正确（去除末尾斜杠）
            String endpoint = baseUrl.endsWith("/")
                    ? baseUrl + "chat/completions"
                    : baseUrl + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String error = "[LLM API error: HTTP " + response.statusCode() + ", body=" + response.body() + "]";
                log.warn("[FURION-AI-DIAGNOSIS] " + error);
                return error;
            }

            // 解析响应：提取 choices[0].message.content
            return parseLlmResponse(response.body());

        } catch (Exception e) {
            String error = "[LLM API call failed: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "]";
            log.warn("[FURION-AI-DIAGNOSIS] " + error);
            return error;
        }
    }

    /**
     * 构建发送给大模型的 user prompt
     */
    private static String buildUserPrompt(Class<?> declaringClass, Method method,
                                           String[] argStrings, Throwable throwable,
                                           StackTraceElement[] stackTrace, String sourceCode) {
        StringBuilder prompt = new StringBuilder(512);

        prompt.append("=== 异常信息 ===\n");
        prompt.append("类名：").append(declaringClass.getName()).append('\n');
        prompt.append("方法：").append(method.getName()).append('\n');
        prompt.append("异常类型：").append(throwable.getClass().getName()).append('\n');
        if (throwable.getMessage() != null) {
            prompt.append("异常消息：").append(throwable.getMessage()).append('\n');
        }

        // 入参
        if (argStrings != null && argStrings.length > 0) {
            prompt.append("\n=== 方法入参 ===\n");
            for (int i = 0; i < argStrings.length; i++) {
                prompt.append("[").append(i).append("] ").append(argStrings[i]).append('\n');
            }
        }

        // 方法源码
        if (sourceCode != null && !sourceCode.isBlank()) {
            prompt.append("\n=== 方法源码 ===\n");
            prompt.append(sourceCode).append('\n');
        }

        // 调用栈（仅保留业务帧）
        if (stackTrace != null && stackTrace.length > 0) {
            prompt.append("\n=== 调用栈（业务帧）===\n");
            int count = 0;
            for (StackTraceElement element : stackTrace) {
                if (!isFrameworkFrame(element.getClassName())) {
                    prompt.append("  at ").append(element).append('\n');
                    count++;
                    if (count >= 15) break; // 限制调用栈深度，避免 prompt 过长
                }
            }
        }

        return prompt.toString();
    }

    /**
     * 解析大模型响应，提取 choices[0].message.content
     */
    private static String parseLlmResponse(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                JsonNode content = message.path("content");
                if (!content.isMissingNode()) {
                    return content.asText();
                }
            }
            return "[LLM response parse error: no choices[0].message.content found]";
        } catch (Exception e) {
            return "[LLM response parse error: " + e.getMessage() + ", raw=" + responseBody + "]";
        }
    }

    /**
     * 从 Spring 容器中获取宿主应用注册的 Handler（容错）
     */
    private static AiDiagnosisResultHandler getHandler() {
        try {
            return SpringContextUtil.getBean(AiDiagnosisResultHandler.class);
        } catch (Exception e) {
            // 宿主应用未实现回调接口，静默跳过
            return null;
        }
    }

    /**
     * 获取方法源码（带缓存）
     */
    private static String getSourceCode(Class<?> declaringClass, Method method) {
        return SOURCE_CODE_CACHE.computeIfAbsent(method, k -> {
            try {
                return ExtractMethodContent.getMethodContent(declaringClass, method);
            } catch (Exception e) {
                return "// Failed to decompile: " + e.getMessage();
            }
        });
    }

    private static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isFrameworkFrame(String className) {
        return className.startsWith("java.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.")
                || className.startsWith("jdk.")
                || className.startsWith("org.apache.catalina.")
                || className.startsWith("org.apache.coyote.")
                || className.startsWith("org.apache.tomcat.")
                || className.startsWith("org.springframework.")
                || className.startsWith("com.kmy.furion.")
                || className.startsWith("net.bytebuddy.");
    }
}
