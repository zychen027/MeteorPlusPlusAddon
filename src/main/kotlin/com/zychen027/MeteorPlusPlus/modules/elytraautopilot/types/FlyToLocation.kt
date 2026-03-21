package com.zychen027.meteorplusplus.modules.elytraautopilot.types

import com.zychen027.meteorplusplus.modules.elytraautopilot.exceptions.InvalidLocationException

/**
 * 飞行目标位置 - 来自 ElytraAutoPilot
 * 用于存储命名飞行位置
 */
data class FlyToLocation(
    var name: String = "",
    var x: Int = 0,
    var z: Int = 0
) {
    /**
     * 将位置转换为字符串格式：name;x;z
     */
    fun convertLocationToString(): String {
        return "$name;$x;$z"
    }

    companion object {
        /**
         * 从字符串解析 FlyToLocation
         * 格式：name;x;z
         */
        @Throws(InvalidLocationException::class)
        fun convertStringToLocation(input: String): FlyToLocation {
            val parts = input.split(";")
            if (parts.size != 3) {
                throw InvalidLocationException("Invalid location format: $input")
            }
            
            return FlyToLocation(
                name = parts[0].replace(":", ";"),
                x = parts[1].toInt(),
                z = parts[2].toInt()
            )
        }
    }
}
