package com.zychen027.meteorplusplus.modules.elytraautopilot.exceptions

/**
 * 无效位置异常 - 来自 ElytraAutoPilot
 * 当 FlyToLocation 字符串格式无效时抛出
 */
class InvalidLocationException(message: String) : Exception(message)
