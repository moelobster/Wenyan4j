package dev.anvilcraft.base.wenyan.runtime;

import dev.anvilcraft.base.wenyan.annotation.WenyuanFunction;
import dev.anvilcraft.base.wenyan.annotation.WenyuanPavilion;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * 发现并保存可暴露给文言脚本的 Java 扩展函数。
 */
public final class WenyuanRegistry {
    private static final String BUILTIN_PACKAGE = "dev.anvilcraft.base.wenyan.wenyuan";

    private final ClassLoader classLoader;
    private final Map<String, Map<String, Method>> pavilionMethods = new LinkedHashMap<>();
    private final Set<String> scannedPackages = new LinkedHashSet<>();

    /**
     * 使用当前线程上下文类加载器创建注册表。
     */
    public WenyuanRegistry() {
        this(Thread.currentThread().getContextClassLoader());
    }

    private WenyuanRegistry(@Nullable ClassLoader classLoader) {
        this.classLoader = classLoader == null ? WenyuanRegistry.class.getClassLoader() : classLoader;
    }

    /**
     * 创建并预加载内置文渊阁函数。
     *
     * @return 已包含内置扩展函数的注册表
     */
    public static WenyuanRegistry withDefaults() {
        return new WenyuanRegistry().registerPackage(BUILTIN_PACKAGE);
    }

    /**
     * 创建并预加载内置文渊阁函数。
     *
     * @return 已包含内置扩展函数的注册表
     */
    public static WenyuanRegistry withDefaults(ClassLoader classLoader) {
        return new WenyuanRegistry(Objects.requireNonNull(classLoader)).registerPackage(BUILTIN_PACKAGE);
    }

    /**
     * 扫描并注册某个包内的扩展类。
     *
     * @param packageName 待扫描包名
     * @return 当前注册表（便于链式调用）
     */
    public WenyuanRegistry registerPackage(String packageName) {
        if (!scannedPackages.add(packageName)) {
            return this;
        }

        Package annotatedPackage = loadAnnotatedPackage(packageName);
        WenyuanPavilion pkgAnnotation = annotatedPackage != null
                                        ? annotatedPackage.getAnnotation(WenyuanPavilion.class)
                                        : null;
        String packagePavilionName = pkgAnnotation != null ? pkgAnnotation.value() : null;
        String packageSimplifiedName = pkgAnnotation != null && !pkgAnnotation.simplified().isEmpty()
                                       ? pkgAnnotation.simplified()
                                       : null;

        for (Class<?> clazz : findClassesInPackage(packageName)) {
            WenyuanPavilion classLevel = clazz.getAnnotation(WenyuanPavilion.class);
            String pavilionName = classLevel != null ? classLevel.value() : packagePavilionName;
            if (pavilionName != null) {
                registerClassInternal(clazz, pavilionName);
                String simplifiedPavilion = classLevel != null && !classLevel.simplified().isEmpty()
                                            ? classLevel.simplified()
                                            : packageSimplifiedName;
                if (simplifiedPavilion != null) {
                    registerClassInternalSimplified(clazz, simplifiedPavilion);
                }
            }
        }
        return this;
    }

    /**
     * 注册单个扩展类。
     *
     * @param clazz 带有文渊阁/函数注解的类
     * @return 当前注册表（便于链式调用）
     */
    @SuppressWarnings("UnusedReturnValue")
    public WenyuanRegistry registerClass(Class<?> clazz) {
        String pavilionName = resolvePavilionName(clazz);
        if (pavilionName == null) {
            throw new IllegalStateException("Class or package is missing @WenyuanPavilion: " + clazz.getName());
        }
        registerClassInternal(clazz, pavilionName);
        String simplifiedPavilionName = resolveSimplifiedPavilionName(clazz);
        if (simplifiedPavilionName != null) {
            registerClassInternalSimplified(clazz, simplifiedPavilionName);
        }
        return this;
    }

    /**
     * 获取指定文渊阁已注册的方法映射。
     *
     * @param pavilionName 文言脚本中的文渊阁名
     * @return 不可变的“函数名 -> Java 方法”映射
     */
    public Map<String, Method> methodsFor(String pavilionName) {
        Map<String, Method> methods = pavilionMethods.get(pavilionName);
        if (methods == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(methods);
    }

    /**
     * 创建深拷贝，确保不同引擎实例使用隔离的注册表。
     *
     * @return 注册表快照
     */
    public WenyuanRegistry snapshot() {
        WenyuanRegistry copy = new WenyuanRegistry(classLoader);
        copy.scannedPackages.addAll(scannedPackages);
        for (Map.Entry<String, Map<String, Method>> entry : pavilionMethods.entrySet()) {
            copy.pavilionMethods.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private @Nullable Package loadAnnotatedPackage(String packageName) {
        try {
            Class<?> packageInfo = Class.forName(packageName + ".package-info", false, classLoader);
            Package pkg = packageInfo.getPackage();
            if (pkg == null || pkg.getAnnotation(WenyuanPavilion.class) == null) {
                return null;
            }
            return pkg;
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private @Nullable String resolvePavilionName(Class<?> clazz) {
        WenyuanPavilion classLevel = clazz.getAnnotation(WenyuanPavilion.class);
        if (classLevel != null) {
            return classLevel.value();
        }
        Package pkg = clazz.getPackage();
        if (pkg != null) {
            WenyuanPavilion packageLevel = pkg.getAnnotation(WenyuanPavilion.class);
            if (packageLevel != null) {
                return packageLevel.value();
            }
        }
        return null;
    }

    private @Nullable String resolveSimplifiedPavilionName(Class<?> clazz) {
        WenyuanPavilion classLevel = clazz.getAnnotation(WenyuanPavilion.class);
        if (classLevel != null && !classLevel.simplified().isEmpty()) {
            return classLevel.simplified();
        }
        Package pkg = clazz.getPackage();
        if (pkg != null) {
            WenyuanPavilion packageLevel = pkg.getAnnotation(WenyuanPavilion.class);
            if (packageLevel != null && !packageLevel.simplified().isEmpty()) {
                return packageLevel.simplified();
            }
        }
        return null;
    }

    private void registerClassInternal(Class<?> clazz, String pavilionName) {
        for (Method method : clazz.getDeclaredMethods()) {
            WenyuanFunction function = method.getAnnotation(WenyuanFunction.class);
            if (function == null || !Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            pavilionMethods
                .computeIfAbsent(pavilionName, key -> new LinkedHashMap<>())
                .put(function.value(), method);
        }
    }

    private void registerClassInternalSimplified(Class<?> clazz, String simplifiedPavilionName) {
        for (Method method : clazz.getDeclaredMethods()) {
            WenyuanFunction function = method.getAnnotation(WenyuanFunction.class);
            if (function == null || !Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String funcName = function.simplified().isEmpty() ? function.value() : function.simplified();
            pavilionMethods
                .computeIfAbsent(simplifiedPavilionName, key -> new LinkedHashMap<>())
                .put(funcName, method);
        }
    }

    private List<Class<?>> findClassesInPackage(String packageName) {
        String packagePath = packageName.replace('.', '/');
        List<Class<?>> classes = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    classes.addAll(scanFileResource(packageName, url));
                } else if ("jar".equals(url.getProtocol())) {
                    classes.addAll(scanJarResource(packageName, packagePath, url));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan package: " + packageName, e);
        }
        return classes;
    }

    private List<Class<?>> scanFileResource(String packageName, URL url) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            Path dir = Paths.get(url.toURI());
            if (!Files.isDirectory(dir)) {
                return classes;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> !fileName.equals("package-info.class") && !fileName.equals("module-info.class"))
                    .filter(fileName -> !fileName.contains("$"))
                    .forEach(fileName -> {
                        String className = packageName + "." + fileName.substring(0, fileName.length() - 6);
                        loadClass(className).ifPresent(classes::add);
                    });
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to read package directory: " + packageName, e);
        }
        return classes;
    }

    private List<Class<?>> scanJarResource(String packageName, String packagePath, URL url) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(packagePath + "/") || !name.endsWith(".class")) {
                        continue;
                    }
                    String relative = name.substring(packagePath.length() + 1);
                    if (relative.contains("/") || relative.contains("$")
                        || relative.equals("package-info.class") || relative.equals("module-info.class")) {
                        continue;
                    }
                    String className = packageName + "." + relative.substring(0, relative.length() - 6);
                    loadClass(className).ifPresent(classes::add);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan package in jar: " + packageName, e);
        }
        return classes;
    }

    private Optional<Class<?>> loadClass(String className) {
        try {
            return Optional.of(Class.forName(className, false, classLoader));
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}



