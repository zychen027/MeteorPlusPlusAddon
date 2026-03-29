package com.dev.xalu;

import com.dev.xalu.commands.FriendsCommand;
import com.dev.xalu.commands.ReloadFriendsCommand;
import com.dev.xalu.modules.AutoCityPlus;
import com.dev.xalu.modules.ElytraFly;
import com.dev.xalu.modules.FollowModule;
import com.dev.xalu.modules.FriendsModule;
import com.dev.xalu.modules.MovementSync;
import com.dev.xalu.modules.NoSlow;
import com.dev.xalu.modules.ReloadFriendsModule;
import com.dev.xalu.modules.SpeedMine;
import com.dev.xalu.modules.SpeedMinePlus;
import com.dev.xalu.systems.friends.Friends;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/XALUAddon.class */
public class XALUAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("§dXALU");

    public void onInitialize() {
        LOG.info("Initializing XALU");
        Friends.init();
        Commands.add(new ReloadFriendsCommand());
        Commands.add(new FriendsCommand());
        Modules.get().add(new AutoCityPlus());
        Modules.get().add(new SpeedMine());
        Modules.get().add(new SpeedMinePlus());
        Modules.get().add(new FriendsModule());
        Modules.get().add(new ReloadFriendsModule());
        Modules.get().add(new FollowModule());
        Modules.get().add(new NoSlow());
        Modules.get().add(new ElytraFly());
        Modules.get().add(new MovementSync());
    }

    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    public String getPackage() {
        return "com.dev.xalu";
    }

    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
