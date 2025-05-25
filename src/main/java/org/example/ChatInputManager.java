// ChatInputManager.java
package org.example;

import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Consumer;

public class ChatInputManager {
    private final Main plugin;
    private final Map<UUID, ChatInput> pendingInputs;

    public ChatInputManager(Main plugin) {
        this.plugin = plugin;
        this.pendingInputs = new HashMap<>();
    }

    public void awaitChatInput(Player player, String prompt, Consumer<String> callback) {
        pendingInputs.put(player.getUniqueId(), new ChatInput(callback));
        player.sendMessage(prompt);
    }

    public boolean handleChatInput(Player player, String message) {
        ChatInput input = pendingInputs.remove(player.getUniqueId());
        if (input != null) {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c[LandClaims] Action cancelled.");
                return true;
            }
            input.getCallback().accept(message);
            return true;
        }
        return false;
    }

    private static class ChatInput {
        private final Consumer<String> callback;

        public ChatInput(Consumer<String> callback) {
            this.callback = callback;
        }

        public Consumer<String> getCallback() {
            return callback;
        }
    }
}
