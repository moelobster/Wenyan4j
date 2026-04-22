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

public final class WenyanEngine {
    private final WenyuanRegistry registry = WenyuanRegistry.withDefaults();

    public WenyanEngine registerWenyuanPackage(String packageName) {
        registry.registerPackage(packageName);
        return this;
    }

    public WenyanEngine registerWenyuanClass(Class<?> clazz) {
        registry.registerClass(clazz);
        return this;
    }

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

    public record Result(String output, WenyanValue lastValue) {
    }

    private static final class ThrowingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            throw new IllegalStateException("Parse error at " + line + ":" + charPositionInLine + " - " + msg, e);
        }
    }
}

