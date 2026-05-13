package com.example.demo.rag;

/**
 * RagException类。
 * 该类型负责 RAG 模块中的基础支撑逻辑。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class RagException extends RuntimeException {

    /**
     * 构造并初始化 RagException 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。
     * @param message 输入参数 message，用于参与本次处理流程。
     */
    public RagException(String message) {
        super(message);
    }

    /**
     * 构造并初始化 RagException 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。
     * @param message 输入参数 message，用于参与本次处理流程。
     * @param cause 输入参数 cause，用于参与本次处理流程。
     */
    public RagException(String message, Throwable cause) {
        super(message, cause);
    }
}
