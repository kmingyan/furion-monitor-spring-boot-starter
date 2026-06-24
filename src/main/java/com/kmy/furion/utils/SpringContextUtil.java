package com.kmy.furion.utils;

import com.kmy.furion.properties.FurionProperties;
import org.apache.commons.logging.Log;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @Author: kmy
 * @Description:
 * @Date: create in 2026-06-22 10:26
 */
public class SpringContextUtil implements ApplicationContextAware {

    // 使用 volatile 保证多线程下的可见性
    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.context = applicationContext;
    }


    /**
     * 根据类型获取 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化完成");
        }
        return context.getBean(clazz);
    }

    /**
     * 根据名称获取 Bean
     */
    public static Object getBean(String name) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化完成");
        }
        return context.getBean(name);
    }


    /**
     * 根据配置的日志级别输出日志
     *
     * @param log     日志对象
     * @param level   日志级别字符串（TRACE/DEBUG/INFO/WARN/ERROR/FATAL），不区分大小写
     * @param message 日志内容
     */
    public static void log(Log log, String level, String message) {
        if (level == null) level = "WARN";
        switch (level.toUpperCase()) {
            case "TRACE": log.trace(message); break;
            case "DEBUG": log.debug(message); break;
            case "INFO":  log.info(message);  break;
            case "ERROR": log.error(message); break;
            case "FATAL": log.fatal(message); break;
            case "WARN":
            default:      log.warn(message);  break;
        }
    }

    /**
     * 根据配置的日志级别输出日志（带异常堆栈）
     */
    public static void log(Log log, String level, String message, Throwable throwable) {
        if (level == null) level = "WARN";
        switch (level.toUpperCase()) {
            case "TRACE": log.trace(message, throwable); break;
            case "DEBUG": log.debug(message, throwable); break;
            case "INFO":  log.info(message, throwable);  break;
            case "ERROR": log.error(message, throwable); break;
            case "FATAL": log.fatal(message, throwable); break;
            case "WARN":
            default:      log.warn(message, throwable);  break;
        }
    }


    /**
     * 获取配置文件中的属性
     * @return
     */
    public static FurionProperties getProperties() {
        try {
            return SpringContextUtil.getBean(FurionProperties.class);
        } catch (Exception e) {
            return null;
        }
    }


}
