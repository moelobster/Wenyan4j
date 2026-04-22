package dev.anvilcraft.base.wenyan.runtime;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 文言变量绑定的词法作用域链。
 */
public final class Environment {
    private final @Nullable Environment parent;
    private final Map<String, WenyanValue> values = new HashMap<>();

    /**
     * 创建一个可带父作用域的环境。
     *
     * @param parent 父作用域；为 {@code null} 时表示全局作用域
     */
    public Environment(@Nullable Environment parent) {
        this.parent = parent;
    }

    /**
     * 仅在当前作用域定义或覆盖变量绑定。
     *
     * @param name 变量名
     * @param value 绑定值
     */
    public synchronized void define(String name, WenyanValue value) {
        values.put(name, value);
    }

    /**
     * 从当前作用域开始向父作用域链查找变量绑定。
     *
     * @param name 变量名
     * @return 查找到的值
     */
    public synchronized WenyanValue get(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }
        if (parent != null) {
            return parent.get(name);
        }
        throw new IllegalStateException("Undefined identifier: " + name);
    }

    /**
     * 为最近的已有绑定赋值；若不存在则在全局作用域创建。
     *
     * @param name 变量名
     * @param value 新值
     */
    public synchronized void assign(String name, WenyanValue value) {
        if (values.containsKey(name)) {
            values.put(name, value);
            return;
        }
        if (parent != null) {
            parent.assign(name, value);
            return;
        }
        values.put(name, value);
    }

    /**
     * 从最近存在该变量的作用域中删除绑定。
     *
     * @param name 变量名
     */
    public synchronized void delete(String name) {
        if (values.containsKey(name)) {
            values.remove(name);
            return;
        }
        if (parent != null) {
            parent.delete(name);
            return;
        }
        throw new IllegalStateException("Undefined identifier: " + name);
    }
}

