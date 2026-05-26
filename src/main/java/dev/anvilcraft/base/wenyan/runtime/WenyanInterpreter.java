package dev.anvilcraft.base.wenyan.runtime;

import dev.anvilcraft.base.wenyan.annotation.WenyuanField;
import dev.anvilcraft.base.wenyan.parser.wenyanBaseVisitor;
import dev.anvilcraft.base.wenyan.parser.wenyanParser;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * 执行文言语法树的访问器。
 */
public final class WenyanInterpreter extends wenyanBaseVisitor<WenyanValue> {
    private final CommonTokenStream tokens;
    private final StringBuilder output;
    private final WenyuanRegistry registry;

    private Environment env = new Environment(null);
    private WenyanValue current = WenyanValue.NULL;
    private List<WenyanValue> pending = new ArrayList<>();
    private boolean inConstructor = false;
    private WenyanClass currentDispatchClass = null;

    /**
     * 创建解释器实例并绑定解析输入与输出缓冲区。
     *
     * @param tokens   解析器 token 流
     * @param output   {@code 書之} 输出缓冲区
     * @param registry 文渊阁函数注册表快照
     */
    public WenyanInterpreter(CommonTokenStream tokens, StringBuilder output, WenyuanRegistry registry) {
        this.tokens = tokens;
        this.output = output;
        this.registry = Objects.requireNonNull(registry, "registry");
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
            return visitInit_define_statement(ctx.init_define_statement());
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
            case "中無陰乎" -> left.asArray().stream().allMatch(WenyanValue::asBoolean)
                               && right.asArray().stream().allMatch(WenyanValue::asBoolean);
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
        List<WenyanValue> arr = new ArrayList<>(env.get(src).asArray());
        for (WenyanValue value : arr) {
            env.define(target, value);
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
        String refText = ctx.reference_base().getText();
        WenyanValue base;
        if (ctx.reference_base().data() != null) {
            base = evalData(ctx.reference_base().data());
        } else if ("其".equals(refText)) {
            base = current;
        } else if ("己".equals(refText) || "父".equals(refText)) {
            WenyanObject self = env.get("己").asInstance();
            String selector = findSelectorAfterZhi(ctx);
            if (selector == null) {
                throw new IllegalStateException("不可直接引用" + ("父".equals(refText) ? "父" : "己") + "实例");
            }
            String fieldName = normalizeSelectorName(selector);
            if ("父".equals(refText)) {
                WenyanClass parentClass = self.getWenyanClass().parentClass();
                if (parentClass == null) {
                    throw new IllegalStateException("无父类可访问");
                }
                WenyanClass.PropertyDef prop = parentClass.lookupProperty(fieldName);
                if (prop == null || prop.visibility() == WenyanClass.Visibility.PRIVATE) {
                    throw new IllegalStateException("父类无可见属性「" + fieldName + "」");
                }
                WenyanValue result = self.fields().getOrDefault(fieldName, WenyanValue.NULL);
                setPending(List.of(result));
                if (ctx.name_single_statement() != null) {
                    applyNameSingle(ctx.name_single_statement());
                }
                return result;
            }
            WenyanValue result = self.getField(fieldName, self);
            setPending(List.of(result));
            if (ctx.name_single_statement() != null) {
                applyNameSingle(ctx.name_single_statement());
            }
            return result;
        } else {
            base = WenyanValue.NULL;
        }
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
        String targetText = ctx.assign_target().getText();
        String statementText = ctx.getText();

        WenyanValue value;
        if (statementText.contains("今不復存矣")) {
            if ("己".equals(targetText) || "父".equals(targetText)) {
                throw new IllegalStateException("不可删除己/父属性");
            }
            String name = stripIdentifier(ctx.assign_target().IDENTIFIER().getText());
            env.delete(name);
            setPending(List.of(WenyanValue.NULL));
            return WenyanValue.NULL;
        }

        String rightSegment = extractBetween(statementText, "今", "是矣");
        if (rightSegment.startsWith("其")) {
            value = current;
            String rightSelector = extractSelector(rightSegment);
            if (rightSelector != null) {
                value = resolveSelector(value, rightSelector);
            }
        } else {
            value = ctx.data() == null ? current : evalData(ctx.data());
            String rightSelector = extractSelector(rightSegment);
            if (rightSelector != null) {
                value = resolveSelector(value, rightSelector);
            }
        }

        String leftSegment = extractBetween(statementText, "昔之", "者");
        String leftSelector = extractSelector(leftSegment);

        if ("己".equals(targetText)) {
            WenyanObject self = env.get("己").asInstance();
            if (leftSelector != null) {
                self.setField(normalizeSelectorName(leftSelector), value, self);
            } else {
                throw new IllegalStateException("不可直接对己赋值");
            }
            setPending(List.of(value));
            return value;
        }

        if ("父".equals(targetText)) {
            throw new IllegalStateException("不可对父属性赋值");
        }

        String name = stripIdentifier(ctx.assign_target().IDENTIFIER().getText());
        WenyanValue target = env.get(name);

        if (leftSelector != null) {
            assignBySelector(target, leftSelector, value);
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

        boolean async = containsAwaitInStatements(ctx.statement());
        WenyanFunction function = new WenyanFunction(params, ctx.statement(), env, async);
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

        WenyanValue result = invokeCallable(functionValue, args, false);
        setPending(List.of(result));
        return result;
    }

    @Override
    public WenyanValue visitWait_statement(wenyanParser.Wait_statementContext ctx) {
        String head = ctx.getChild(0).getText();

        // Handle: 待之以 data [TIME_UNIT] [wait_crash_branch]
        if ("待之以".equals(head)) {
            if (ctx.TIME_UNIT() != null) {
                // Sleep with time unit
                BigDecimal seconds = evalData(ctx.data(0)).asNumber();
                String timeUnit = ctx.TIME_UNIT().getText();
                BigDecimal timeUnitWaitTime = switch (timeUnit) {
                    case "分" -> BigDecimal.valueOf(60000L);
                    case "時" -> BigDecimal.valueOf(3600000L);
                    case "日" -> BigDecimal.valueOf(86400000L);
                    case "月" -> BigDecimal.valueOf(2592000000L);
                    case "年" -> BigDecimal.valueOf(31536000000L);
                    default -> BigDecimal.valueOf(1000L);
                };
                long millis = seconds.multiply(timeUnitWaitTime).longValue();
                if (millis > 0) {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Wait interrupted", e);
                    }
                }
                WenyanValue result = WenyanValue.NULL;
                setPending(List.of(result));
                if (ctx.name_single_statement() != null) {
                    applyNameSingle(ctx.name_single_statement());
                }
                return result;
            }

            // Await promise with crash branch support
            WenyanValue awaited = evalData(ctx.data(0));
            if (awaited.type() != WenyanValue.Type.PROMISE) {
                throw new IllegalStateException("待之以 without time unit requires a promise");
            }
            WenyanPromise promise = awaited.asPromise();
            WenyanValue result = promise.await();

            // Handle promise rejection with crash branch
            if (promise.isRejected()) {
                if (ctx.wait_crash_branch() != null) {
                    // Set error variable named 「錯誤」
                    env.define("錯誤", result);
                    current = result;
                    setPending(List.of(result));
                    executeStatements(ctx.wait_crash_branch().statement());
                    return current;
                }
                throw new IllegalStateException("Promise rejected: " + result.toDisplayString());
            }

            setPending(List.of(result));
            if (ctx.name_single_statement() != null) {
                applyNameSingle(ctx.name_single_statement());
            }
            return result;
        }

        // Handle: 待施 IDENTIFIER [preposition data]* [wait_crash_branch]
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

        // Call function and await if promise
        WenyanValue invoked = invokeCallable(functionValue, args, false);
        if (invoked.type() != WenyanValue.Type.PROMISE) {
            // Sync function, no crash branch possible
            setPending(List.of(invoked));
            if (ctx.name_single_statement() != null) {
                applyNameSingle(ctx.name_single_statement());
            }
            return invoked;
        }

        // Await the promise
        WenyanPromise promise = invoked.asPromise();
        WenyanValue result = promise.await();

        // Handle rejection with crash branch
        if (promise.isRejected()) {
            if (ctx.wait_crash_branch() != null) {
                env.define("錯誤", result);
                current = result;
                setPending(List.of(result));
                executeStatements(ctx.wait_crash_branch().statement());
                return current;
            }
            throw new IllegalStateException("Promise rejected: " + result.toDisplayString());
        }

        setPending(List.of(result));
        if (ctx.name_single_statement() != null) {
            applyNameSingle(ctx.name_single_statement());
        }
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
        String text = ctx.getText();

        // Handle rejection: 即拒
        if (text.startsWith("即拒")) {
            WenyanValue reason;
            if (ctx.data() != null) {
                reason = evalData(ctx.data());
            } else if (text.contains("其")) {
                reason = current;
            } else {
                reason = WenyanValue.NULL;
            }
            throw new RejectionSignal(reason);
        }

        // Handle return: 乃得 or 乃歸空無 or 乃得矣
        WenyanValue value;
        if (ctx.data() != null) {
            value = evalData(ctx.data());
        } else if (text.contains("其")) {
            value = current;
        } else {
            value = WenyanValue.NULL;
        }
        throw new ReturnSignal(value);
    }

    @Override
    public WenyanValue visitPrint_statement(wenyanParser.Print_statementContext ctx) {
        synchronized (output) {
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
        }
        return current;
    }

    @Override
    public WenyanValue visitImport_statement(wenyanParser.Import_statementContext ctx) {
        String pavilionName = stripStringLiteral(ctx.STRING_LITERAL().getText());
        Map<String, Method> pavilionMethods = registry.methodsFor(pavilionName);
        if (pavilionMethods.isEmpty()) {
            throw new IllegalStateException("Unknown pavilion: " + pavilionName);
        }

        Set<String> requested = ctx.IDENTIFIER().stream()
            .map(TerminalNode::getText)
            .map(this::stripIdentifier)
            .collect(Collectors.toSet());

        for (Map.Entry<String, Method> entry : pavilionMethods.entrySet()) {
            String importedName = entry.getKey();
            if (!requested.isEmpty() && !requested.contains(importedName)) {
                continue;
            }
            Method method = entry.getValue();
            env.assign(importedName, WenyanValue.nativeFunction(args -> invokeNativeMethod(method, args)));
        }
        return WenyanValue.NULL;
    }

    @Override
    public WenyanValue visitObject_statement(wenyanParser.Object_statementContext ctx) {
        return WenyanValue.NULL;
    }

    // ==================== OOP 访问者 ====================

    @Override
    public WenyanValue visitClass_statement(wenyanParser.Class_statementContext ctx) {
        return super.visitClass_statement(ctx);
    }

    @Override
    public WenyanValue visitClass_define(wenyanParser.Class_defineContext ctx) {
        String modifier = ctx.CLASS_MODIFIER().getText();
        boolean isAbstract = "虚族".equals(modifier);
        boolean isFinal = "终族".equals(modifier);

        List<String> allIds = ctx.IDENTIFIER().stream()
            .map(t -> stripIdentifier(t.getText()))
            .toList();
        String className = allIds.get(0);

        // 解析承/守
        String parentClassName = null;
        List<String> interfaceNames = new ArrayList<>();
        boolean inExtends = false, inImplements = false;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            String text = ctx.getChild(i).getText();
            if ("承".equals(text)) { inExtends = true; continue; }
            if ("守".equals(text)) { inExtends = false; inImplements = true; continue; }
            if ("其族如是".equals(text)) break;
            if (inExtends && text.startsWith("「") && text.endsWith("」")) {
                parentClassName = stripIdentifier(text);
                inExtends = false;
            }
            if (inImplements && text.startsWith("「") && text.endsWith("」")) {
                interfaceNames.add(stripIdentifier(text));
            }
        }

        WenyanClass clazz = new WenyanClass(className, isAbstract, isFinal);

        if (parentClassName != null) {
            WenyanValue parentVal = env.get(parentClassName);
            if (parentVal.type() == WenyanValue.Type.CLASS) {
                clazz.setParentClass(parentVal.asClass());
            }
        }

        for (String ifaceName : interfaceNames) {
            WenyanInterface iface = registeredInterfaces.get(ifaceName);
            if (iface == null) {
                throw new IllegalStateException("「" + ifaceName + "」不是约或未定义");
            }
            clazz.addInterface(iface);
        }

        // 处理类成员
        for (wenyanParser.Class_memberContext member : ctx.class_member()) {
            if (member.property_define() != null) {
                processPropertyDefine(member.property_define(), clazz);
            } else if (member.method_define() != null) {
                processMethodDefine(member.method_define(), clazz, env);
            } else if (member.constructor_define() != null) {
                processConstructorDefine(member.constructor_define(), clazz, env);
            }
        }

        clazz.validate();
        env.define(className, WenyanValue.clazz(clazz));
        return WenyanValue.NULL;
    }

    private final Map<String, WenyanInterface> registeredInterfaces = new LinkedHashMap<>();

    private void processPropertyDefine(wenyanParser.Property_defineContext ctx, WenyanClass clazz) {
        String prefix = ctx.property_prefix().getText();
        WenyanClass.Visibility vis = WenyanClass.Visibility.PUBLIC;
        boolean readonly = prefix.contains("恆性");
        if (prefix.startsWith("私之")) vis = WenyanClass.Visibility.PRIVATE;
        else if (prefix.startsWith("密之")) vis = WenyanClass.Visibility.PROTECTED;

        String propName = stripIdentifier(ctx.IDENTIFIER().getText());
        String type = ctx.TYPE().getText();
        WenyanValue defaultValue = ctx.data() != null ? evalData(ctx.data()) : null;
        clazz.addProperty(new WenyanClass.PropertyDef(propName, vis, readonly, type, defaultValue));
    }

    private void processMethodDefine(wenyanParser.Method_defineContext ctx, WenyanClass clazz, Environment closure) {
        String prefix = ctx.method_prefix().getText();
        WenyanClass.Visibility vis = prefix.contains("私術") ? WenyanClass.Visibility.PRIVATE
            : prefix.contains("密術") ? WenyanClass.Visibility.PROTECTED
            : WenyanClass.Visibility.PUBLIC;
        boolean isAbstract = prefix.contains("虚術");

        String methodName = stripIdentifier(ctx.IDENTIFIER(0).getText());

        List<String> paramNames = new ArrayList<>();
        if (ctx.method_params() != null) {
            for (int i = 0; i < ctx.method_params().getChildCount(); i++) {
                if ("曰".equals(ctx.method_params().getChild(i).getText())) {
                    String next = ctx.method_params().getChild(i + 1).getText();
                    if (next.startsWith("「") && !next.startsWith("「「")) {
                        paramNames.add(stripIdentifier(next));
                    }
                }
            }
        }

        List<wenyanParser.StatementContext> body = new ArrayList<>();
        if (!isAbstract && ctx.statement() != null) {
            body = ctx.statement();
        }

        clazz.addMethod(new WenyanClass.MethodDef(methodName, vis, isAbstract, paramNames, body, closure));
    }

    private void processConstructorDefine(wenyanParser.Constructor_defineContext ctx, WenyanClass clazz, Environment closure) {
        List<String> paramNames = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if ("曰".equals(ctx.getChild(i).getText())) {
                String next = ctx.getChild(i + 1).getText();
                if (next.startsWith("「") && !next.startsWith("「「")) {
                    paramNames.add(stripIdentifier(next));
                }
            }
        }
        clazz.setConstructor(new WenyanClass.ConstructorDef(paramNames, ctx.statement(), closure));
    }

    @Override
    public WenyanValue visitClass_instantiate(wenyanParser.Class_instantiateContext ctx) {
        String className = stripIdentifier(ctx.IDENTIFIER(0).getText());
        WenyanValue classVal = env.get(className);
        if (classVal.type() != WenyanValue.Type.CLASS) {
            throw new IllegalStateException("「" + className + "」不是一个族");
        }
        WenyanClass clazz = classVal.asClass();
        if (clazz.isAbstract()) {
            throw new IllegalStateException("虚族「" + className + "」不可实例化");
        }

        WenyanObject instance = new WenyanObject(clazz);
        // 初始化默认字段值
        addDefaultFields(instance, clazz);

        List<WenyanValue> args = new ArrayList<>();
        for (wenyanParser.DataContext dataCtx : ctx.data()) {
            args.add(evalData(dataCtx));
        }

        WenyanClass.ConstructorDef ctor = clazz.constructor();
        if (ctor != null) {
            if (args.size() < ctor.paramNames().size()) {
                throw new IllegalStateException(
                    "族「" + className + "」之初術需" + ctor.paramNames().size()
                    + "参，但仅提供了" + args.size() + "参");
            }
            executeConstructor(instance, clazz, ctor, args);
        } else if (clazz.parentClass() != null) {
            callParentConstructor(instance, clazz.parentClass(), List.of());
        }

        WenyanValue instanceVal = WenyanValue.instance(instance);
        if (ctx.IDENTIFIER().size() > 1) {
            String instanceName = stripIdentifier(ctx.IDENTIFIER(1).getText());
            env.define(instanceName, instanceVal);
        }
        setPending(List.of(instanceVal));
        return instanceVal;
    }

    private void addDefaultFields(WenyanObject instance, WenyanClass clazz) {
        for (WenyanClass.PropertyDef prop : clazz.properties()) {
            if (prop.defaultValue() != null) {
                instance.fields().put(prop.name(), prop.defaultValue());
            }
        }
        if (clazz.parentClass() != null) {
            addDefaultFields(instance, clazz.parentClass());
        }
    }

    private void executeConstructor(WenyanObject instance, WenyanClass clazz,
                                     WenyanClass.ConstructorDef ctor, List<WenyanValue> args) {
        if (clazz.parentClass() != null) {
            boolean callsSuper = !ctor.body().isEmpty()
                && ctor.body().get(0).getText().contains("施父之初");
            if (!callsSuper) {
                callParentConstructor(instance, clazz.parentClass(), List.of());
            }
        }

        Environment previous = env;
        env = new Environment(ctor.closure());
        instance.beginInit();
        inConstructor = true;
        try {
            env.define("己", WenyanValue.instance(instance));
            for (int i = 0; i < ctor.paramNames().size(); i++) {
                env.define(ctor.paramNames().get(i), i < args.size() ? args.get(i) : WenyanValue.NULL);
            }
            executeStatements(ctor.body());
        } catch (ReturnSignal e) {
            // 构造器中忽略返回值
        } finally {
            inConstructor = false;
            instance.endInit();
            env = previous;
        }
    }

    private void callParentConstructor(WenyanObject instance, WenyanClass parentClass, List<WenyanValue> args) {
        if (parentClass == null) return;
        WenyanClass.ConstructorDef parentCtor = parentClass.constructor();
        if (parentCtor == null) {
            callParentConstructor(instance, parentClass.parentClass(), args);
            return;
        }
        Environment previous = env;
        env = new Environment(parentCtor.closure());
        boolean prevInConstructor = inConstructor;
        inConstructor = true;
        try {
            env.define("己", WenyanValue.instance(instance));
            for (int i = 0; i < parentCtor.paramNames().size(); i++) {
                env.define(parentCtor.paramNames().get(i), i < args.size() ? args.get(i) : WenyanValue.NULL);
            }
            executeStatements(parentCtor.body());
        } catch (ReturnSignal e) {
            // ignore
        } finally {
            inConstructor = prevInConstructor;
            env = previous;
        }
    }

    @Override
    public WenyanValue visitInterface_define(wenyanParser.Interface_defineContext ctx) {
        String ifaceName = stripIdentifier(ctx.IDENTIFIER(0).getText());
        WenyanInterface iface = new WenyanInterface(ifaceName);

        for (wenyanParser.Interface_methodContext method : ctx.interface_method()) {
            String methodName = stripIdentifier(method.IDENTIFIER(0).getText());
            List<String> paramTypes = method.param_type().stream()
                .map(pt -> pt.getText())
                .toList();
            iface.addMethod(methodName, paramTypes);
        }

        registeredInterfaces.put(ifaceName, iface);
        WenyanClass ifaceStub = new WenyanClass(ifaceName, true, false);
        env.define(ifaceName, WenyanValue.clazz(ifaceStub));
        return WenyanValue.NULL;
    }

    @Override
    public WenyanValue visitInstance_method_call(wenyanParser.Instance_method_callContext ctx) {
        // 获取目标：第一个 token 是 '施'，第二个是 IDENTIFIER/'其'/'己'/'父'
        String targetText = ctx.getChild(1).getText();
        WenyanObject instance;
        WenyanClass dispatchClass;

        if ("己".equals(targetText)) {
            instance = env.get("己").asInstance();
            dispatchClass = instance.getWenyanClass();
        } else if ("父".equals(targetText)) {
            instance = env.get("己").asInstance();
            dispatchClass = currentDispatchClass != null ? currentDispatchClass.parentClass() : null;
            if (dispatchClass == null) {
                throw new IllegalStateException("无父类可调用");
            }
        } else if ("其".equals(targetText)) {
            instance = current.asInstance();
            dispatchClass = instance.getWenyanClass();
        } else {
            String varName = stripIdentifier(targetText);
            instance = env.get(varName).asInstance();
            dispatchClass = instance.getWenyanClass();
        }

        String methodName = stripIdentifier(ctx.IDENTIFIER(ctx.IDENTIFIER().size() - 1).getText());
        WenyanClass.MethodDef method = dispatchClass.lookupMethod(methodName);
        if (method == null) {
            throw new IllegalStateException(
                "类「" + dispatchClass.name() + "」无術「" + methodName + "」");
        }
        if (method.isAbstract()) {
            throw new IllegalStateException("不可调用虚術「" + methodName + "」");
        }

        // 可见性检查
        if (method.visibility() != WenyanClass.Visibility.PUBLIC) {
            WenyanClass definingClass = findMethodDefiningClass(dispatchClass, methodName);
            WenyanObject caller = null;
            try {
                caller = env.get("己").asInstance();
            } catch (IllegalStateException ignored) {
                // 己未定义 → 外部调用
            }

            if (method.visibility() == WenyanClass.Visibility.PRIVATE) {
                if (caller == null || caller.getWenyanClass() != definingClass) {
                    throw new IllegalStateException("私有術「" + methodName + "」不可从外部访问");
                }
            } else if (method.visibility() == WenyanClass.Visibility.PROTECTED) {
                if (caller == null) {
                    throw new IllegalStateException("密術「" + methodName + "」不可从外部访问");
                }
                // 检查调用者是否为定义类或其子类
                boolean hasAccess = false;
                WenyanClass c = caller.getWenyanClass();
                while (c != null) {
                    if (c == definingClass) { hasAccess = true; break; }
                    c = c.parentClass();
                }
                if (!hasAccess) {
                    throw new IllegalStateException("密術「" + methodName + "」不可从外部访问");
                }
            }
        }

        List<WenyanValue> args = new ArrayList<>();
        for (wenyanParser.DataContext dataCtx : ctx.data()) {
            args.add(evalData(dataCtx));
        }

        WenyanValue result = callInstanceMethod(instance, method, args, dispatchClass);
        setPending(List.of(result));
        return result;
    }

    private WenyanValue callInstanceMethod(WenyanObject instance, WenyanClass.MethodDef method,
                                            List<WenyanValue> args, WenyanClass dispatchClass) {
        Environment previous = env;
        env = new Environment(method.closure());
        WenyanClass prevDispatch = currentDispatchClass;
        currentDispatchClass = dispatchClass;
        try {
            env.define("己", WenyanValue.instance(instance));
            for (int i = 0; i < method.paramNames().size(); i++) {
                env.define(method.paramNames().get(i), i < args.size() ? args.get(i) : WenyanValue.NULL);
            }
            executeStatements(method.body());
        } catch (ReturnSignal signal) {
            return signal.value();
        } finally {
            currentDispatchClass = prevDispatch;
            env = previous;
        }
        return WenyanValue.NULL;
    }

    @Override
    public WenyanValue visitSuper_constructor_call(wenyanParser.Super_constructor_callContext ctx) {
        if (!inConstructor) {
            throw new IllegalStateException("施父之初仅可在初術中调用");
        }
        WenyanObject instance = env.get("己").asInstance();
        WenyanClass parentClass = instance.getWenyanClass().parentClass();
        if (parentClass == null) {
            throw new IllegalStateException("无父类，不可调用父初術");
        }

        List<WenyanValue> args = new ArrayList<>();
        for (wenyanParser.DataContext dataCtx : ctx.data()) {
            args.add(evalData(dataCtx));
        }

        callParentConstructor(instance, parentClass, args);
        return WenyanValue.NULL;
    }

    /** 查找实际定义该方法的类（沿继承链向上） */
    private WenyanClass findMethodDefiningClass(WenyanClass startClass, String methodName) {
        WenyanClass c = startClass;
        while (c != null) {
            if (c.methods().containsKey(methodName)) return c;
            c = c.parentClass();
        }
        return startClass;
    }

    // 以下访问者仅用于满足接口，实际处理在各自的 process 方法中完成
    @Override public WenyanValue visitClass_member(wenyanParser.Class_memberContext ctx) { return super.visitClass_member(ctx); }
    @Override public WenyanValue visitProperty_define(wenyanParser.Property_defineContext ctx) { return super.visitProperty_define(ctx); }
    @Override public WenyanValue visitProperty_prefix(wenyanParser.Property_prefixContext ctx) { return super.visitProperty_prefix(ctx); }
    @Override public WenyanValue visitMethod_define(wenyanParser.Method_defineContext ctx) { return super.visitMethod_define(ctx); }
    @Override public WenyanValue visitMethod_prefix(wenyanParser.Method_prefixContext ctx) { return super.visitMethod_prefix(ctx); }
    @Override public WenyanValue visitConstructor_define(wenyanParser.Constructor_defineContext ctx) { return super.visitConstructor_define(ctx); }
    @Override public WenyanValue visitInterface_method(wenyanParser.Interface_methodContext ctx) { return super.visitInterface_method(ctx); }


    /**
     * 返回当前解释执行过程产生的全部输出文本。
     *
     * @return 累积输出文本
     */
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
        } catch (RejectionSignal signal) {
            throw signal;
        } finally {
            env = previous;
        }
        return WenyanValue.NULL;
    }

    private WenyanValue invokeCallable(WenyanValue functionValue, List<WenyanValue> args, boolean awaitRequested) {
        if (functionValue.type() == WenyanValue.Type.FUNCTION) {
            WenyanFunction function = functionValue.asFunction();
            if (function.async()) {
                WenyanValue promiseValue = WenyanValue.promise(invokeAsyncFunction(function, args));
                return awaitRequested ? awaitIfPromise(promiseValue) : promiseValue;
            }
            try {
                WenyanValue result = callFunction(function, args);
                return awaitRequested ? awaitIfPromise(result) : result;
            } catch (RejectionSignal signal) {
                WenyanValue rejected = WenyanValue.promise(WenyanPromise.rejected(signal.reason()));
                return awaitRequested ? awaitIfPromise(rejected) : rejected;
            }
        }
        if (functionValue.type() == WenyanValue.Type.NATIVE_FUNCTION) {
            WenyanValue result = functionValue.asNativeFunction().apply(args);
            return awaitRequested ? awaitIfPromise(result) : result;
        }
        throw new IllegalStateException("Identifier is not callable: " + functionValue.type());
    }

    private WenyanPromise invokeAsyncFunction(WenyanFunction function, List<WenyanValue> args) {
        WenyanPromise promise = new WenyanPromise();
        Thread thread = new Thread(() -> {
            try {
                promise.resolve(invokeFunctionIsolated(function, args));
            } catch (RejectionSignal signal) {
                promise.reject(signal.reason());
            } catch (Throwable throwable) {
                promise.reject(errorToValue(throwable));
            }
        }, "wenyan-async-" + System.nanoTime());
        thread.setDaemon(true);
        thread.start();
        return promise;
    }

    private WenyanValue invokeFunctionIsolated(WenyanFunction function, List<WenyanValue> args) {
        WenyanInterpreter child = new WenyanInterpreter(tokens, output, registry);
        return child.callFunction(function, args);
    }

    private WenyanValue awaitIfPromise(WenyanValue value) {
        if (value.type() != WenyanValue.Type.PROMISE) {
            return value;
        }
        WenyanPromise promise = value.asPromise();
        WenyanValue result = promise.await();
        if (promise.isRejected()) {
            throw new IllegalStateException("Promise rejected: " + result.toDisplayString());
        }
        return result;
    }

    private WenyanCallable asCallable(WenyanValue value) {
        if (value.type() == WenyanValue.Type.FUNCTION) {
            WenyanFunction fn = value.asFunction();
            if (fn.async()) {
                return args -> WenyanValue.promise(invokeAsyncFunction(fn, args));
            }
            return args -> invokeFunctionIsolated(fn, args);
        }
        if (value.type() == WenyanValue.Type.NATIVE_FUNCTION) {
            return args -> value.asNativeFunction().apply(args);
        }
        throw new IllegalStateException("Value is not callable: " + value.type());
    }

    private WenyanValue createPromiseSelectorMethod(WenyanPromise promise, boolean successBranch) {
        return WenyanValue.nativeFunction(args -> {
            if (args.isEmpty()) {
                throw new IllegalStateException("Promise continuation requires a callable argument");
            }
            WenyanCallable callable = asCallable(args.getFirst());
            WenyanPromise next = successBranch ? promise.then(callable) : promise.crash(callable);
            return WenyanValue.promise(next);
        });
    }

    private WenyanValue errorToValue(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return WenyanValue.text(message);
    }

    private boolean containsAwaitInStatements(List<wenyanParser.StatementContext> statements) {
        for (wenyanParser.StatementContext statement : statements) {
            if (statement.wait_statement() != null) {
                return true;
            }

            // Nested function definitions define their own async boundary.
            if (statement.function_statement() != null && statement.function_statement().function_define_statement() != null) {
                continue;
            }

            if (statement.if_statement() != null && containsAwaitInStatements(statement.if_statement().statement())) {
                return true;
            }
            if (statement.for_statement() != null) {
                wenyanParser.For_statementContext forStatement = statement.for_statement();
                if (forStatement.for_arr_statement() != null
                    && containsAwaitInStatements(forStatement.for_arr_statement().statement())) {
                    return true;
                }
                if (forStatement.for_enum_statement() != null
                    && containsAwaitInStatements(forStatement.for_enum_statement().statement())) {
                    return true;
                }
                if (forStatement.for_while_statement() != null
                    && containsAwaitInStatements(forStatement.for_while_statement().statement())) {
                    return true;
                }
            }
        }
        return false;
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
        WenyanValue base;
        if (ctx.getText().startsWith("其之")) {
            base = current;
        } else {
            String baseName = stripIdentifier(ctx.IDENTIFIER(0).getText());
            base = env.get(baseName);
        }
        String selector = findSelectorAfterZhi(ctx);
        if (selector == null) {
            throw new IllegalStateException("Invalid selector expression: " + ctx.getText());
        }
        return resolveSelector(base, selector);
    }

    private WenyanValue resolveSelector(WenyanValue base, String selector) {
        String normalizedSelector = normalizeSelectorName(selector);
        if ("長".equals(normalizedSelector)) {
            if (base.type() == WenyanValue.Type.ARRAY) {
                return WenyanValue.number(BigDecimal.valueOf(base.asArray().size()));
            }
            if (base.type() == WenyanValue.Type.STRING) {
                return WenyanValue.number(BigDecimal.valueOf(base.asText().length()));
            }
            if (base.type() == WenyanValue.Type.OBJECT) {
                return WenyanValue.number(BigDecimal.valueOf(base.asObject().size()));
            }
            throw new IllegalStateException("Length unsupported for type: " + base.type());
        }

        if (base.type() == WenyanValue.Type.PROMISE && isQuotedLiteral(selector)) {
            return switch (normalizedSelector) {
                case "繼以", "继以" -> createPromiseSelectorMethod(base.asPromise(), true);
                case "攝錯", "摄错" -> createPromiseSelectorMethod(base.asPromise(), false);
                default -> WenyanValue.NULL;
            };
        }

        if (base.type() == WenyanValue.Type.OBJECT && isQuotedLiteral(selector)) {
            return base.asObject().getOrDefault(normalizedSelector, WenyanValue.NULL);
        }

        if (base.type() == WenyanValue.Type.INSTANCE && isQuotedLiteral(selector)) {
            return base.asInstance().getField(normalizedSelector, null);
        }

        WenyanValue indexValue = evalDataOrCurrent(selector);
        int index = toIndex(indexValue);
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
        if (isStringLiteral(text)) {
            return WenyanValue.text(stripStringLiteral(text));
        }
        if (isIdentifierLiteral(text)) {
            return env.get(stripIdentifier(text));
        }
        if ("陽".equals(text) || "陰".equals(text)) {
            return WenyanValue.bool("陽".equals(text));
        }
        return WenyanValue.number(WenyanNumber.parse(text));
    }

    private void setPending(List<WenyanValue> values) {
        pending = new ArrayList<>(values);
        current = pending.isEmpty() ? WenyanValue.NULL : pending.getLast();
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
        env.define(name, pending.getFirst().copyIfNeeded());
    }

    private void applyNameMulti(wenyanParser.Name_multi_statementContext ctx) {
        List<TerminalNode> ids = ctx.IDENTIFIER();
        for (int i = 0; i < ids.size() && i < pending.size(); i++) {
            env.define(stripIdentifier(ids.get(i).getText()), pending.get(i).copyIfNeeded());
        }
    }

    private String stripIdentifier(String text) {
        if (text.startsWith("「") && text.endsWith("」")) {
            return text.substring(1, text.length() - 1);
        }
        if (text.startsWith("『") && text.endsWith("』")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String stripStringLiteral(String text) {
        if (text.startsWith("「「") && text.endsWith("」」")) {
            return text.substring(2, text.length() - 2);
        }
        if (text.startsWith("『") && text.endsWith("』")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }


    private WenyanValue invokeNativeMethod(Method method, List<WenyanValue> args) {
        try {
            Object[] javaArgs = adaptArguments(method.getParameterTypes(), args);
            Object raw = method.invoke(null, javaArgs);
            return fromJavaValue(raw);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke native function: " + method.getName(), e);
        }
    }

    private Object[] adaptArguments(Class<?>[] parameterTypes, List<WenyanValue> args) {
        Object[] javaArgs = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            WenyanValue arg = i < args.size() ? args.get(i) : WenyanValue.NULL;
            javaArgs[i] = toJavaValue(arg, parameterTypes[i]);
        }
        return javaArgs;
    }

    private Object toJavaValue(WenyanValue value, Class<?> targetType) {
        if (targetType == String.class) {
            return value.asText();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return value.asBoolean();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return value.asNumber().intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return value.asNumber().longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return value.asNumber().doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return value.asNumber().floatValue();
        }
        if (targetType == BigDecimal.class) {
            return value.asNumber();
        }
        if (targetType == BigInteger.class) {
            return value.asNumber().toBigInteger();
        }
        if (targetType == WenyanPromise.class) {
            return value.asPromise();
        }
        if (targetType == WenyanCallable.class) {
            return asCallable(value);
        }
        if (targetType == WenyanValue.class) {
            return value;
        }
        throw new IllegalStateException("Unsupported native parameter type: " + targetType.getName());
    }

    private WenyanValue fromJavaValue(@Nullable Object value) {
        switch (value) {
            case null -> {
                return WenyanValue.NULL;
            }
            case WenyanValue wenyanValue -> {
                return wenyanValue;
            }
            case String text -> {
                return WenyanValue.text(text);
            }
            case Boolean bool -> {
                return WenyanValue.bool(bool);
            }
            case Number number -> {
                return WenyanValue.number(new BigDecimal(number.toString()));
            }
            case WenyanPromise promise -> {
                return WenyanValue.promise(promise);
            }
            case List<?> list -> {
                List<WenyanValue> converted = new ArrayList<>(list.size());
                for (Object item : list) {
                    converted.add(fromJavaValue(item));
                }
                return WenyanValue.array(converted);
            }
            default -> {
            }
        }

        Map<String, WenyanValue> fields = readAnnotatedFields(value);
        if (!fields.isEmpty()) {
            return WenyanValue.object(fields);
        }
        return WenyanValue.text(value.toString());
    }

    private Map<String, WenyanValue> readAnnotatedFields(Object value) {
        Map<String, WenyanValue> fields = new LinkedHashMap<>();
        Class<?> type = value.getClass();

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                WenyuanField fieldAnnotation = component.getAnnotation(WenyuanField.class);
                if (fieldAnnotation == null) {
                    continue;
                }
                try {
                    Object fieldValue = component.getAccessor().invoke(value);
                    WenyanValue converted = fromJavaValue(fieldValue);
                    fields.put(fieldAnnotation.value(), converted);
                    if (!fieldAnnotation.simplified().isEmpty()) {
                        fields.put(fieldAnnotation.simplified(), converted);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Failed to read record field: " + component.getName(), e);
                }
            }
            return fields;
        }

        for (Field field : type.getDeclaredFields()) {
            WenyuanField fieldAnnotation = field.getAnnotation(WenyuanField.class);
            if (fieldAnnotation == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                WenyanValue converted = fromJavaValue(field.get(value));
                fields.put(fieldAnnotation.value(), converted);
                if (!fieldAnnotation.simplified().isEmpty()) {
                    fields.put(fieldAnnotation.simplified(), converted);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field: " + field.getName(), e);
            }
        }
        return fields;
    }

    private boolean isStringLiteral(String text) {
        return (text.startsWith("「「") && text.endsWith("」」"))
               || (text.startsWith("『") && text.endsWith("』"));
    }

    private boolean isIdentifierLiteral(String text) {
        return (text.startsWith("「") && text.endsWith("」"))
               || (text.startsWith("『") && text.endsWith("』"));
    }

    private boolean isQuotedLiteral(String text) {
        return isStringLiteral(text) || isIdentifierLiteral(text);
    }

    private String normalizeSelectorName(String selector) {
        return stripStringLiteral(stripIdentifier(selector));
    }

    private String extractBetween(String text, String start, String end) {
        int from = text.indexOf(start);
        if (from < 0) {
            return "";
        }
        int begin = from + start.length();
        int to = text.indexOf(end, begin);
        if (to < 0) {
            return text.substring(begin);
        }
        return text.substring(begin, to);
    }

    private @Nullable String extractSelector(String segment) {
        int idx = segment.indexOf("之");
        if (idx < 0 || idx == segment.length() - 1) {
            return null;
        }
        return segment.substring(idx + 1);
    }

    private void assignBySelector(WenyanValue target, String selectorText, WenyanValue value) {
        String selector = normalizeSelectorName(selectorText);
        if (target.type() == WenyanValue.Type.OBJECT && isQuotedLiteral(selectorText)) {
            target.asObject().put(selector, value);
            return;
        }
        if (target.type() == WenyanValue.Type.INSTANCE && isQuotedLiteral(selectorText)) {
            target.asInstance().setField(selector, value, null);
            return;
        }
        int index = toIndex(evalDataOrCurrent(selectorText));
        if (target.type() != WenyanValue.Type.ARRAY) {
            throw new IllegalStateException("Indexed assignment unsupported for type: " + target.type());
        }
        List<WenyanValue> arr = target.asArray();
        while (index >= arr.size()) {
            arr.add(WenyanValue.NULL);
        }
        arr.set(index, value);
    }

    private int toIndex(WenyanValue numericSelector) {
        int raw = numericSelector.asNumber().intValue();
        return raw <= 0 ? 0 : raw - 1;
    }

    private @Nullable String findSelectorAfterZhi(ParserRuleContext ctx) {
        for (int i = 0; i < ctx.getChildCount() - 1; i++) {
            if ("之".equals(ctx.getChild(i).getText())) {
                return ctx.getChild(i + 1).getText();
            }
        }
        return null;
    }
}

