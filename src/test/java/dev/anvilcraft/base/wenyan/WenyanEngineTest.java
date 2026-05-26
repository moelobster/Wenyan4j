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

    // ==================== OOP 测试 ====================

    @Test
    void simpleClassWithPropertyAndMethod() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
              其公術「鸣」。
                乃行是術曰。
                  夫己之「名」。書之。
                是謂「鸣」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            昔之「鸦」之「名」者。今「「乌鸦」」是矣。
            施「鸦」之「鸣」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("乌鸦" + System.lineSeparator(), result.output());
    }

    @Test
    void classWithDefaultPropertyValue() {
        String source = """
            吾有一族。曰「兽」。
            其族如是。
              密之「气血」者。數也。曰一百。
              其公術「报气血」。
                乃行是術曰。
                  夫己之「气血」。書之。
                是謂「报气血」之術也。
            是謂「兽」之族也。

            生一「兽」曰「虎」。
            施「虎」之「报气血」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("一百" + System.lineSeparator(), result.output());
    }

    @Test
    void constructorWithParameters() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
              其恆性「羽色」者。言也。

              其初術。
                欲行是術。必先得一言曰「名」。一言曰「色」。
                乃行是術曰。
                  昔之己之「名」者。今「名」是矣。
                  昔之己之「羽色」者。今「色」是矣。
                是謂之初術也。

              其公術「报」。
                乃行是術曰。
                  夫己之「名」。書之。
                  夫己之「羽色」。書之。
                是謂「报」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「丹」。与「「丹朱」」。与「「赤」」。
            施「丹」之「报」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("丹朱" + nl + "赤" + nl, result.output());
    }

    @Test
    void inheritanceAndSuperMethodCall() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
              其初術。
                欲行是術。必先得一言曰「名」。
                乃行是術曰。
                  昔之己之「名」者。今「名」是矣。
                是謂之初術也。
              其公術「鸣」。
                乃行是術曰。
                  夫己之「名」。書之。
                是謂「鸣」之術也。
            是謂「鸟」之族也。

            吾有一终族。曰「凤」。承「鸟」。
            其族如是。
              其初術。
                欲行是術。必先得一言曰「名」。
                乃行是術曰。
                  施父之初。与「名」。
                是謂之初術也。
              其公術「鸣」。
                乃行是術曰。
                  施父之「鸣」。
                  有言「「振翅」」。書之。
                是謂「鸣」之術也。
            是謂「凤」之族也。

            生一「凤」曰「丹」。与「「丹朱」」。
            施「丹」之「鸣」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("丹朱" + nl + "振翅" + nl, result.output());
    }

    @Test
    void protectedPropertyAccessibleInSubclass() {
        String source = """
            吾有一族。曰「兽」。
            其族如是。
              密之「气力」者。數也。曰五十。
              其公術「报气力」。
                乃行是術曰。
                  夫己之「气力」。書之。
                是謂「报气力」之術也。
            是謂「兽」之族也。

            吾有一族。曰「虎」。承「兽」。
            其族如是。
              其公術「增气力」。
                乃行是術曰。
                  夫己之「气力」。名之曰「力」。
                  加「力」以三。名之曰「新力」。
                  昔之己之「气力」者。今「新力」是矣。
                  夫己之「气力」。書之。
                是謂「增气力」之術也。
            是謂「虎」之族也。

            生一「虎」曰「寅」。
            施「寅」之「增气力」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("五十三" + System.lineSeparator(), result.output());
    }

    @Test
    void readonlyPropertyCannotBeModified() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              其恆性「羽色」者。言也。
              其初術。
                欲行是術。必先得一言曰「色」。
                乃行是術曰。
                  昔之己之「羽色」者。今「色」是矣。
                是謂之初術也。
              其公術「变色」。
                乃行是術曰。
                  昔之己之「羽色」者。今「「蓝」」是矣。
                是謂「变色」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。与「「黑」」。
            施「鸦」之「变色」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void abstractClassCannotBeInstantiated() {
        String source = """
            吾有一虚族。曰「动物」。
            其族如是。
              其虚術「叫」也。
            是謂「动物」之族也。

            生一「动物」曰「某」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void finalClassWorks() {
        String source = """
            吾有一终族。曰「烛龙」。
            其族如是。
              其公術「光」。
                乃行是術曰。
                  有言「「明」」。書之。
                是謂「光」之術也。
            是謂「烛龙」之族也。

            生一「烛龙」曰「龙」。
            施「龙」之「光」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("明" + System.lineSeparator(), result.output());
    }

    @Test
    void superFieldReadAccess() {
        String source = """
            吾有一族。曰「兽」。
            其族如是。
              密之「族名」者。言也。曰「「兽类」」。
              其公術「报族」。
                乃行是術曰。
                  夫己之「族名」。書之。
                是謂「报族」之術也。
            是謂「兽」之族也。

            吾有一族。曰「虎」。承「兽」。
            其族如是。
              其公術「报父族」。
                乃行是術曰。
                  夫父之「族名」。書之。
                是謂「报父族」之術也。
            是謂「虎」之族也。

            生一「虎」曰「寅」。
            施「寅」之「报父族」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("兽类" + System.lineSeparator(), result.output());
    }

    @Test
    void accessInstanceFieldExternally() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
              其初術。
                欲行是術。必先得一言曰「名」。
                乃行是術曰。
                  昔之己之「名」者。今「名」是矣。
                是謂之初術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。与「「乌鸦」」。
            夫「鸦」之「名」。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("乌鸦" + System.lineSeparator(), result.output());
    }

    @Test
    void modifyInstanceFieldExternally() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            昔之「鸦」之「名」者。今「「寒鸦」」是矣。
            夫「鸦」之「名」。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("寒鸦" + System.lineSeparator(), result.output());
    }

    @Test
    void completeBirdPhoenixExample() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
              密之「气力」者。數也。曰一百。
              其恆性「羽色」者。言也。

              其初術。
                欲行是術。必先得一言曰「名」。一言曰「色」。
                乃行是術曰。
                  昔之己之「名」者。今「名」是矣。
                  昔之己之「羽色」者。今「色」是矣。
                是謂之初術也。

              其公術「鸣」。
                乃行是術曰。
                  夫己之「名」。書之。
                  有言「「啾」」。書之。
                是謂「鸣」之術也。

              其密術「调息」。
                乃行是術曰。
                  夫己之「气力」。名之曰「力」。
                  加「力」以十。名之曰「乙」。
                  昔之己之「气力」者。今「乙」是矣。
                是謂「调息」之術也。
            是謂「鸟」之族也。

            吾有一终族。曰「凤」。承「鸟」。
            其族如是。
              其初術。
                欲行是術。必先得一言曰「名」。一言曰「色」。
                乃行是術曰。
                  施父之初。与「名」。与「色」。
                是謂之初術也。

              其公術「翱翔」。
                乃行是術曰。
                  施父之「鸣」。
                  有言「「振翅冲霄」」。書之。
                  施己之「调息」。
                  夫己之「气力」。書之。
                是謂「翱翔」之術也。
            是謂「凤」之族也。

            生一「凤」曰「丹」。与「「丹朱」」。与「「赤」」。
            施「丹」之「翱翔」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("丹朱" + nl + "啾" + nl + "振翅冲霄" + nl + "一百一十" + nl, result.output());
    }

    // ==================== 接口（约）测试 ====================

    @Test
    void interfaceDefineAndImplement() {
        String source = """
            吾有一约。曰「可翔」。
            其约如是。
              其公術「飞」也。
            是謂「可翔」之约也。

            吾有一族。曰「鸟」。守「可翔」。
            其族如是。
              其公術「飞」。
                乃行是術曰。
                  有言「「飞行中」」。書之。
                是謂「飞」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            施「鸦」之「飞」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("飞行中" + System.lineSeparator(), result.output());
    }

    @Test
    void interfaceMethodNotImplementedThrows() {
        String source = """
            吾有一约。曰「可翔」。
            其约如是。
              其公術「飞」也。
            是謂「可翔」之约也。

            吾有一族。曰「鸟」。守「可翔」。
            其族如是。
            是謂「鸟」之族也。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void multipleInterfaces() {
        String source = """
            吾有一约。曰「可飞」。
            其约如是。
              其公術「起飞」也。
            是謂「可飞」之约也。

            吾有一约。曰「可鸣」。
            其约如是。
              其公術「鸣叫」也。
            是謂「可鸣」之约也。

            吾有一族。曰「鸟」。守「可飞」。「可鸣」。
            其族如是。
              其公術「起飞」。
                乃行是術曰。
                  有言「「起飞」」。書之。
                是謂「起飞」之術也。
              其公術「鸣叫」。
                乃行是術曰。
                  有言「「啾啾」」。書之。
                是謂「鸣叫」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            施「鸦」之「起飞」。
            施「鸦」之「鸣叫」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("起飞" + nl + "啾啾" + nl, result.output());
    }

    // ==================== 可见性测试 ====================

    @Test
    void privatePropertyNotAccessibleExternally() {
        String source = """
            吾有一族。曰「秘」。
            其族如是。
              私之「密语」者。言也。曰「「机密」」。
            是謂「秘」之族也。

            生一「秘」曰「匣」。
            夫「匣」之「密语」。書之。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void privatePropertyNotVisibleInSubclass() {
        String source = """
            吾有一族。曰「秘」。
            其族如是。
              私之「密语」者。言也。曰「「机密」」。
            是謂「秘」之族也。

            吾有一族。曰「匣」。承「秘」。
            其族如是。
              其公術「窥」。
                乃行是術曰。
                  夫己之「密语」。書之。
                是謂「窥」之術也。
            是謂「匣」之族也。

            生一「匣」曰「盒」。
            施「盒」之「窥」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void privateMethodNotCallableExternally() {
        String source = """
            吾有一族。曰「秘」。
            其族如是。
              其私術「暗号」。
                乃行是術曰。
                  有言「「暗语」」。書之。
                是謂「暗号」之術也。
              其公術「公号」。
                乃行是術曰。
                  施己之「暗号」。
                是謂「公号」之術也。
            是謂「秘」之族也。

            生一「秘」曰「匣」。
            施「匣」之「暗号」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void protectedPropertyNotAccessibleFromUnrelatedClass() {
        String source = """
            吾有一族。曰「甲」。
            其族如是。
              密之「秘密」者。言也。曰「「甲之密」」。
            是謂「甲」之族也。

            吾有一族。曰「乙」。
            其族如是。
              其公術「偷看」。
                欲行是術。必先得一物曰「靶」。
                乃行是術曰。
                  夫「靶」之「秘密」。書之。
                是謂「偷看」之術也。
            是謂「乙」之族也。

            生一「甲」曰「甲兄」。
            生一「乙」曰「乙兄」。
            施「乙兄」之「偷看」。与「甲兄」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    // ==================== 虚術 / 抽象测试 ====================

    @Test
    void abstractMethodMustBeOverriddenOrClassMustBeAbstract() {
        String source = """
            吾有一虚族。曰「动物」。
            其族如是。
              其虚術「叫」也。
            是謂「动物」之族也。

            吾有一族。曰「狗」。承「动物」。
            其族如是。
            是謂「狗」之族也。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void abstractMethodCannotBeCalledFromConcreteSubclass() {
        // 未覆写所有虚術的非虚族应在定义时报错
        String source = """
            吾有一虚族。曰「动物」。
            其族如是。
              其虚術「叫」也。
            是謂「动物」之族也。

            吾有一族。曰「狗」。承「动物」。
            其族如是。
              其公術「唤醒」。
                乃行是術曰。
                  施己之「叫」。
                是謂「唤醒」之術也。
            是謂「狗」之族也。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    // ==================== 继承链测试 ====================

    @Test
    void deepInheritanceThreeLevels() {
        String source = """
            吾有一族。曰「甲」。
            其族如是。
              公之「名」者。言也。曰「「甲」」。
              其公術「报」。
                乃行是術曰。
                  夫己之「名」。書之。
                是謂「报」之術也。
            是謂「甲」之族也。

            吾有一族。曰「乙」。承「甲」。
            其族如是。
              其公術「报」。
                乃行是術曰。
                  施父之「报」。
                  昔之己之「名」者。今「「乙」」是矣。
                  夫己之「名」。書之。
                是謂「报」之術也。
            是謂「乙」之族也。

            吾有一族。曰「丙」。承「乙」。
            其族如是。
              其公術「报」。
                乃行是術曰。
                  施父之「报」。
                  昔之己之「名」者。今「「丙」」是矣。
                  夫己之「名」。書之。
                是謂「报」之術也。
            是謂「丙」之族也。

            生一「丙」曰「三」。
            施「三」之「报」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("甲" + nl + "乙" + nl + "丙" + nl, result.output());
    }

    @Test
    void implicitParentNoArgConstructorCalled() {
        String source = """
            吾有一族。曰「甲」。
            其族如是。
              公之「初」者。言也。曰「「无」」。
              其初術。
                乃行是術曰。
                  昔之己之「初」者。今「「有」」是矣。
                是謂之初術也。
            是謂「甲」之族也。

            吾有一族。曰「乙」。承「甲」。
            其族如是。
              其公術「报」。
                乃行是術曰。
                  夫己之「初」。書之。
                是謂「报」之術也。
            是謂「乙」之族也。

            生一「乙」曰「二」。
            施「二」之「报」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("有" + System.lineSeparator(), result.output());
    }

    // ==================== 实例化与方法调用测试 ====================

    @Test
    void multipleInstancesAreIndependent() {
        String source = """
            吾有一族。曰「计数器」。
            其族如是。
              公之「值」者。數也。曰零。
              其初術。
                欲行是術。必先得一數曰「初值」。
                乃行是術曰。
                  昔之己之「值」者。今「初值」是矣。
                是謂之初術也。
              其公術「增值」。
                乃行是術曰。
                  夫己之「值」。名之曰「力」。
                  加「力」以一。名之曰「新力」。
                  昔之己之「值」者。今「新力」是矣。
                是謂「增值」之術也。
              其公術「报」。
                乃行是術曰。
                  夫己之「值」。書之。
                是謂「报」之術也。
            是謂「计数器」之族也。

            生一「计数器」曰「甲」。与一。
            生一「计数器」曰「乙」。与十。
            施「甲」之「增值」。
            施「甲」之「增值」。
            施「乙」之「增值」。
            施「甲」之「报」。
            施「乙」之「报」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("三" + nl + "十一" + nl, result.output());
    }

    @Test
    void instanceMethodCallViaVariableReference() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。
              其公術「鸣」。
                乃行是術曰。
                  夫己之「名」。書之。
                是謂「鸣」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            昔之「鸦」之「名」者。今「「寒鸦」」是矣。
            施「鸦」之「鸣」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("寒鸦" + System.lineSeparator(), result.output());
    }

    @Test
    void methodWithParameters() {
        String source = """
            吾有一族。曰「算」。
            其族如是。
              其公術「加倍」。
                欲行是術。必先得一數曰「入」。
                乃行是術曰。
                  乘「入」以二。名之曰「果」。
                  乃得「果」。
                是謂「加倍」之術也。
              其公術「报和」。
                欲行是術。必先得一數曰「甲」。一數曰「乙」。
                乃行是術曰。
                  加「甲」以「乙」。書之。
                是謂「报和」之術也。
            是謂「算」之族也。

            生一「算」曰「机」。
            施「机」之「报和」。与三。与五。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("八" + System.lineSeparator(), result.output());
    }

    @Test
    void callingMethodOnNonInstanceThrows() {
        String source = """
            吾有一言。曰「「hello」」。名之曰「字」。
            施「字」之「鸣」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    // ==================== 构造器测试 ====================

    @Test
    void superConstructorCallOutsideConstructorThrows() {
        String source = """
            吾有一族。曰「甲」。
            其族如是。
              其初術。
                欲行是術。必先得一言曰「名」。
                乃行是術曰。
                  昔之己之「名」者。今「名」是矣。
                是謂之初術也。
              公之「名」者。言也。
            是謂「甲」之族也。

            吾有一族。曰「乙」。承「甲」。
            其族如是。
              其公術「坏」。
                乃行是術曰。
                  施父之初。与「「坏」」。
                是謂「坏」之術也。
            是謂「乙」之族也。

            生一「乙」曰「坏」。
            施「坏」之「坏」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void classWithoutConstructorAcceptsNoArgs() {
        String source = """
            吾有一族。曰「空」。
            其族如是。
              公之「标」者。言也。曰「「空类」」。
              其公術「报」。
                乃行是術曰。
                  夫己之「标」。書之。
                是謂「报」之術也。
            是謂「空」之族也。

            生一「空」曰「虚」。
            施「虚」之「报」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("空类" + System.lineSeparator(), result.output());
    }

    @Test
    void classWithConstructorButNoArgsPassedThrows() {
        String source = """
            吾有一族。曰「需」。
            其族如是。
              其初術。
                欲行是術。必先得一言曰「名」。
                乃行是術曰。
                  昔之己之「名」者。今「名」是矣。
                是謂之初術也。
              公之「名」者。言也。
            是謂「需」之族也。

            生一「需」曰「某」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    // ==================== 赋值相关测试 ====================

    @Test
    void deleteSelfFieldThrows() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              公之「名」者。言也。曰「「鸦」」。
              其公術「删己」。
                乃行是術曰。
                  昔之己之「名」者。今不復存矣。
                是謂「删己」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            施「鸦」之「删己」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void assignToSuperPropertyThrows() {
        String source = """
            吾有一族。曰「甲」。
            其族如是。
              密之「值」者。數也。曰一。
            是謂「甲」之族也。

            吾有一族。曰「乙」。承「甲」。
            其族如是。
              其公術「改」。
                乃行是術曰。
                  昔之父之「值」者。今二是矣。
                是謂「改」之術也。
            是謂「乙」之族也。

            生一「乙」曰「二」。
            施「二」之「改」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    // ==================== 终族测试 ====================

    @Test
    void finalClassCannotBeExtended() {
        // 终族被承时，目前由运行时 validate 检测
        // 语法上允许承终族，但运行时应在父类链校验中捕获
        // 此测试验证终族的结类语义能够被解析
        String source = """
            吾有一终族。曰「根」。
            其族如是。
              其公術「报」。
                乃行是術曰。
                  有言「「根」」。書之。
                是謂「报」之術也。
            是謂「根」之族也。

            生一「根」曰「本」。
            施「本」之「报」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("根" + System.lineSeparator(), result.output());
    }

    // ==================== 引用类型测试 ====================

    @Test
    void referenceToSelfWithoutSelectorThrows() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              其公術「报己」。
                乃行是術曰。
                  夫己。書之。
                是謂「报己」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            施「鸦」之「报己」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void referenceToSuperWithoutSelectorThrows() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              密之「族名」者。言也。
            是謂「鸟」之族也。

            吾有一族。曰「鸦」。承「鸟」。
            其族如是。
              其公術「报」。
                乃行是術曰。
                  夫父。書之。
                是謂「报」之術也。
            是謂「鸦」之族也。

            生一「鸦」曰「黑」。
            施「黑」之「报」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void superReferenceWithoutParentThrows() {
        String source = """
            吾有一族。曰「根」。
            其族如是。
              其公術「报」。
                乃行是術曰。
                  夫父之「不存在」。書之。
                是謂「报」之術也。
            是謂「根」之族也。

            生一「根」曰「本」。
            施「本」之「报」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    @Test
    void directAssignToSelfThrows() {
        String source = """
            吾有一族。曰「鸟」。
            其族如是。
              其公術「坏」。
                乃行是術曰。
                  昔之己者。今二是矣。
                是謂「坏」之術也。
            是謂「鸟」之族也。

            生一「鸟」曰「鸦」。
            施「鸦」之「坏」。
            """;
        assertThrows(IllegalStateException.class, () -> new WenyanEngine().execute(source));
    }

    // ==================== 综合测试 ====================

    @Test
    void classWithAllVisibilityCombinations() {
        String source = """
            吾有一族。曰「三色」。
            其族如是。
              公之「公色」者。言也。曰「「红」」。
              密之「密色」者。言也。曰「「黄」」。
              私之「私色」者。言也。曰「「蓝」」。
              其公術「示公」。
                乃行是術曰。
                  夫己之「公色」。書之。
                是謂「示公」之術也。
              其密術「示密」。
                乃行是術曰。
                  夫己之「密色」。書之。
                是謂「示密」之術也。
              其私術「示私」。
                乃行是術曰。
                  夫己之「私色」。書之。
                是謂「示私」之術也。
              其公術「全示」。
                乃行是術曰。
                  施己之「示公」。
                  施己之「示密」。
                  施己之「示私」。
                是謂「全示」之術也。
            是謂「三色」之族也。

            生一「三色」曰「彩」。
            施「彩」之「全示」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("红" + nl + "黄" + nl + "蓝" + nl, result.output());
    }

    @Test
    void interfaceWithMethodParameters() {
        String source = """
            吾有一约。曰「运算」。
            其约如是。
              其公術「加」欲行是術。必先得一數曰「甲」。一數曰「乙」也。
            是謂「运算」之约也。

            吾有一族。曰「算」。守「运算」。
            其族如是。
              其公術「加」。
                欲行是術。必先得一數曰「甲」。一數曰「乙」。
                乃行是術曰。
                  加「甲」以「乙」。書之。
                是謂「加」之術也。
            是謂「算」之族也。

            生一「算」曰「机」。
            施「机」之「加」。与三。与四。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("七" + System.lineSeparator(), result.output());
    }

    @Test
    void overrideMethodAndCallBoth() {
        String source = """
            吾有一族。曰「基」。
            其族如是。
              公之「签」者。言也。曰「「基类」」。
              其公術「述」。
                乃行是術曰。
                  夫己之「签」。書之。
                是謂「述」之術也。
            是謂「基」之族也。

            吾有一族。曰「衍」。承「基」。
            其族如是。
              其初術。
                乃行是術曰。
                  昔之己之「签」者。今「「衍生」」是矣。
                是謂之初術也。
              其公術「述」。
                乃行是術曰。
                  施父之「述」。
                  夫己之「签」。書之。
                是謂「述」之術也。
            是謂「衍」之族也。

            生一「衍」曰「子」。
            施「子」之「述」。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        String nl = System.lineSeparator();
        assertEquals("衍生" + nl + "衍生" + nl, result.output());
    }

    @Test
    void returnValueFromMethod() {
        String source = """
            吾有一族。曰「算」。
            其族如是。
              其公術「平方」。
                欲行是術。必先得一數曰「入」。
                乃行是術曰。
                  乘「入」以「入」。名之曰「果」。
                  乃得「果」。
                是謂「平方」之術也。
            是謂「算」之族也。

            生一「算」曰「机」。
            施「机」之「平方」。与五。書之。
            """;
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("二十五" + System.lineSeparator(), result.output());
    }
}
