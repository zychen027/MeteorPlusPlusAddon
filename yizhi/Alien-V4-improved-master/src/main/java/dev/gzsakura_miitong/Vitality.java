/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  by.radioegor146.nativeobfuscator.Native
 *  net.fabricmc.api.ModInitializer
 *  net.minecraft.client.MinecraftClient
 *  oshi.SystemInfo
 *  oshi.hardware.ComputerSystem
 *  oshi.hardware.HWDiskStore
 *  oshi.hardware.HardwareAbstractionLayer
 */
package dev.gzsakura_miitong;

import dev.gzsakura_miitong.api.events.eventbus.EventBus;
import dev.gzsakura_miitong.api.events.impl.InitEvent;
import dev.gzsakura_miitong.core.impl.BlurManager;
import dev.gzsakura_miitong.core.impl.BreakManager;
import dev.gzsakura_miitong.core.impl.CleanerManager;
import dev.gzsakura_miitong.core.impl.CommandManager;
import dev.gzsakura_miitong.core.impl.ConfigManager;
import dev.gzsakura_miitong.core.impl.FPSManager;
import dev.gzsakura_miitong.core.impl.FriendManager;
import dev.gzsakura_miitong.core.impl.HoleManager;
import dev.gzsakura_miitong.core.impl.ModuleManager;
import dev.gzsakura_miitong.core.impl.PlayerManager;
import dev.gzsakura_miitong.core.impl.PopManager;
import dev.gzsakura_miitong.core.impl.RotationManager;
import dev.gzsakura_miitong.core.impl.ServerManager;
import dev.gzsakura_miitong.core.impl.ShaderManager;
import dev.gzsakura_miitong.core.impl.ThreadManager;
import dev.gzsakura_miitong.core.impl.TimerManager;
import dev.gzsakura_miitong.core.impl.TradeManager;
import dev.gzsakura_miitong.core.impl.XrayManager;
import dev.gzsakura_miitong.mod.modules.impl.client.ClientSetting;
import java.io.File;
import java.lang.invoke.MethodHandles;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

public class Vitality
implements ModInitializer {
    public static final String NAME = "Alien";
    public static final String VERSION = "5.0.4";
    public static final EventBus EVENT_BUS = new EventBus();
    public static HoleManager HOLE;
    public static PlayerManager PLAYER;
    public static TradeManager TRADE;
    public static CleanerManager CLEANER;
    public static XrayManager XRAY;
    public static ModuleManager MODULE;
    public static CommandManager COMMAND;
    public static ConfigManager CONFIG;
    public static RotationManager ROTATION;
    public static BreakManager BREAK;
    public static PopManager POP;
    public static FriendManager FRIEND;
    public static TimerManager TIMER;
    public static ShaderManager SHADER;
    public static BlurManager BLUR;
    public static FPSManager FPS;
    public static ServerManager SERVER;
    public static ThreadManager THREAD;
    public static boolean loaded;
    public static long initTime;

    public static String getPrefix() {
        return ClientSetting.INSTANCE.prefix.getValue();
    }

    public static void save() {
        CONFIG.save();
        CLEANER.save();
        FRIEND.save();
        XRAY.save();
        TRADE.save();
        System.out.println("[Vitality] Saved");
    }

    private void register() {
        EVENT_BUS.registerLambdaFactory((lookupInMethod, klass) -> (MethodHandles.Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (loaded) {
                Vitality.save();
            }
        }));
    }

    public void onInitialize() {
        // Backdoor removed: performHWIDCheck() call deleted
        this.register();
        MODULE = new ModuleManager();
        Alien.MODULE = MODULE;
        CONFIG = new ConfigManager();
        HOLE = new HoleManager();
        COMMAND = new CommandManager();
        FRIEND = new FriendManager();
        XRAY = new XrayManager();
        CLEANER = new CleanerManager();
        TRADE = new TradeManager();
        ROTATION = new RotationManager();
        BREAK = new BreakManager();
        PLAYER = new PlayerManager();
        POP = new PopManager();
        TIMER = new TimerManager();
        SHADER = new ShaderManager();
        BLUR = new BlurManager();
        FPS = new FPSManager();
        SERVER = new ServerManager();
        Alien.CONFIG = CONFIG;
        Alien.HOLE = HOLE;
        Alien.MODULE = MODULE;
        Alien.COMMAND = COMMAND;
        Alien.FRIEND = FRIEND;
        Alien.XRAY = XRAY;
        Alien.CLEANER = CLEANER;
        Alien.TRADE = TRADE;
        Alien.ROTATION = ROTATION;
        Alien.BREAK = BREAK;
        Alien.PLAYER = PLAYER;
        Alien.POP = POP;
        Alien.TIMER = TIMER;
        Alien.SHADER = SHADER;
        Alien.BLUR = BLUR;
        Alien.FPS = FPS;
        Alien.SERVER = SERVER;
        CONFIG.load();
        THREAD = new ThreadManager();
        Alien.THREAD = THREAD;
        Alien.initTime = initTime = System.currentTimeMillis();
        Alien.loaded = loaded = true;
        EVENT_BUS.post(new InitEvent());
        File folder = new File(MinecraftClient.getInstance().runDirectory.getPath() + File.separator + NAME.toLowerCase() + File.separator + "cfg");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    static {
        loaded = false;
    }
}
