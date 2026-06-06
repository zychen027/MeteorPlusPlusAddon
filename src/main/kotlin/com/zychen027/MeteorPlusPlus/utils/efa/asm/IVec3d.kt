package com.zychen027.meteorplusplus.utils.efa.asm

import net.minecraft.util.math.Vec3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(Vec3d::class)
interface IVec3d {
    @Mutable
    @Accessor("x")
    fun setX(x: Double)

    @Mutable
    @Accessor("y")
    fun setY(y: Double)

    @Mutable
    @Accessor("z")
    fun setZ(z: Double)
}
