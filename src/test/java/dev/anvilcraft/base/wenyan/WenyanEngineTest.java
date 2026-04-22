package dev.anvilcraft.base.wenyan;

import dev.anvilcraft.base.wenyan.extension.TestExtensionLibrary;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WenyanEngineTest {
    @Test
    void runSimpleLoopExample() throws Exception {
        String source = Files.readString(Path.of("example", "天地，好在否.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertFalse(result.output().isBlank());
    }

    @Test
    void runFunctionExample() throws Exception {
        String source = Files.readString(Path.of("example", "乘算口訣.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertFalse(result.output().isBlank());
    }

    @Test
    void referenceSelectorReadsIndexedArrayValue() {
        String source = """
                吾有一列。名之曰「甲」。
                充「甲」以陽。以陰。
                夫「甲」之二。書之。
                """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("陰" + System.lineSeparator(), result.output());
    }

    @Test
    void printOutputsChineseNumberAndBoolean() {
        String source = """
                吾有一數。曰二十一。書之。
                吾有一爻。曰陽。書之。
                """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("二十一" + nl + "陽" + nl, result.output());
    }

    @Test
    void runSieveExampleProducesPrimeSequence() throws Exception {
        String source = Files.readString(Path.of("example", "埃氏筛.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("[二, 三, 五, 七, 十一, 十三, 十七, 十九, 二十三, 二十九, 三十一, 三十七, 四十一, 四十三, 四十七, 五十三, 五十九, 六十一, 六十七, 七十一, 七十三, 七十九, 八十三, 八十九, 九十七]"
                + System.lineSeparator(), result.output());
    }

    @Test
    void runMergeSortExampleProducesSortedSequence() throws Exception {
        String source = Files.readString(Path.of("example", "歸併排序.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("[一, 二, 三, 十四, 十八]" + System.lineSeparator(), result.output());
    }

    @Test
    void recursiveFunctionsScopedLocally() {
        // variables declared via 名之曰 inside a function must not bleed into caller scope
        String source = """
                吾有一術。名之曰「加一」。欲行是術。必先得一數。曰「甲」。乃行是術曰。
                    加一以「甲」。名之曰「乙」。
                    乃得「乙」。
                是謂「加一」之術也。
                施「加一」於一。名之曰「結果一」。
                施「加一」於三。名之曰「結果二」。
                夫「結果一」。書之。
                夫「結果二」。書之。
                """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("二" + nl + "四" + nl, result.output());
    }

    @Test
    void importPavilionFunctionAndReadAnnotatedFields() {
        String source = """
                吾嘗觀『算經』之書。方悟「取底除」之義。
                施「取底除」於七。於三。名之曰「甲」。
                夫「甲」之『商』。書之。
                夫「甲」之『餘』。書之。
                """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("二" + nl + "一" + nl, result.output());
    }

    @Test
    void runIncrementalSquareRootExample() throws Exception {
        String source = Files.readString(Path.of("example", "增乘開平方.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("二百六十八" + System.lineSeparator(), result.output());
    }

    @Test
    void registerExtensionPackageByPackageInfo() {
        WenyanEngine engine = new WenyanEngine()
                .registerWenyuanPackage("dev.anvilcraft.base.wenyan.extension");
        String source = """
                吾嘗觀『試算館』之書。方悟「倍之」之義。
                施「倍之」於二十一。書之。
                """;
        WenyanEngine.Result result = engine.execute(source);
        assertEquals("四十二" + System.lineSeparator(), result.output());
    }

    @Test
    void registerExtensionClassExplicitly() {
        WenyanEngine engine = new WenyanEngine()
                .registerWenyuanClass(TestExtensionLibrary.class);
        String source = """
                吾嘗觀『試算館』之書。方悟「倍之」之義。
                施「倍之」於九。書之。
                """;
        WenyanEngine.Result result = engine.execute(source);
        assertEquals("十八" + System.lineSeparator(), result.output());
    }
}

