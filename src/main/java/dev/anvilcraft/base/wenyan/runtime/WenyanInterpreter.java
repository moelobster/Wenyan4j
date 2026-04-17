package dev.anvilcraft.base.wenyan.runtime;

import dev.anvilcraft.base.wenyan.parser.wenyanBaseVisitor;
import dev.anvilcraft.base.wenyan.parser.wenyanParser;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class WenyanInterpreter extends wenyanBaseVisitor<WenyanValue> {
    private final CommonTokenStream tokens;
    private final StringBuilder output;

    private Environment env = new Environment(null);
    private WenyanValue current = WenyanValue.NULL;
    private List<WenyanValue> pending = new ArrayList<>();

    public WenyanInterpreter(CommonTokenStream tokens, StringBuilder output) {
        this.tokens = tokens;
        this.output = output;
    }

    @Override
    public WenyanValue visitProgram(wenyanParser.ProgramContext ctx) {
        for (wenyanParser.StatementContext statement : ctx.statement()) {
            visit(statement);
        }
        return current;
    }

    @Override
    public WenyanValue visitStatement(wenyanParser.StatementContext ctx) {
        if (ctx.BREAK() != null) {
            throw new BreakSignal();
        }
        return super.visitStatement(ctx);
    }

    @Override
    public WenyanValue visitComment(wenyanParser.CommentContext ctx) {
        return WenyanValue.NULL;
    }

    @Override
    public WenyanValue visitFlush_statement(wenyanParser.Flush_statementContext ctx) {
        pending.clear();
        current = WenyanValue.NULL;
        return current;
    }

    @Override
    public WenyanValue visitDeclare_statement(wenyanParser.Declare_statementContext ctx) {
        int count = WenyanNumber.parse(ctx.INT_NUM().getText()).intValueExact();
        List<WenyanValue> values = new ArrayList<>();
        List<wenyanParser.DataContext> declared = ctx.data();
        for (int i = 0; i < count; i++) {
            WenyanValue value;
            if (i < declared.size()) {
                value = evalData(declared.get(i));
            } else {
                value = defaultValue(ctx.TYPE().getText());
            }
            values.add(value);
        }
        setPending(values);
        return current;
    }

    @Override
    public WenyanValue visitDefine_statement(wenyanParser.Define_statementContext ctx) {
        if (ctx.init_define_statement() != null) {
            WenyanValue value = visitInit_define_statement(ctx.init_define_statement());
            return value;
        }
        visitDeclare_statement(ctx.declare_statement());
        applyNameMulti(ctx.name_multi_statement());
        return current;
    }

    @Override
    public WenyanValue visitInit_define_statement(wenyanParser.Init_define_statementContext ctx) {
        WenyanValue value = evalData(ctx.data());
        setPending(List.of(value));
        if (ctx.name_single_statement() != null) {
            applyNameSingle(ctx.name_single_statement());
        }
        return value;
    }

    @Override
    public WenyanValue visitMath_statement(wenyanParser.Math_statementContext ctx) {
        WenyanValue result;
        if (ctx.arith_math_statement() != null) {
            result = visitArith_math_statement(ctx.arith_math_statement());
        } else if (ctx.mod_math_statement() != null) {
            result = visitMod_math_statement(ctx.mod_math_statement());
        } else {
            result = visitBoolean_algebra_statement(ctx.boolean_algebra_statement());
        }
        if (ctx.name_multi_statement() != null) {
            applyNameMulti(ctx.name_multi_statement());
        }
        return result;
    }

    @Override
    public WenyanValue visitArith_binary_math(wenyanParser.Arith_binary_mathContext ctx) {
        WenyanValue left = evalDataOrCurrent(ctx.getChild(1).getText());
        WenyanValue right = evalDataOrCurrent(ctx.getChild(3).getText());
        BigDecimal l = left.asNumber();
        BigDecimal r = right.asNumber();
        String op = ctx.ARITH_BINARY_OP().getText();
        BigDecimal result = switch (op) {
            case "加" -> l.add(r);
            case "減" -> l.subtract(r);
            case "乘" -> l.multiply(r);
            default -> throw new IllegalStateException("Unsupported op: " + op);
        };
        WenyanValue value = WenyanValue.number(result);
        setPending(List.of(value));
        return value;
    }

    @Override
    public WenyanValue visitArith_unary_math(wenyanParser.Arith_unary_mathContext ctx) {
        WenyanValue value = evalDataOrCurrent(ctx.getChild(1).getText());
        WenyanValue result = WenyanValue.number(value.asNumber().negate());
        setPending(List.of(result));
        return result;
    }

    @Override
    public WenyanValue visitMod_math_statement(wenyanParser.Mod_math_statementContext ctx) {
        WenyanValue left = evalDataOrCurrent(ctx.getChild(1).getText());
        WenyanValue right = evalDataOrCurrent(ctx.getChild(3).getText());
        BigDecimal l = left.asNumber();
        BigDecimal r = right.asNumber();
        BigDecimal result;
        if (ctx.POST_MOD_MATH_OP() != null) {
            result = l.remainder(r);
        } else {
            result = l.divide(r, 20, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        WenyanValue value = WenyanValue.number(result);
        setPending(List.of(value));
        return value;
    }

    @Override
    public WenyanValue visitBoolean_algebra_statement(wenyanParser.Boolean_algebra_statementContext ctx) {
        WenyanValue left = env.get(stripIdentifier(ctx.IDENTIFIER(0).getText()));
        WenyanValue right = env.get(stripIdentifier(ctx.IDENTIFIER(1).getText()));
        boolean result = switch (ctx.LOGIC_BINARY_OP().getText()) {
            case "中有陽乎" -> left.asArray().stream().anyMatch(WenyanValue::asBoolean)
                    || right.asArray().stream().anyMatch(WenyanValue::asBoolean);
            case "中無陰乎" -> left.asArray().stream().noneMatch(v -> !v.asBoolean())
                    && right.asArray().stream().noneMatch(v -> !v.asBoolean());
            default -> throw new IllegalStateException("Unsupported boolean op");
        };
        WenyanValue value = WenyanValue.bool(result);
        setPending(List.of(value));
        return value;
    }

    @Override
    public WenyanValue visitIf_statement(wenyanParser.If_statementContext ctx) {
        boolean cond = evalIfExpression(ctx.if_expression());
        int elseTokenIndex = ctx.ELSE() == null ? Integer.MAX_VALUE : ctx.ELSE().getSymbol().getTokenIndex();
        List<wenyanParser.StatementContext> thenBranch = new ArrayList<>();
        List<wenyanParser.StatementContext> elseBranch = new ArrayList<>();
        for (wenyanParser.StatementContext st : ctx.statement()) {
            if (st.getStart().getTokenIndex() < elseTokenIndex) {
                thenBranch.add(st);
            } else {
                elseBranch.add(st);
            }
        }

        if (cond) {
            executeStatements(thenBranch);
        } else {
            executeStatements(elseBranch);
        }
        return current;
    }

    @Override
    public WenyanValue visitFor_enum_statement(wenyanParser.For_enum_statementContext ctx) {
        String text = ctx.getChild(1).getText();
        int times = evalDataOrCurrent(text).asNumber().intValue();
        for (int i = 0; i < times; i++) {
            try {
                executeStatements(ctx.statement());
            } catch (BreakSignal ignored) {
                break;
            }
        }
        return current;
    }

    @Override
    public WenyanValue visitFor_while_statement(wenyanParser.For_while_statementContext ctx) {
        while (true) {
            try {
                executeStatements(ctx.statement());
            } catch (BreakSignal ignored) {
                break;
            }
        }
        return current;
    }

    @Override
    public WenyanValue visitFor_arr_statement(wenyanParser.For_arr_statementContext ctx) {
        String src = stripIdentifier(ctx.IDENTIFIER(0).getText());
        String target = stripIdentifier(ctx.IDENTIFIER(1).getText());
        List<WenyanValue> arr = env.get(src).asArray();
        for (WenyanValue value : arr) {
            env.assign(target, value);
            try {
                executeStatements(ctx.statement());
            } catch (BreakSignal ignored) {
                break;
            }
        }
        return current;
    }

    @Override
    public WenyanValue visitArray_push_statement(wenyanParser.Array_push_statementContext ctx) {
        WenyanValue arrayValue = evalDataOrCurrent(ctx.getChild(1).getText());
        List<WenyanValue> arr = arrayValue.asArray();
        for (int i = 0; i < ctx.data().size(); i++) {
            arr.add(evalData(ctx.data(i)));
        }
        setPending(List.of(arrayValue));
        if (ctx.name_single_statement() != null) {
            applyNameSingle(ctx.name_single_statement());
        }
        return arrayValue;
    }

    @Override
    public WenyanValue visitArray_cat_statement(wenyanParser.Array_cat_statementContext ctx) {
        WenyanValue base = evalDataOrCurrent(ctx.getChild(1).getText());
        List<WenyanValue> merged = new ArrayList<>(base.asArray());
        List<TerminalNode> ids = ctx.IDENTIFIER();
        for (int i = 1; i < ids.size(); i++) {
            merged.addAll(env.get(stripIdentifier(ids.get(i).getText())).asArray());
        }
        WenyanValue result = WenyanValue.array(merged);
        setPending(List.of(result));
        if (ctx.name_single_statement() != null) {
            applyNameSingle(ctx.name_single_statement());
        }
        return result;
    }

    @Override
    public WenyanValue visitReference_statement(wenyanParser.Reference_statementContext ctx) {
        WenyanValue base = evalData(ctx.data());
        WenyanValue result = base;
        String selector = findSelectorAfterZhi(ctx);
        if (selector != null) {
            result = resolveSelector(base, selector);
        }
        setPending(List.of(result));
        if (ctx.name_single_statement() != null) {
            applyNameSingle(ctx.name_single_statement());
        }
        return result;
    }

    @Override
    public WenyanValue visitAssign_statement(wenyanParser.Assign_statementContext ctx) {
        String name = stripIdentifier(ctx.IDENTIFIER(0).getText());
        WenyanValue target = env.get(name);

        WenyanValue value;
        if (ctx.getText().contains("今不復存矣")) {
            env.delete(name);
            setPending(List.of(WenyanValue.NULL));
            return WenyanValue.NULL;
        }

        if (ctx.getText().contains("今其是矣")) {
            value = current;
        } else {
            value = ctx.data() == null ? current : evalData(ctx.data());
        }

        if (ctx.IDENTIFIER().size() >= 2 || !ctx.INT_NUM().isEmpty() || ctx.STRING_LITERAL() != null) {
            String indexText;
            if (ctx.IDENTIFIER().size() >= 2) {
                indexText = ctx.IDENTIFIER(1).getText();
            } else if (!ctx.INT_NUM().isEmpty()) {
                indexText = ctx.INT_NUM(0).getText();
            } else {
                indexText = ctx.STRING_LITERAL().getText();
            }
            int index = evalDataOrCurrent(indexText).asNumber().intValue() - 1;
            List<WenyanValue> arr = target.asArray();
            while (index >= arr.size()) {
                arr.add(WenyanValue.NULL);
            }
            arr.set(index, value);
        } else {
            env.assign(name, value);
        }
        setPending(List.of(value));
        return value;
    }

    @Override
    public WenyanValue visitFunction_define_statement(wenyanParser.Function_define_statementContext ctx) {
        String functionName = stripIdentifier(ctx.name_single_statement().IDENTIFIER().getText());
        List<String> params = new ArrayList<>();
        if (ctx.getText().contains("必先得")) {
            boolean inParamSection = false;
            for (Token token : tokens.getTokens(ctx.getStart().getTokenIndex(), ctx.getStop().getTokenIndex())) {
                if ("必先得".equals(token.getText())) {
                    inParamSection = true;
                    continue;
                }
                if ("是術曰".equals(token.getText()) || "乃行是術曰".equals(token.getText())) {
                    break;
                }
                if (!inParamSection) {
                    continue;
                }
                if ("曰".equals(token.getText())) {
                    Token next = tokens.get(token.getTokenIndex() + 1);
                    if (next.getText().startsWith("「") && !next.getText().startsWith("「「")) {
                        params.add(stripIdentifier(next.getText()));
                    }
                }
            }
        }

        WenyanFunction function = new WenyanFunction(params, ctx.statement(), env);
        env.assign(functionName, WenyanValue.function(function));
        setPending(List.of(WenyanValue.function(function)));
        return current;
    }

    @Override
    public WenyanValue visitFunction_pre_call(wenyanParser.Function_pre_callContext ctx) {
        WenyanValue functionValue;
        if (ctx.IDENTIFIER() != null) {
            functionValue = env.get(stripIdentifier(ctx.IDENTIFIER().getText()));
        } else {
            functionValue = current;
        }

        List<WenyanValue> args = new ArrayList<>();
        for (wenyanParser.DataContext data : ctx.data()) {
            args.add(evalData(data));
        }

        WenyanValue result = callFunction(functionValue.asFunction(), args);
        setPending(List.of(result));
        return result;
    }

    @Override
    public WenyanValue visitFunction_statement(wenyanParser.Function_statementContext ctx) {
        WenyanValue value = super.visitFunction_statement(ctx);
        if (ctx.name_single_statement() != null) {
            applyNameSingle(ctx.name_single_statement());
        }
        return value;
    }

    @Override
    public WenyanValue visitReturn_statement(wenyanParser.Return_statementContext ctx) {
        WenyanValue value;
        if (ctx.data() != null) {
            value = evalData(ctx.data());
        } else if (ctx.getText().contains("其")) {
            value = current;
        } else {
            value = WenyanValue.NULL;
        }
        throw new ReturnSignal(value);
    }

    @Override
    public WenyanValue visitPrint_statement(wenyanParser.Print_statementContext ctx) {
        if (pending.isEmpty()) {
            output.append(current.toDisplayString()).append(System.lineSeparator());
            return current;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (i > 0) {
                output.append(" ");
            }
            output.append(pending.get(i).toDisplayString());
        }
        output.append(System.lineSeparator());
        return current;
    }

    @Override
    public WenyanValue visitImport_statement(wenyanParser.Import_statementContext ctx) {
        return WenyanValue.NULL;
    }

    @Override
    public WenyanValue visitObject_statement(wenyanParser.Object_statementContext ctx) {
        return WenyanValue.NULL;
    }

    public String output() {
        return output.toString();
    }

    private WenyanValue callFunction(WenyanFunction function, List<WenyanValue> args) {
        Environment previous = env;
        env = new Environment(function.closure());
        try {
            for (int i = 0; i < function.parameterNames().size(); i++) {
                WenyanValue arg = i < args.size() ? args.get(i) : WenyanValue.NULL;
                env.define(function.parameterNames().get(i), arg);
            }
            executeStatements(function.body());
        } catch (ReturnSignal signal) {
            return signal.value();
        } finally {
            env = previous;
        }
        return WenyanValue.NULL;
    }

    private boolean evalIfExpression(wenyanParser.If_expressionContext ctx) {
        if (ctx.binary_if_expression() == null) {
            return evalUnaryIfExpression(ctx.unary_if_expression()).asBoolean();
        }
        WenyanValue left = evalUnaryIfExpression(ctx.binary_if_expression().unary_if_expression(0));
        WenyanValue right = evalUnaryIfExpression(ctx.binary_if_expression().unary_if_expression(1));
        int compare = compareValues(left, right);
        return switch (ctx.binary_if_expression().IF_LOGIC_OP().getText()) {
            case "等於" -> compare == 0;
            case "不等於" -> compare != 0;
            case "不大於" -> compare <= 0;
            case "不小於" -> compare >= 0;
            case "大於" -> compare > 0;
            case "小於" -> compare < 0;
            default -> throw new IllegalStateException("Unknown if op");
        };
    }

    private WenyanValue evalUnaryIfExpression(wenyanParser.Unary_if_expressionContext ctx) {
        if (ctx.data() != null) {
            return evalData(ctx.data());
        }
        if (ctx.getText().equals("其")) {
            return current;
        }
        String baseName = stripIdentifier(ctx.IDENTIFIER(0).getText());
        WenyanValue base = env.get(baseName);
        String selector = ctx.getChild(2).getText();
        return resolveSelector(base, selector);
    }

    private WenyanValue resolveSelector(WenyanValue base, String selector) {
        if ("長".equals(selector)) {
            if (base.type() == WenyanValue.Type.ARRAY) {
                return WenyanValue.number(BigDecimal.valueOf(base.asArray().size()));
            }
            if (base.type() == WenyanValue.Type.STRING) {
                return WenyanValue.number(BigDecimal.valueOf(base.asText().length()));
            }
            throw new IllegalStateException("Length unsupported for type: " + base.type());
        }

        WenyanValue indexValue = evalDataOrCurrent(selector);
        int index = indexValue.asNumber().intValue() - 1;
        if (base.type() == WenyanValue.Type.ARRAY) {
            if (index < 0 || index >= base.asArray().size()) {
                return WenyanValue.NULL;
            }
            return base.asArray().get(index);
        }
        if (base.type() == WenyanValue.Type.STRING) {
            String text = base.asText();
            if (index < 0 || index >= text.length()) {
                return WenyanValue.text("");
            }
            return WenyanValue.text(String.valueOf(text.charAt(index)));
        }
        throw new IllegalStateException("Index access unsupported for type: " + base.type());
    }

    private int compareValues(WenyanValue left, WenyanValue right) {
        if (left.type() == WenyanValue.Type.NUMBER || right.type() == WenyanValue.Type.NUMBER) {
            return left.asNumber().compareTo(right.asNumber());
        }
        return left.toDisplayString().compareTo(right.toDisplayString());
    }

    private void executeStatements(List<wenyanParser.StatementContext> statements) {
        for (wenyanParser.StatementContext statement : statements) {
            visit(statement);
        }
    }

    private WenyanValue evalData(wenyanParser.DataContext ctx) {
        if (ctx.INT_NUM() != null) {
            return WenyanValue.number(WenyanNumber.parse(ctx.INT_NUM().getText()));
        }
        if (ctx.FLOAT_NUM() != null) {
            return WenyanValue.number(WenyanNumber.parse(ctx.FLOAT_NUM().getText()));
        }
        if (ctx.STRING_LITERAL() != null) {
            return WenyanValue.text(stripStringLiteral(ctx.STRING_LITERAL().getText()));
        }
        if (ctx.BOOL_VALUE() != null) {
            return WenyanValue.bool("陽".equals(ctx.BOOL_VALUE().getText()));
        }
        String name = stripIdentifier(ctx.IDENTIFIER().getText());
        return env.get(name);
    }

    private WenyanValue evalDataOrCurrent(String text) {
        if ("其".equals(text)) {
            return current;
        }
        if (text.startsWith("「「") && text.endsWith("」」")) {
            return WenyanValue.text(stripStringLiteral(text));
        }
        if (text.startsWith("「") && text.endsWith("」")) {
            return env.get(stripIdentifier(text));
        }
        if ("陽".equals(text) || "陰".equals(text)) {
            return WenyanValue.bool("陽".equals(text));
        }
        return WenyanValue.number(WenyanNumber.parse(text));
    }

    private void setPending(List<WenyanValue> values) {
        pending = new ArrayList<>(values);
        current = pending.isEmpty() ? WenyanValue.NULL : pending.get(pending.size() - 1);
    }

    private WenyanValue defaultValue(String typeText) {
        return switch (typeText) {
            case "數" -> WenyanValue.number(BigDecimal.ZERO);
            case "列" -> WenyanValue.array(new ArrayList<>());
            case "言" -> WenyanValue.text("");
            case "爻" -> WenyanValue.bool(false);
            default -> WenyanValue.NULL;
        };
    }

    private void applyNameSingle(wenyanParser.Name_single_statementContext ctx) {
        if (pending.isEmpty()) {
            return;
        }
        String name = stripIdentifier(ctx.IDENTIFIER().getText());
        env.assign(name, pending.get(0).copyIfNeeded());
    }

    private void applyNameMulti(wenyanParser.Name_multi_statementContext ctx) {
        List<TerminalNode> ids = ctx.IDENTIFIER();
        for (int i = 0; i < ids.size() && i < pending.size(); i++) {
            env.assign(stripIdentifier(ids.get(i).getText()), pending.get(i).copyIfNeeded());
        }
    }

    private String stripIdentifier(String text) {
        if (text.startsWith("「") && text.endsWith("」")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String stripStringLiteral(String text) {
        if (text.startsWith("「「") && text.endsWith("」」")) {
            return text.substring(2, text.length() - 2);
        }
        return text;
    }

    private String findSelectorAfterZhi(wenyanParser.Reference_statementContext ctx) {
        for (int i = 0; i < ctx.getChildCount() - 1; i++) {
            if ("之".equals(ctx.getChild(i).getText())) {
                return ctx.getChild(i + 1).getText();
            }
        }
        return null;
    }
}

