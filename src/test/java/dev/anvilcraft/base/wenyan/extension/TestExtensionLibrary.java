package dev.anvilcraft.base.wenyan.extension;

import dev.anvilcraft.base.wenyan.annotation.WenyuanFunction;
import dev.anvilcraft.base.wenyan.runtime.WenyanCallable;
import dev.anvilcraft.base.wenyan.runtime.WenyanValue;

public final class TestExtensionLibrary {
    private TestExtensionLibrary() {
    }

    @WenyuanFunction("倍之")
    public static int doubleValue(int x) {
        return x * 2;
    }

    @WenyuanFunction("试算")
    public static int testCalc(WenyanCallable callable) {
        try {
            return callable.call(WenyanValue.number(3)).asNumber().intValue() * 2;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw e;
        }
    }
}

