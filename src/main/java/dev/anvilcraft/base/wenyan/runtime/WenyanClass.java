package dev.anvilcraft.base.wenyan.runtime;

import dev.anvilcraft.base.wenyan.parser.wenyanParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文言面向对象 —— 类定义。
 */
public class WenyanClass {
    /** 可见性 */
    public enum Visibility { PUBLIC, PRIVATE, PROTECTED }

    /** 属性定义 */
    public record PropertyDef(
        String name,
        Visibility visibility,
        boolean readonly,
        String type,
        @Nullable WenyanValue defaultValue
    ) {}

    /** 方法定义 */
    public record MethodDef(
        String name,
        Visibility visibility,
        boolean isAbstract,
        List<String> paramNames,
        List<wenyanParser.StatementContext> body,
        Environment closure
    ) {}

    /** 构造器定义 */
    public record ConstructorDef(
        List<String> paramNames,
        List<wenyanParser.StatementContext> body,
        Environment closure
    ) {}

    private final String name;
    private @Nullable WenyanClass parentClass;
    private final List<WenyanInterface> interfaces = new ArrayList<>();
    private final boolean isAbstract;
    private final boolean isFinal;
    private final List<PropertyDef> properties = new ArrayList<>();
    private final Map<String, MethodDef> methods = new LinkedHashMap<>();
    private @Nullable ConstructorDef constructor;

    public WenyanClass(String name, boolean isAbstract, boolean isFinal) {
        this.name = name;
        this.isAbstract = isAbstract;
        this.isFinal = isFinal;
    }

    public String name() { return name; }
    public @Nullable WenyanClass parentClass() { return parentClass; }
    public void setParentClass(@Nullable WenyanClass parentClass) { this.parentClass = parentClass; }
    public List<WenyanInterface> interfaces() { return interfaces; }
    public boolean isAbstract() { return isAbstract; }
    public boolean isFinal() { return isFinal; }
    public List<PropertyDef> properties() { return properties; }
    public Map<String, MethodDef> methods() { return methods; }
    public @Nullable ConstructorDef constructor() { return constructor; }
    public void setConstructor(@Nullable ConstructorDef constructor) { this.constructor = constructor; }

    public void addProperty(PropertyDef prop) { properties.add(prop); }
    public void addMethod(MethodDef method) { methods.put(method.name(), method); }
    public void addInterface(WenyanInterface iface) { interfaces.add(iface); }

    /**
     * 在本类及父类链中查找方法定义，子类优先。
     */
    public @Nullable MethodDef lookupMethod(String methodName) {
        MethodDef def = methods.get(methodName);
        if (def != null) return def;
        if (parentClass != null) return parentClass.lookupMethod(methodName);
        return null;
    }

    /**
     * 在本类及父类链中查找属性定义。
     */
    public @Nullable PropertyDef lookupProperty(String propName) {
        for (PropertyDef p : properties) {
            if (p.name().equals(propName)) return p;
        }
        if (parentClass != null) return parentClass.lookupProperty(propName);
        return null;
    }

    /**
     * 检查是否实现了指定约（接口）。
     */
    public boolean implementsInterface(String interfaceName) {
        for (WenyanInterface iface : interfaces) {
            if (iface.name().equals(interfaceName)) return true;
        }
        if (parentClass != null) return parentClass.implementsInterface(interfaceName);
        return false;
    }

    /**
     * 验证类的完整性：虚術是否全部覆写、接口方法是否全部实现。
     */
    public void validate() {
        if (isAbstract) return;
        // 检查所有继承的虚術是否已被覆写
        List<String> abstractMethods = collectAbstractMethods();
        for (String am : abstractMethods) {
            if (!methods.containsKey(am)) {
                throw new IllegalStateException(
                    "类「" + name + "」未覆写虚術「" + am + "」，应声明为虚族");
            }
        }
        // 检查所有接口方法是否已实现
        for (WenyanInterface iface : interfaces) {
            for (WenyanInterface.MethodSignature sig : iface.methods().values()) {
                MethodDef impl = lookupMethod(sig.name());
                if (impl == null || impl.isAbstract()) {
                    throw new IllegalStateException(
                        "类「" + name + "」未实现约「" + iface.name() + "」之術「" + sig.name() + "」");
                }
            }
        }
    }

    private List<String> collectAbstractMethods() {
        List<String> result = new ArrayList<>();
        if (parentClass != null) {
            result.addAll(parentClass.collectAbstractMethods());
        }
        for (MethodDef m : methods.values()) {
            if (m.isAbstract()) {
                result.add(m.name());
            } else {
                result.remove(m.name()); // 被覆写了
            }
        }
        for (WenyanInterface iface : interfaces) {
            for (String sigName : iface.methods().keySet()) {
                if (!result.contains(sigName)) {
                    result.add(sigName);
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "<族「" + name + "」>";
    }
}
