package dev.anvilcraft.base.wenyan.runtime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class WenyanValue {
    public static final WenyanValue NULL = new WenyanValue(Type.NULL, null);

    public enum Type {
        NUMBER,
        STRING,
        BOOLEAN,
        ARRAY,
        FUNCTION,
        NATIVE_FUNCTION,
        PROMISE,
        OBJECT,
        NULL
    }

    private final Type type;
    private final @Nullable Object value;

    private WenyanValue(Type type, @Nullable Object value) {
        this.type = type;
        this.value = value;
    }

    public static WenyanValue number(BigDecimal value) {
        return new WenyanValue(Type.NUMBER, value.stripTrailingZeros());
    }

    public static WenyanValue number(double value) {
        return new WenyanValue(Type.NUMBER, BigDecimal.valueOf(value));
    }

    public static WenyanValue number(float value) {
        return new WenyanValue(Type.NUMBER, BigDecimal.valueOf(value));
    }

    public static WenyanValue number(byte value) {
        return new WenyanValue(Type.NUMBER, BigDecimal.valueOf(value));
    }

    public static WenyanValue number(short value) {
        return new WenyanValue(Type.NUMBER, BigDecimal.valueOf(value));
    }

    public static WenyanValue number(int value) {
        return new WenyanValue(Type.NUMBER, BigDecimal.valueOf(value));
    }

    public static WenyanValue number(long value) {
        return new WenyanValue(Type.NUMBER, BigDecimal.valueOf(value));
    }

    public static WenyanValue text(String value) {
        return new WenyanValue(Type.STRING, value);
    }

    public static WenyanValue bool(boolean value) {
        return new WenyanValue(Type.BOOLEAN, value);
    }

    public static WenyanValue array(List<WenyanValue> value) {
        return new WenyanValue(Type.ARRAY, new ArrayList<>(value));
    }

    public static WenyanValue array(WenyanValue... value) {
        return new WenyanValue(Type.ARRAY, List.of(value));
    }

    public static WenyanValue function(WenyanFunction value) {
        return new WenyanValue(Type.FUNCTION, value);
    }

    public static WenyanValue nativeFunction(Function<List<WenyanValue>, WenyanValue> value) {
        return new WenyanValue(Type.NATIVE_FUNCTION, value);
    }

    public static WenyanValue promise(WenyanPromise value) {
        return new WenyanValue(Type.PROMISE, value);
    }

    public static WenyanValue object(Map<String, WenyanValue> value) {
        return new WenyanValue(Type.OBJECT, new LinkedHashMap<>(value));
    }

    public Type type() {
        return type;
    }

    @SuppressWarnings("DataFlowIssue")
    public BigDecimal asNumber() {
        if (type == Type.NUMBER) {
            return (BigDecimal) value;
        }
        if (type == Type.BOOLEAN) {
            return (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        throw new IllegalStateException("Expected number but got " + type);
    }

    @SuppressWarnings("DataFlowIssue")
    public String asText() {
        if (type == Type.STRING) {
            return (String) value;
        }
        return toDisplayString();
    }

    @SuppressWarnings("DataFlowIssue")
    public boolean asBoolean() {
        return switch (type) {
            case BOOLEAN -> (Boolean) value;
            case NUMBER -> asNumber().compareTo(BigDecimal.ZERO) != 0;
            case STRING -> !((String) value).isEmpty();
            case ARRAY -> !asArray().isEmpty();
            case FUNCTION, NATIVE_FUNCTION, PROMISE -> true;
            case OBJECT -> !asObject().isEmpty();
            case NULL -> false;
        };
    }

    @SuppressWarnings(
        {
            "unchecked",
            "DataFlowIssue"
        }
    )
    public List<WenyanValue> asArray() {
        if (type != Type.ARRAY) {
            throw new IllegalStateException("Expected array but got " + type);
        }
        return (List<WenyanValue>) value;
    }

    @SuppressWarnings("DataFlowIssue")
    public WenyanFunction asFunction() {
        if (type != Type.FUNCTION) {
            throw new IllegalStateException("Expected function but got " + type);
        }
        return (WenyanFunction) value;
    }

    @SuppressWarnings(
        {
            "unchecked",
            "DataFlowIssue"
        }
    )
    public Function<List<WenyanValue>, WenyanValue> asNativeFunction() {
        if (type != Type.NATIVE_FUNCTION) {
            throw new IllegalStateException("Expected native function but got " + type);
        }
        return (Function<List<WenyanValue>, WenyanValue>) value;
    }

    @SuppressWarnings("DataFlowIssue")
    public WenyanPromise asPromise() {
        if (type != Type.PROMISE) {
            throw new IllegalStateException("Expected promise but got " + type);
        }
        return (WenyanPromise) value;
    }

    @SuppressWarnings(
        {
            "unchecked",
            "DataFlowIssue"
        }
    )
    public Map<String, WenyanValue> asObject() {
        if (type != Type.OBJECT) {
            throw new IllegalStateException("Expected object but got " + type);
        }
        return (Map<String, WenyanValue>) value;
    }

    public WenyanValue copyIfNeeded() {
        if (type == Type.ARRAY) {
            return array(asArray());
        }
        if (type == Type.OBJECT) {
            return object(asObject());
        }
        return this;
    }

    @SuppressWarnings("DataFlowIssue")
    public String toDisplayString() {
        return switch (type) {
            case NUMBER -> WenyanNumber.toChineseText(asNumber());
            case STRING -> (String) value;
            case BOOLEAN -> (Boolean) value ? "陽" : "陰";
            case ARRAY -> asArray().stream().map(WenyanValue::toDisplayString).toList().toString();
            case FUNCTION, NATIVE_FUNCTION -> "<術>";
            case PROMISE -> asPromise().toString();
            case OBJECT -> asObject().entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().toDisplayString())
                .toList()
                .toString();
            case NULL -> "空無";
        };
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WenyanValue other)) {
            return false;
        }
        return type == other.type && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }
}

