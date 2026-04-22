package dev.anvilcraft.base.wenyan;

import dev.anvilcraft.base.wenyan.extension.TestExtensionLibrary;
import dev.anvilcraft.base.wenyan.runtime.WenyanPromise;
import dev.anvilcraft.base.wenyan.runtime.WenyanValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WenyanEngineTest {
    @Test
    void simplifiedKeywordsRequireSimplifiedPavilionAtScriptStart() {
        String source = """
            吾有一数。曰二。书之。
            """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
        assertTrue(ex.getMessage().contains("简化秘术"));
    }

    @Test
    void simplifiedKeywordsAndLiteralsWorkAfterSimplifiedPavilionImport() {
        String source = """
            吾嘗觀『简化秘术』之書。
            吾有一数。曰二十一。书之。
            吾有一爻。曰阳。书之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("二十一" + nl + "陽" + nl, result.output());
    }

    @Test
    void simplifiedModeHeaderDoesNotRequireTrailingPeriodOrNewline() {
        String source = "吾嘗觀『简化秘术』之書吾有一数曰三书之";
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("三" + System.lineSeparator(), result.output());
    }

    @Test
    void simplifiedModeDoesNotRewriteStringOrIdentifierLiteralContents() {
        String source = """
            吾嘗觀『简化秘术』之書。
            吾有一言。曰『阳书数』。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("阳书数" + System.lineSeparator(), result.output());
    }

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
        assertEquals(
            "[二, 三, 五, 七, 十一, 十三, 十七, 十九, 二十三, 二十九, 三十一, 三十七, 四十一, 四十三, 四十七, 五十三, 五十九, 六十一, 六十七, 七十一, 七十三, 七十九, 八十三, 八十九, 九十七]"
            + System.lineSeparator(), result.output()
        );
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
    void simplifiedPavilionAndFieldNamesResolveInSimplifiedMode() {
        String source = """
            吾嘗觀『简化秘术』之書。
            吾尝观『算经』之书。方悟「取底除」之义。
            施「取底除」于七。于三。名之曰「甲」。
            夫「甲」之『余』。书之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("一" + System.lineSeparator(), result.output());
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

    @Test
    void registerCallableExtensionClassExplicitly() {
        WenyanEngine engine = new WenyanEngine()
            .registerWenyuanClass(TestExtensionLibrary.class);
        String source = """
            吾嘗觀『試算館』之書。方悟「试算」之義。
            吾有一術。名之曰「加一」。欲行是術。必先得一數。曰「甲」。乃行是術曰。
                加一以「甲」。名之曰「乙」。
                乃得「乙」。
            是謂「加一」之術也。
            施「试算」於「加一」。書之。
            """;
        WenyanEngine.Result result = engine.execute(source);
        assertEquals("八" + System.lineSeparator(), result.output());
    }

    @Test
    void testCallableParams() {
        String source = """
            吾有一術。名之曰「加一」。欲行是術。必先得一數。曰「甲」。乃行是術曰。
                加一以「甲」。名之曰「乙」。
                乃得「乙」。
            是謂「加一」之術也。
            
            吾有一術。名之曰「试算」。欲行是術。必先得一術。曰「甲」。乃行是術曰。
                施「甲」於一。名之曰「乙」。
                乃得「乙」。
            是謂「试算」之術也。
            
            施「试算」於「加一」。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("二" + System.lineSeparator(), result.output());
    }

    @Test
    void nestingFunctionTest() {
        String source = """
            吾有一術。名之曰「试算」。欲行是術。必先得一數。曰「甲」。乃行是術曰。
                吾有一術。名之曰「加一」。欲行是術。必先得一數。曰「乙」。乃行是術曰。
                    加一以「乙」。名之曰「丙」。
                    乃得「丙」。
                是謂「加一」之術也。
                施「加一」於「甲」。名之曰「丁」。
                施「加一」於「丁」。名之曰「戊」。
                乃得「戊」。
            是謂「试算」之術也。
            
            施「试算」於一。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("三" + System.lineSeparator(), result.output());
    }

    @Test
    void asyncFunctionCallReturnsPromiseImmediately() {
        String source = """
            吾有一術。名之曰「延迟」。欲行是術。必先得一數。曰「秒」。乃行是術曰。
                待之以「秒」秒。
                乃得「秒」。
            是謂「延迟」之術也。
            
            施「延迟」於一。
            """;
        long start = System.currentTimeMillis();
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        long end = System.currentTimeMillis();
        assertTrue(end - start < 900);
        assertEquals(WenyanValue.Type.PROMISE, result.lastValue().type());
        WenyanPromise promise = result.lastValue().asPromise();
        assertTrue(promise.isPending());
        assertEquals(WenyanValue.number(1), promise.await());
        assertTrue(promise.isFulfilled());
    }

    @Test
    void scriptCanAwaitPromiseResult() {
        String source = """
            吾有一術。名之曰「延迟」。欲行是術。必先得一數。曰「秒」。乃行是術曰。
                待之以「秒」秒。
                乃得「秒」。
            是謂「延迟」之術也。

            施「延迟」於一。名之曰「期」。
            待之以「期」。
            書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("一" + System.lineSeparator(), result.output());
    }

    @Test
    void scriptCanChainPromiseWithThen() {
        String source = """
            吾有一術。名之曰「加一」。欲行是術。必先得一數。曰「甲」。乃行是術曰。
                待之以零秒。
                加一以「甲」。名之曰「乙」。
                乃得「乙」。
            是謂「加一」之術也。

            施「加一」於一。名之曰「期」。
            夫「期」之「繼以」。
            施其於「加一」。名之曰「後」。
            待之以「後」。
            書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("三" + System.lineSeparator(), result.output());
    }

    @Test
    void waitCallReturnsDirectResultForAsyncFunction() {
        String source = """
            吾有一術。名之曰「延迟」。欲行是術。必先得一數。曰「秒」。乃行是術曰。
                待之以「秒」秒。
                乃得「秒」。
            是謂「延迟」之術也。

            待施「延迟」於一。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("一" + System.lineSeparator(), result.output());
        assertEquals(WenyanValue.Type.NUMBER, result.lastValue().type());
    }

    @Test
    void scriptCanHandleRejectedPromiseWithCrashBranch() {
        String source = """
            吾有一術。名之曰「延迟」。欲行是術。必先得一數。曰「秒」。乃行是術曰。
                待之以「秒」秒。
                乃得「秒」。
            是謂「延迟」之術也。

            吾有一術。名之曰「易致」。欲行是術。必先得一數。曰「期」。乃行是術曰。
                即拒『錯！』。
                乃得「期」。
            是謂「易致」之術也。

            施「易致」於一。名之曰「期」。
            待之以「期」。若非有言『捕获成功』。書之。也。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("捕获成功" + System.lineSeparator(), result.output());
    }

    @Test
    void rejectionCanPassErrorMessageToCrashBranch() {
        String source = """
            吾有一術。名之曰「返錯」。是術曰。
                即拒『致命傷害』。
                乃得零。
            是謂「返錯」之術也。

            施「返錯」。名之曰「期」。
            待之以「期」。若非夫「錯誤」。書之。也。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("致命傷害" + System.lineSeparator(), result.output());
    }

    @Test
    void asyncFunctionRejectionTriggersCrashBranch() {
        String source = """
            吾有一術。名之曰「拒絕」。是術曰。
                待之以零秒。
                即拒『非同步錯誤』。
                乃得零。
            是謂「拒絕」之術也。

            施「拒絕」。名之曰「期」。
            待之以「期」。若非有言『已捕捉』。書之。也。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("已捕捉" + System.lineSeparator(), result.output());
    }
}
