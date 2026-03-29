package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.systems.friends.Friends;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/FriendsModule.class */
public class FriendsModule extends Module {
    public static FriendsModule INSTANCE;
    private final SettingGroup sgGeneral;
    private final Setting<Boolean> autoReload;

    public FriendsModule() {
        super(XALUAddon.CATEGORY, "Friends List", "Displays friends list.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.autoReload = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("auto-reload")).description("Automatically reload friends list on module enable.")).defaultValue(true)).build());
        INSTANCE = this;
    }

    public void onActivate() {
        if (((Boolean) this.autoReload.get()).booleanValue()) {
            Friends.load();
        }
        displayFriendsList();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        toggle();
    }

    private void displayFriendsList() {
        info("Friends list:", new Object[0]);
        int count = 0;
        for (Friends.Friend friend : Friends.getFriends()) {
            info("- " + friend.name, new Object[0]);
            count++;
        }
        if (count == 0) {
            info("No friends found. Add friends by editing friends.txt file directly.", new Object[0]);
        } else {
            info("Total: " + count + " friends", new Object[0]);
        }
    }
}
