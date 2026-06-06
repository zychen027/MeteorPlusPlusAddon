package com.zychen027.meteorplusplus.utils.efa.events

import meteordevelopment.meteorclient.events.Cancellable
import net.minecraft.entity.player.PlayerEntity

class TravelEvent(val entity: PlayerEntity) : Cancellable()
