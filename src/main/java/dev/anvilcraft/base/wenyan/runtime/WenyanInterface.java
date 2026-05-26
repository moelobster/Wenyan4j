package dev.anvilcraft.base.wenyan.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文言面向对象 —— 约（接口）定义。
 */
public class WenyanInterface {

    /** 方法签名 */
    public record MethodSignature(String name, List<String> paramTypes) {}

    private final String name;
    private final Map<String, MethodSignature> methods = new LinkedHashMap<>();

    public WenyanInterface(String name) {
        this.name = name;
    }

    public String name() { return name; }
    public Map<String, MethodSignature> methods() { return methods; }

    public void addMethod(String methodName, List<String> paramTypes) {
        methods.put(methodName, new MethodSignature(methodName, paramTypes));
    }

    @Override
    public String toString() {
        return "<约「" + name + "」>";
    }
}
