package dev.anvilcraft.base.wenyan.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public final class WenyanNumber {
    private static final String[] CHINESE_DIGITS = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] SMALL_INTEGER_UNITS = {"", "十", "百", "千"};
    private static final String[] LARGE_INTEGER_UNITS = {"", "萬", "億", "兆", "京", "垓"};

    private static final Map<Character, Integer> DIGITS = Map.ofEntries(
            Map.entry('零', 0),
            Map.entry('〇', 0),
            Map.entry('一', 1),
            Map.entry('二', 2),
            Map.entry('三', 3),
            Map.entry('四', 4),
            Map.entry('五', 5),
            Map.entry('六', 6),
            Map.entry('七', 7),
            Map.entry('八', 8),
            Map.entry('九', 9)
    );

    private static final Map<Character, Integer> SMALL_UNITS = Map.of(
            '十', 10,
            '百', 100,
            '千', 1000
    );

    private static final Map<Character, Long> LARGE_UNITS = Map.of(
            '萬', 10_000L,
            '亿', 100_000_000L,
            '億', 100_000_000L,
            '兆', 1_000_000_000_000L
    );

    private WenyanNumber() {
    }

    public static String toChineseText(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return "零";
        }

        if (normalized.scale() <= 0) {
            return toChineseInteger(normalized.toBigIntegerExact());
        }

        BigDecimal abs = normalized.abs();
        String plain = abs.toPlainString();
        String[] parts = plain.split("\\.", 2);
        String integerText = toChineseInteger(new BigInteger(parts[0]));
        String fraction = parts.length > 1 ? parts[1] : "";

        StringBuilder text = new StringBuilder();
        if (normalized.signum() < 0) {
            text.append("負");
        }
        text.append(integerText).append("點");
        for (int i = 0; i < fraction.length(); i++) {
            int digit = fraction.charAt(i) - '0';
            text.append(CHINESE_DIGITS[digit]);
        }
        return text.toString();
    }

    public static BigDecimal parse(String text) {
        String s = text.trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            return new BigDecimal(s);
        }
        if (s.contains("又")) {
            return parseFloatLike(s);
        }
        return new BigDecimal(parseIntegerChinese(s));
    }

    private static BigDecimal parseFloatLike(String text) {
        String[] parts = text.split("又", 2);
        BigDecimal integerPart = new BigDecimal(parseIntegerChinese(parts[0]));
        String fraction = parts.length > 1 ? parts[1] : "";
        BigDecimal result = integerPart;
        result = result.add(extractFraction(fraction, "分", 10));
        result = result.add(extractFraction(fraction, "釐", 100));
        result = result.add(extractFraction(fraction, "毫", 1000));
        result = result.add(extractFraction(fraction, "絲", 10000));
        result = result.add(extractFraction(fraction, "忽", 100000));
        result = result.add(extractFraction(fraction, "微", 1000000));
        return result;
    }

    private static BigDecimal extractFraction(String source, String unit, int denominator) {
        int idx = source.indexOf(unit);
        if (idx < 0) {
            return BigDecimal.ZERO;
        }
        int start = idx - 1;
        while (start >= 0 && DIGITS.containsKey(source.charAt(start))) {
            start--;
        }
        String number = source.substring(start + 1, idx);
        if (number.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal numerator = new BigDecimal(parseIntegerChinese(number));
        return numerator.divide(BigDecimal.valueOf(denominator));
    }

    private static BigInteger parseIntegerChinese(String text) {
        BigInteger total = BigInteger.ZERO;
        BigInteger section = BigInteger.ZERO;
        int number = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (DIGITS.containsKey(ch)) {
                number = DIGITS.get(ch);
                continue;
            }
            if (SMALL_UNITS.containsKey(ch)) {
                int unit = SMALL_UNITS.get(ch);
                if (number == 0) {
                    number = 1;
                }
                section = section.add(BigInteger.valueOf((long) number * unit));
                number = 0;
                continue;
            }
            if (LARGE_UNITS.containsKey(ch)) {
                long unit = LARGE_UNITS.get(ch);
                section = section.add(BigInteger.valueOf(number));
                if (section.equals(BigInteger.ZERO)) {
                    section = BigInteger.ONE;
                }
                total = total.add(section.multiply(BigInteger.valueOf(unit)));
                section = BigInteger.ZERO;
                number = 0;
            }
        }

        section = section.add(BigInteger.valueOf(number));
        return total.add(section);
    }

    private static String toChineseInteger(BigInteger value) {
        if (value.signum() == 0) {
            return "零";
        }

        BigInteger abs = value.abs();
        String digits = abs.toString();
        int groupCount = (digits.length() + 3) / 4;
        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;

        for (int groupIndex = groupCount - 1; groupIndex >= 0; groupIndex--) {
            int start = Math.max(0, digits.length() - (groupIndex + 1) * 4);
            int end = digits.length() - groupIndex * 4;
            int groupValue = Integer.parseInt(digits.substring(start, end));

            if (groupValue == 0) {
                pendingZero = result.length() > 0;
                continue;
            }

            if (pendingZero) {
                result.append("零");
                pendingZero = false;
            }

            String groupText = toChineseFourDigits(groupValue);
            if (!groupText.isEmpty()) {
                result.append(groupText);
                if (groupIndex < LARGE_INTEGER_UNITS.length) {
                    result.append(LARGE_INTEGER_UNITS[groupIndex]);
                }
            }

            if (groupValue < 1000 && groupIndex > 0) {
                pendingZero = true;
            }
        }

        if (value.signum() < 0) {
            result.insert(0, "負");
        }
        return result.toString();
    }

    private static String toChineseFourDigits(int value) {
        StringBuilder result = new StringBuilder();
        boolean zeroPending = false;

        for (int unit = 3; unit >= 0; unit--) {
            int divisor = (int) Math.pow(10, unit);
            int digit = value / divisor;
            value %= divisor;

            if (digit == 0) {
                zeroPending = result.length() > 0;
                continue;
            }

            if (zeroPending) {
                result.append("零");
                zeroPending = false;
            }

            if (!(digit == 1 && unit == 1 && result.length() == 0)) {
                result.append(CHINESE_DIGITS[digit]);
            }
            result.append(SMALL_INTEGER_UNITS[unit]);
        }

        return result.toString();
    }
}

