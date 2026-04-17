package dev.anvilcraft.base.wenyan.wenyuan;

import dev.anvilcraft.base.wenyan.annotation.WenyuanField;
import dev.anvilcraft.base.wenyan.annotation.WenyuanFunction;
import dev.anvilcraft.base.wenyan.annotation.WenyuanPavilion;

@WenyuanPavilion("算經")
public class MathematicalClassic {
    public static record DivMod(
        @WenyuanField("商")
        int quotient,
        @WenyuanField("餘")
        int remainder
    ) {
    }

    @WenyuanFunction("取底除")
    public static DivMod divMod(int x, int y) {
        return new DivMod(x / y, x % y);
    }
}
