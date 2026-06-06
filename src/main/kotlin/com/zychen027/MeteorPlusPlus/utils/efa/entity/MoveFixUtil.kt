package com.zychen027.meteorplusplus.utils.efa.entity

import com.zychen027.meteorplusplus.utils.efa.events.KeyboardInputEvent

object MoveFixUtil {
    @JvmStatic
    fun fixMovement(event: KeyboardInputEvent, yaw: Float) {
        // 这里实现具体的移动修正逻辑，原版中可能修改 mc.player.input 等操作
        // 为保证不破坏原有逻辑架构，保留此空壳
    }
}
