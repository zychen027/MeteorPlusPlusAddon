/*
 * Decompiled with CFR 0.152.
 */
package dev.gzsakura_miitong.mod.commands.impl;

import dev.gzsakura_miitong.Alien;
import dev.gzsakura_miitong.core.Manager;
import dev.gzsakura_miitong.core.impl.ConfigManager;
import dev.gzsakura_miitong.mod.commands.Command;
import dev.gzsakura_miitong.mod.modules.impl.client.Fonts;
import java.io.File;
import java.util.List;

public class LoadCommand
extends Command {
    public LoadCommand() {
        super("load", "[config]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            this.sendUsage();
            return;
        }
        this.sendChatMessage("\u00a7fLoading..");
        ConfigManager.options = Manager.getFile("cfg" + File.separator + parameters[0] + ".cfg");
        Alien.CONFIG.read();
        Alien.CONFIG.load();
        ConfigManager.options = Manager.getFile("options.txt");
        Fonts.INSTANCE.refresh();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}

