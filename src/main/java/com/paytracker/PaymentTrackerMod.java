package com.paytracker;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentTrackerMod implements ClientModInitializer {
    private static final String VERSION = "v10";

    private static KeyBinding openKey;
    private static String lastOverlay = "";
    private static long lastOverlayTime = 0;
    private static boolean debug = false;
    private static Object lastPlayer = null;

    @Override
    public void onInitializeClient() {
        TrackerData.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.paytracker.open_panel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.paytracker"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new PaymentScreen());
            }
            // one-time hello after joining a world/server, so you can see the mod is loaded
            if (client.player != null && client.player != lastPlayer) {
                lastPlayer = client.player;
                client.player.sendMessage(Text.literal("[PayTracker " + VERSION + "] ").formatted(Formatting.LIGHT_PURPLE)
                        .append(Text.literal("loaded - press Right Shift to open").formatted(Formatting.GRAY)), false);
            }
        });

        // System messages: chat box + action bar.
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                process(message.getString(), overlay, overlay ? "GAME (action bar)" : "GAME"));

        // Chat lines the server sends WITHOUT a signed player profile are server-made
        // (many servers send payment lines this way). Real signed player chat is never tracked.
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String text = message.getString();
            if (signedMessage == null) {
                process(text, false, "CHAT (server)");
            } else if (debug && looksInteresting(text)) {
                say("[PT debug] CHAT (player message, not tracked): " + text);
            }
        });

        // /paytracker debug               -> toggles debug output
        // /paytracker test <message text> -> checks a message against your patterns (nothing is saved)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("paytracker")
                        .then(ClientCommandManager.literal("debug").executes(ctx -> {
                            debug = !debug;
                            ctx.getSource().sendFeedback(Text.literal("[PayTracker] debug " + (debug ? "ON" : "OFF")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("test")
                                .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String t = StringArgumentType.getString(ctx, "text");
                                            String res = handle(t, false, true);
                                            ctx.getSource().sendFeedback(Text.literal("[PayTracker] test -> "
                                                    + (res == null ? "no pattern matched" : res)));
                                            return 1;
                                        })))));
    }

    private static void process(String text, boolean overlay, String channel) {
        String res = handle(text, overlay, false);
        if (res != null && (res.contains("BELOW") || res.contains("ABOVE"))) {
            // never silent: tell the player why a payment wasn't logged
            say("[PayTracker] Not logged: " + res);
        }
        if (debug && (res != null || looksInteresting(text))) {
            say("[PT debug] " + channel + ": " + text);
            say("[PT debug] -> " + (res == null ? "no pattern matched" : res));
        }
    }

    private static boolean looksInteresting(String text) {
        String l = text.toLowerCase();
        return l.contains("paid") || l.contains("pay") || l.contains("$") || l.contains("receiv");
    }

    private static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s).formatted(Formatting.GRAY), false);
    }

    /** Returns a short result description, or null if no pattern matched. */
    private static String handle(String text, boolean overlay, boolean dryRun) {
        if (overlay && !dryRun) {
            // action bar text can be re-sent repeatedly; ignore repeats for 5s
            long now = System.currentTimeMillis();
            boolean repeat = text.equals(lastOverlay) && now - lastOverlayTime < 5000;
            lastOverlay = text;
            lastOverlayTime = now;
            if (repeat) return "repeat ignored";
        }

        TrackerData data = TrackerData.INSTANCE;
        for (String regex : data.patterns) {
            Matcher m;
            try {
                m = Pattern.compile(regex).matcher(text);
            } catch (Exception e) {
                continue; // bad regex in config, skip it
            }
            if (!m.find()) continue;

            double amount;
            try {
                amount = Money.toValue(m.group("amount"), group(m, "suffix"));
            } catch (Exception e) {
                continue;
            }

            String player = group(m, "player");
            if (player == null) player = "Unknown";
            String what = "matched $" + Money.format(amount) + " from " + player;

            if (amount < data.threshold) {
                return what + " but BELOW your minimum ($" + Money.format(data.threshold) + ")";
            }
            if (data.maximum > 0 && amount > data.maximum) {
                return what + " but ABOVE your maximum ($" + Money.format(data.maximum) + ")";
            }
            if (dryRun) return what + " -> would be LOGGED";

            data.entries.add(new TrackerData.Entry(player, amount, System.currentTimeMillis()));
            TrackerData.save();

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[PayTracker] ").formatted(Formatting.LIGHT_PURPLE)
                        .append(Text.literal("Logged $" + Money.format(amount) + " from " + player)
                                .formatted(Formatting.WHITE)), false);
            }
            return what + " -> LOGGED";
        }
        return null;
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null; // this pattern has no such group
        }
    }
}
