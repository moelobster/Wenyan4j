package dev.anvilcraft.base.wenyan;

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
    void runSieveExampleProducesPrimeSequence() throws Exception {
        String source = Files.readString(Path.of("example", "埃氏筛.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("[2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97]"
                + System.lineSeparator(), result.output());
    }

    @Test
    void runMergeSortExampleProducesSortedSequence() throws Exception {
        String source = Files.readString(Path.of("example", "歸併排序.wy"), StandardCharsets.UTF_8);
        WenyanEngine.Result result = new WenyanEngine().execute(source);
        assertEquals("[1, 2, 3, 14, 18]" + System.lineSeparator(), result.output());
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
        assertEquals("2" + nl + "4" + nl, result.output());
    }
}

