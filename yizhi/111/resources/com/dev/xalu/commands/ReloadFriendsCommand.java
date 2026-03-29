package com.dev.xalu.commands;

import com.dev.xalu.systems.friends.Friends;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.class_2172;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/commands/ReloadFriendsCommand.class */
public class ReloadFriendsCommand extends Command {
    public ReloadFriendsCommand() {
        super("reloadfriends", "Reloads the friends list from friends.txt file (read-only).", new String[0]);
    }

    public void build(LiteralArgumentBuilder<class_2172> builder) {
        builder.executes(context -> {
            Friends.load();
            info("Successfully reloaded friends list from friends.txt file.", new Object[0]);
            int friendCount = Friends.getFriends().size();
            info("Current friends: " + friendCount, new Object[0]);
            return 1;
        });
    }
}
