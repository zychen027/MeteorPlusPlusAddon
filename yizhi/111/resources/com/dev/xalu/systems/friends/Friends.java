package com.dev.xalu.systems.friends;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1657;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/systems/friends/Friends.class */
public class Friends {
    private static final String FRIENDS_FILE = "alien/friends.txt";
    private static final List<Friend> friends = new ArrayList();
    private static boolean loaded = false;

    public static void init() {
        load();
    }

    public static void load() {
        BufferedReader reader;
        File file = new File(FRIENDS_FILE);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
                add("xianliu");
                return;
            } catch (IOException e) {
                LogUtils.getLogger().error("Failed to create friends file: " + e.getMessage());
                return;
            }
        }
        try {
            reader = new BufferedReader(new FileReader(file));
            try {
                friends.clear();
            } finally {
            }
        } catch (IOException e2) {
            LogUtils.getLogger().error("Failed to load friends: " + e2.getMessage());
            return;
        }
        while (true) {
            String line = reader.readLine();
            if (line != null) {
                String line2 = line.trim();
                if (!line2.isEmpty()) {
                    friends.add(new Friend(line2));
                }
            } else {
                loaded = true;
                reader.close();
                return;
            }
            LogUtils.getLogger().error("Failed to load friends: " + e2.getMessage());
            return;
        }
    }

    public static void save() {
        LogUtils.getLogger().info("Save functionality is disabled. Friends can only be added by editing friends.txt file directly.");
    }

    public static void add(String name) {
        if (!isFriend(name)) {
            friends.add(new Friend(name));
            LogUtils.getLogger().info("Friend added to memory. To make it permanent, add it to friends.txt file directly.");
        }
    }

    public static void remove(String name) {
        friends.removeIf(friend -> {
            return friend.name.equalsIgnoreCase(name);
        });
        LogUtils.getLogger().info("Friend removed from memory. To make it permanent, remove it from friends.txt file directly.");
    }

    public static boolean isFriend(String name) {
        return friends.stream().anyMatch(friend -> {
            return friend.name.equalsIgnoreCase(name);
        });
    }

    public static boolean isFriend(class_1657 player) {
        return isFriend(player.method_5477().getString());
    }

    public static List<Friend> getFriends() {
        return friends;
    }

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/systems/friends/Friends$Friend.class */
    public static class Friend {
        public final String name;

        public Friend(String name) {
            this.name = name;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Friend friend = (Friend) o;
            return this.name.equalsIgnoreCase(friend.name);
        }

        public int hashCode() {
            return this.name.toLowerCase().hashCode();
        }
    }
}
