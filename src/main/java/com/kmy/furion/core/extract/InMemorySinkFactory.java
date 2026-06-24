package com.kmy.furion.core.extract;

import org.benf.cfr.reader.api.OutputSinkFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @Author: kmy
 * @Description:
 * @Date: create in 2026-06-24 14:42
 */
public class InMemorySinkFactory implements OutputSinkFactory {
    private final StringBuilder sourceBuilder = new StringBuilder();

    @Override
    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
        // 只接收 JAVA 类型的输出（反编译后的源码）
        if (sinkType == SinkType.JAVA && available.contains(SinkClass.STRING)) {
            return Collections.singletonList(SinkClass.STRING);
        }

        // 接收 EXCEPTION 类型的输出（错误信息）
        if (sinkType == SinkType.EXCEPTION && available.contains(SinkClass.STRING)) {
            return Collections.singletonList(SinkClass.STRING);
        }

        // 其他类型不接收
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
        // 只处理 JAVA 类型的源码输出
        if (sinkType == SinkType.JAVA && sinkClass == SinkClass.STRING) {
            return s -> sourceBuilder.append(s).append("\n");
        }

        // 其他类型的 sink 不做处理
        return ignored -> {};
    }

    /**
     * 获取完整的反编译源码
     */
    public String getSource() {
        return sourceBuilder.toString();
    }

    /**
     * 重置缓冲区（复用时需要）
     */
    public void reset() {
        sourceBuilder.setLength(0);
    }
}
