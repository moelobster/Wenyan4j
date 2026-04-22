package dev.anvilcraft.base.wenyan;

import dev.anvilcraft.base.wenyan.parser.wenyanLexer;
import dev.anvilcraft.base.wenyan.parser.wenyanParser;
import dev.anvilcraft.base.wenyan.runtime.WenyanInterpreter;
import dev.anvilcraft.base.wenyan.runtime.WenyanValue;
import dev.anvilcraft.base.wenyan.runtime.WenyuanRegistry;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * 文言运行时的高层入口，负责解析并执行脚本。
 */
public final class WenyanEngine {
    private final WenyuanRegistry registry;

    /**
     * 创建带有内置扩展注册信息的引擎实例。
     */
    public WenyanEngine() {
        registry = WenyuanRegistry.withDefaults();
    }

    /**
     * 创建带有内置扩展注册信息的引擎实例。
     */
    public WenyanEngine(ClassLoader classLoader) {
        registry = WenyuanRegistry.withDefaults(classLoader);
    }

    /**
     * 注册指定 Java 包中所有带注解的文渊扩展类。
     *
     * @param packageName 要扫描的 Java 包名
     * @return 当前引擎实例（便于链式调用）
     */
    public WenyanEngine registerWenyuanPackage(String packageName) {
        registry.registerPackage(packageName);
        return this;
    }

    /**
     * 注册单个扩展类（需带有文渊阁/函数注解）。
     *
     * @param clazz 扩展类
     * @return 当前引擎实例（便于链式调用）
     */
    public WenyanEngine registerWenyuanClass(Class<?> clazz) {
        registry.registerClass(clazz);
        return this;
    }

    /**
     * 解析并执行一段文言脚本。
     *
     * @param source 脚本文本
     * @return 执行结果，包含输出文本与最终值
     */
    public Result execute(String source) {
        String preprocessed = ScriptPreprocessor.preprocess(source);
        wenyanLexer lexer = new wenyanLexer(CharStreams.fromString(preprocessed));
        wenyanParser parser = new wenyanParser(new CommonTokenStream(lexer));

        ThrowingErrorListener errorListener = new ThrowingErrorListener();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);

        CommonTokenStream tokens = (CommonTokenStream) parser.getInputStream();
        tokens.fill();

        StringBuilder output = new StringBuilder();
        WenyanInterpreter interpreter = new WenyanInterpreter(tokens, output, registry.snapshot());
        WenyanValue last = interpreter.visitProgram(parser.program());
        return new Result(output.toString(), last);
    }

    /**
     * 不可变的执行结果。
     *
     * @param output    由 {@code 書之} 产生的文本输出
     * @param lastValue 脚本执行结束后的最后一个运行时值
     */
    public record Result(String output, WenyanValue lastValue) {
    }

    private static final class ThrowingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e
        ) {
            throw new IllegalStateException("Parse error at " + line + ":" + charPositionInLine + " - " + msg, e);
        }
    }
}

