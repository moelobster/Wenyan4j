package dev.anvilcraft.base.wenyan.wenyuan;

import dev.anvilcraft.base.wenyan.annotation.WenyuanField;
import dev.anvilcraft.base.wenyan.annotation.WenyuanFunction;
import dev.anvilcraft.base.wenyan.annotation.WenyuanPavilion;

/**
 * 提供基础算术能力的内置文渊阁。
 */
@WenyuanPavilion(value = "算經", simplified = "算经")
public class MathematicalClassic {
    /**
     * 工具类无需实例化。
     */
    private MathematicalClassic() {
    }

    /**
     * 不可变的商/余数二元组。
     *
     * @param quotient 商
     * @param remainder 余数
     */
    public static record DivMod(
        @WenyuanField(value = "商", simplified = "商")
        int quotient,
        @WenyuanField(value = "餘", simplified = "余")
        int remainder
    ) {
    }

    /**
     * 计算整数除法的商与余数。
     *
     * @param x 被除数
     * @param y 除数
     * @return 商与余数
     */
    @WenyuanFunction(value = "取底除", simplified = "取底除")
    public static DivMod divMod(int x, int y) {
        return new DivMod(x / y, x % y);
    }
}
