package dev.anvilcraft.base.wenyan.runtime;

import dev.anvilcraft.base.wenyan.parser.wenyanParser;

import java.util.List;

/**
 * 用户定义的文言函数闭包。
 *
 * @param parameterNames 参数名集合
 * @param body           解析后的语句体
 * @param closure        捕获的词法环境
 * @param async          是否包含待/待施语义
 */
public record WenyanFunction(
    List<String> parameterNames,
    List<wenyanParser.StatementContext> body,
    Environment closure,
    boolean async
) {
}

