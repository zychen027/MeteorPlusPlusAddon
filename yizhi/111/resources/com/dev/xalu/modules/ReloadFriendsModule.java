package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.systems.friends.Friends;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/ReloadFriendsModule.class */
public class ReloadFriendsModule extends Module {
    public static ReloadFriendsModule INSTANCE;

    public ReloadFriendsModule() {
        super(XALUAddon.CATEGORY, "Reload Friends", "Reloads the friends list.");
        INSTANCE = this;
    }

    public void onActivate() {
        reloadFriends();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        toggle();
    }

    private void reloadFriends() {
        Friends.load();
        info("Friends list reloaded!", new Object[0]);
    }
}
