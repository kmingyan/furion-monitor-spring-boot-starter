package com.kmy.furion.config;

import com.kmy.furion.core.advice.AiDiagnosisService;
import com.kmy.furion.core.advice.ExceptionMonitorService;
import com.kmy.furion.core.advice.InvokeStatReporter;
import com.kmy.furion.core.advice.SlowSqlMonitor;
import com.kmy.furion.core.advice.SlowStackMonitor;
import com.kmy.furion.core.advice.TraceLogMonitor;
import com.kmy.furion.core.agent.FurionAgentInstaller;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @Author: kmy
 * @Description: Furion Monitor 自动配置类
 * @Date: create in 2026-06-22 09:44
 */
@AutoConfiguration
@EnableConfigurationProperties(FurionProperties.class)
@ConditionalOnProperty(prefix = "furion.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FurionAutoConfiguration {

    private final FurionProperties properties;

    public FurionAutoConfiguration(FurionProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SpringContextUtil springContextUtil() {
        return new SpringContextUtil();
    }

    @PostConstruct
    public void init() {
        FurionAgentInstaller.install();
        InvokeStatReporter.start(properties.getInvokeStatIntervalSeconds());
    }

    /**
     * 当程序销毁时，关闭所有监控
     */
    @PreDestroy
    public void destroy() {
        SlowStackMonitor.shutdown();
        SlowSqlMonitor.shutdown();
        ExceptionMonitorService.shutdown();
        InvokeStatReporter.shutdown();
        TraceLogMonitor.shutdown();
        AiDiagnosisService.shutdown();
    }
}
