package dev.anvilcraft.base.wenyan.runtime;

import java.util.List;

/**
 * 可从原生 Java 扩展函数中回调的文言函数接口。
 *
 * <p>使用方法：在带有 {@link dev.anvilcraft.base.wenyan.annotation.WenyuanFunction}
 * 注解的静态方法中，将参数类型声明为 {@code WenyanCallable}，解释器会自动将
 * 文言函数（用户定义或原生）包装后传入。
 *
 * <pre>{@code
 * @WenyuanFunction("映射")
 * public static WenyanValue map(WenyanValue array, WenyanCallable fn) {
 *     List<WenyanValue> result = new ArrayList<>();
 *     for (WenyanValue item : array.asArray()) {
 *         result.add(fn.call(List.of(item)));
 *     }
 *     return WenyanValue.array(result);
 * }
 * }</pre>
 */
@FunctionalInterface
public interface WenyanCallable {
    /**
     * 调用文言函数。
     *
     * @param args 传入的参数列表
     * @return 函数返回值
     */
    WenyanValue call(List<WenyanValue> args);

    /**
     * 调用文言函数。
     *
     * @param args 传入的参数列表
     * @return 函数返回值
     */
    default WenyanValue call(WenyanValue... args) {
        return this.call(List.of(args));
    }
}

