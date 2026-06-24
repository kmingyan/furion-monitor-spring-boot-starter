package com.kmy.furion.core.extract;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * @Author: kmy
 * @Description:
 * @Date: create in 2026-06-24 15:02
 */
public class RuntimeClassFileSource implements ClassFileSource {

    private final ClassLoader classLoader;

    public RuntimeClassFileSource(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {

    }

    @Override
    public Collection<String> addJar(String jarPath) {
        return null;
    }

    @Override
    public String getPossiblyRenamedPath(String path) {
        // ✅ 改成直接返回 path
        return path;
    }

    @Override
    public Pair<byte[], String> getClassFileContent(String path) throws IOException {
        try (InputStream is = classLoader.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Class not found in classloader: " + path);
            }
            byte[] bytes = is.readAllBytes();
            return new Pair<>(bytes, path);
        }
    }
}
