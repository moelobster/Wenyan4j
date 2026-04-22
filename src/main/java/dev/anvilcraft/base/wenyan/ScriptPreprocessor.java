package dev.anvilcraft.base.wenyan;

import java.util.Map;

final class ScriptPreprocessor {
    private static final String SIMPLIFIED_PAVILION_HEADER = "吾嘗觀『简化秘术』之書";
    private static final String SIMPLIFIED_PAVILION_HEADER_SIMPLIFIED = "吾尝观『简化秘术』之书";

    private static final Map<Character, Character> SIMPLIFIED_TO_TRADITIONAL = Map.ofEntries(
            Map.entry('尝', '嘗'),
            Map.entry('观', '觀'),
            Map.entry('书', '書'),
            Map.entry('术', '術'),
            Map.entry('数', '數'),
            Map.entry('阴', '陰'),
            Map.entry('阳', '陽'),
            Map.entry('长', '長'),
            Map.entry('于', '於'),
            Map.entry('为', '為'),
            Map.entry('义', '義'),
            Map.entry('变', '變'),
            Map.entry('几', '幾'),
            Map.entry('余', '餘'),
            Map.entry('恒', '恆'),
            Map.entry('无', '無'),
            Map.entry('复', '復'),
            Map.entry('万', '萬'),
            Map.entry('亿', '億'),
            Map.entry('丝', '絲'),
            Map.entry('尘', '塵'),
            Map.entry('厘', '釐')
    );

    private ScriptPreprocessor() {
    }

    static String preprocess(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        String normalized = source.charAt(0) == '\uFEFF' ? source.substring(1) : source;
        String body = stripSimplifiedPavilionPrefix(normalized);
        boolean simplifiedMode = body != null;
        if (!simplifiedMode) {
            checkSimplifiedUsage(normalized);
            return normalized;
        }
        return normalizeSimplifiedOutsideQuotes(body);
    }

    private static String normalizeSimplifiedOutsideQuotes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        State state = State.CODE;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';

            if (state == State.CODE) {
                if (ch == '「' && next == '「') {
                    state = State.DOUBLE_CORNER_STRING;
                    out.append(ch);
                    continue;
                }
                if (ch == '「') {
                    state = State.IDENTIFIER;
                    out.append(ch);
                    continue;
                }
                if (ch == '『') {
                    state = State.BOOK_STRING;
                    out.append(ch);
                    continue;
                }
                out.append(SIMPLIFIED_TO_TRADITIONAL.getOrDefault(ch, ch));
                continue;
            }

            out.append(ch);
            if (state == State.DOUBLE_CORNER_STRING && ch == '」' && next == '」') {
                out.append(next);
                i++;
                state = State.CODE;
            } else if (state == State.IDENTIFIER && ch == '」') {
                state = State.CODE;
            } else if (state == State.BOOK_STRING && ch == '』') {
                state = State.CODE;
            }
        }

        return out.toString();
    }

    private static void checkSimplifiedUsage(String text) {
        State state = State.CODE;
        int line = 1;
        int column = 1;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';

            if (state == State.CODE) {
                if (ch == '「' && next == '「') {
                    state = State.DOUBLE_CORNER_STRING;
                } else if (ch == '「') {
                    state = State.IDENTIFIER;
                } else if (ch == '『') {
                    state = State.BOOK_STRING;
                } else if (SIMPLIFIED_TO_TRADITIONAL.containsKey(ch)) {
                    throw new IllegalStateException(
                            "Simplified Wenyan token '" + ch + "' at " + line + ":" + column
                                    + "; add `吾嘗觀『简化秘术』之書。` as the first line to enable simplified mode."
                    );
                }
            } else if (state == State.DOUBLE_CORNER_STRING && ch == '」' && next == '」') {
                i++;
                column++;
                state = State.CODE;
            } else if (state == State.IDENTIFIER && ch == '」') {
                state = State.CODE;
            } else if (state == State.BOOK_STRING && ch == '』') {
                state = State.CODE;
            }

            if (ch == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
    }

    private static String stripSimplifiedPavilionPrefix(String source) {
        Integer matched = null;
        if (source.startsWith(SIMPLIFIED_PAVILION_HEADER)) {
            matched = SIMPLIFIED_PAVILION_HEADER.length();
        } else if (source.startsWith(SIMPLIFIED_PAVILION_HEADER_SIMPLIFIED)) {
            matched = SIMPLIFIED_PAVILION_HEADER_SIMPLIFIED.length();
        }
        if (matched == null) {
            return null;
        }

        int from = matched;
        if (from < source.length() && source.charAt(from) == '。') {
            from++;
        }
        while (from < source.length()) {
            char ch = source.charAt(from);
            if (ch == '\r' || ch == '\n' || ch == ' ' || ch == '\t') {
                from++;
                continue;
            }
            break;
        }
        return source.substring(from);
    }

    private enum State {
        CODE,
        DOUBLE_CORNER_STRING,
        IDENTIFIER,
        BOOK_STRING
    }
}


