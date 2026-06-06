package com.zychen027.meteorplusplus.utils.efa.events

import meteordevelopment.meteorclient.events.Cancellable
import net.minecraft.entity.Entity

class ElytraUpdateEvent(val entity: Entity) : Cancellable()
