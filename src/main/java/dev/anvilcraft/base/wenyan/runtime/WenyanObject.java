package dev.anvilcraft.base.wenyan.runtime;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文言面向对象 —— 实例对象，持有类引用与字段值。
 */
public class WenyanObject {
    private final WenyanClass clazz;
    private final Map<String, WenyanValue> fields = new LinkedHashMap<>();
    private boolean initializing = false;

    public WenyanObject(WenyanClass clazz) {
        this.clazz = clazz;
    }

    public WenyanClass getWenyanClass() { return clazz; }
    public Map<String, WenyanValue> fields() { return fields; }

    /** 标记实例正在初始化（构造器执行期间），此时恒性可被赋值 */
    public void beginInit() { initializing = true; }
    public void endInit() { initializing = false; }

    /** 读取字段值（含可见性检查） */
    public WenyanValue getField(String name, WenyanObject caller) {
        WenyanClass.PropertyDef prop = clazz.lookupProperty(name);
        if (prop == null) {
            throw new IllegalStateException(
                "类「" + clazz.name() + "」无属性「" + name + "」");
        }
        WenyanClass definingClass = findDefiningClass(name);
        checkReadAccess(prop, definingClass, caller);
        return fields.getOrDefault(name, prop.defaultValue() != null ? prop.defaultValue() : WenyanValue.NULL);
    }

    /** 写入字段值（含可见性/只读检查）。构造器执行期间恒性可写。 */
    public void setField(String name, WenyanValue value, WenyanObject caller) {
        WenyanClass.PropertyDef prop = clazz.lookupProperty(name);
        if (prop == null) {
            throw new IllegalStateException(
                "类「" + clazz.name() + "」无属性「" + name + "」");
        }
        if (prop.readonly() && !initializing) {
            throw new IllegalStateException("恆性「" + name + "」不可修改");
        }
        WenyanClass definingClass = findDefiningClass(name);
        checkWriteAccess(prop, definingClass, caller);
        fields.put(name, value);
    }

    /** 查找实际定义该属性的类（沿继承链向上） */
    private WenyanClass findDefiningClass(String name) {
        WenyanClass c = clazz;
        while (c != null) {
            for (WenyanClass.PropertyDef p : c.properties()) {
                if (p.name().equals(name)) return c;
            }
            c = c.parentClass();
        }
        return clazz; // fallback
    }

    private void checkReadAccess(WenyanClass.PropertyDef prop, WenyanClass definingClass, @Nullable WenyanObject caller) {
        if (prop.visibility() == WenyanClass.Visibility.PUBLIC) return;
        if (caller == null) {
            throw new IllegalStateException("不可从外部访问属性「" + prop.name() + "」");
        }
        if (prop.visibility() == WenyanClass.Visibility.PRIVATE) {
            if (caller.clazz != definingClass) {
                throw new IllegalStateException("私有属性「" + prop.name() + "」不可从外部访问");
            }
        }
        if (prop.visibility() == WenyanClass.Visibility.PROTECTED) {
            if (!isSameOrSubclass(caller.clazz, definingClass)) {
                throw new IllegalStateException("密屬性「" + prop.name() + "」不可从外部访问");
            }
        }
    }

    private void checkWriteAccess(WenyanClass.PropertyDef prop, WenyanClass definingClass, @Nullable WenyanObject caller) {
        checkReadAccess(prop, definingClass, caller);
    }

    private boolean isSameOrSubclass(WenyanClass sub, WenyanClass sup) {
        if (sub == sup) return true;
        WenyanClass parent = sub.parentClass();
        while (parent != null) {
            if (parent == sup) return true;
            parent = parent.parentClass();
        }
        return false;
    }

    @Override
    public String toString() {
        return "<「" + clazz.name() + "」之实例>";
    }
}
