package com.kmy.furion.core.handlers;

/**
 * AI 诊断结果回调接口
 * 宿主应用实现此接口并注册为 Spring Bean，即可接收大模型对异常的分析结果。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Component
 * public class DingTalkNotifyHandler implements AiDiagnosisResultHandler {
 *     &#64;Override
 *     public void onResult(AiDiagnosisResult result) {
 *         // 发送钉钉告警、存数据库、推送邮件等
 *         dingTalk.send("AI诊断: " + result.getAiAnalysis());
 *     }
 * }
 * </pre>
 *
 * @Author: kmy
 */
public interface AiDiagnosisResultHandler {

    /**
     * 当大模型完成异常分析后回调
     *
     * @param result 包含 AI 分析结果和原始异常上下文
     */
    void onResult(AiDiagnosisResult result);
}
