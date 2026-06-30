package com.kmy.furion.core.handlers;

import lombok.Data;
import lombok.ToString;

/**
 * AI 诊断结果
 * 封装大模型分析结果及原始异常上下文，通过 {@link AiDiagnosisResultHandler} 回调给宿主应用
 *
 * @Author: kmy
 */
@Data
@ToString
public class AiDiagnosisResult {

    /**
     * 发生异常的类全限定名
     */
    private String className;

    /**
     * 发生异常的方法名
     */
    private String methodName;

    /**
     * 异常类名（如 java.lang.NullPointerException）
     */
    private String exceptionType;

    /**
     * 异常消息（可能为 null）
     */
    private String exceptionMessage;

    /**
     * 大模型分析结果（核心字段）
     */
    private String aiAnalysis;
}
