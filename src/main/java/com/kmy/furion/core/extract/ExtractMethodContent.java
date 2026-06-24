package com.kmy.furion.core.extract;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.kmy.furion.core.advice.SlowStackMonitor;
import com.kmy.furion.properties.FurionProperties;
import com.kmy.furion.utils.SpringContextUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.benf.cfr.reader.api.CfrDriver;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.kmy.furion.utils.SpringContextUtil.getProperties;


/**
 * @Author: kmy
 * @Description:
 * @Date: create in 2026-06-24 15:42
 */
public class ExtractMethodContent {

    private static final Log log = LogFactory.getLog(ExtractMethodContent.class);

    /**
     * 获取方法内容
     */
    public static String getMethodContent(Class<?> clazz, Method method) {
        FurionProperties properties = getProperties();
        // 传入目标类自己的 ClassLoader
        InMemorySinkFactory output = new InMemorySinkFactory();
        CfrDriver driver = new CfrDriver.Builder()
                .withClassFileSource(new RuntimeClassFileSource(clazz.getClassLoader()))
                .withOutputSink(output)
                .build();
        driver.analyse(Collections.singletonList(clazz.getName().replace(".", "/") + ".class"));
        String source = output.getSource();
        // 提取重载方法（按名称 + 参数个数）
        String overloadMethod = extractMethodBySignature(source, method.getName(), method.getParameterCount());
        SpringContextUtil.log(log, properties.getLogLevel(),overloadMethod);
        return overloadMethod;
    }

    /**
     * 按方法名提取（返回第一个匹配的方法）
     */
    public static String extractMethodByName(String classSource, String methodName) {
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(classSource).getResult().orElseThrow();
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(methodName))
                .findFirst()
                .map(MethodDeclaration::toString)
                .orElse("// ❌ 未找到方法: " + methodName);
    }

    /**
     * 按方法名 + 参数个数提取（解决重载问题）
     */
    public static String extractMethodBySignature(String classSource, String methodName, int paramCount) {
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(classSource).getResult().orElseThrow();
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(methodName)
                        && m.getParameters().size() == paramCount)
                .findFirst()
                .map(MethodDeclaration::toString)
                .orElse("// ❌ 未找到方法: " + methodName + "(参数个数:" + paramCount + ")");
    }

    /**
     * 获取类中所有方法的源码列表
     */
    public static List<String> getAllMethods(String classSource) {
        JavaParser javaParser = new JavaParser();
        CompilationUnit cu = javaParser.parse(classSource).getResult().orElseThrow();

        // 调试：打印解析的类名
        System.out.println("解析的类: " + cu.getPrimaryTypeName().orElse("Unknown"));
        System.out.println("找到的方法数量: " + cu.findAll(MethodDeclaration.class).size());

        return cu.findAll(MethodDeclaration.class).stream()
                .map(MethodDeclaration::toString)
                .collect(Collectors.toList());
    }




}
