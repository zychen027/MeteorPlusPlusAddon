package com.dev.xalu.commands;

import com.dev.xalu.systems.friends.Friends;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.class_2172;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/commands/FriendsCommand.class */
public class FriendsCommand extends Command {
    public FriendsCommand() {
        super("xfriends", "Manages friends list.", new String[0]);
    }

    public void build(LiteralArgumentBuilder<class_2172> builder) {
        LiteralArgumentBuilder<class_2172> listCommand = LiteralArgumentBuilder.literal("list");
        listCommand.executes(context -> {
            List<Friends.Friend> friends = Friends.getFriends();
            if (friends.isEmpty()) {
                info("No friends found.", new Object[0]);
                return 1;
            }
            StringBuilder friendsList = new StringBuilder("Friends: ");
            for (int i = 0; i < friends.size(); i++) {
                friendsList.append(friends.get(i).name);
                if (i < friends.size() - 1) {
                    friendsList.append(", ");
                }
            }
            info(friendsList.toString(), new Object[0]);
            return 1;
        });
        builder.then(listCommand);
    }
}
