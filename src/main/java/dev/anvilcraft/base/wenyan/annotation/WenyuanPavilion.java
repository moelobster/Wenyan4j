package dev.anvilcraft.base.wenyan.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明扩展类型/包/模块对应的文渊阁名称。
 */
@Target({ElementType.TYPE, ElementType.PACKAGE, ElementType.MODULE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WenyuanPavilion {
    /**
     * 供导入语句使用的文渊阁名称（繁体）。
     *
     * @return 文渊阁名称（用于 {@code 吾嘗觀...之書}）
     */
    String value();

    /**
     * 简化秘术模式下使用的文渊阁简化名称；为空时不注册简化名。
     *
     * @return 简化文渊阁名称
     */
    String simplified() default "";
}
