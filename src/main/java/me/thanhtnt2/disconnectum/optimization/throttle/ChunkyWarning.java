package me.thanhtnt2.disconnectum.optimization.throttle;

import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds and sends the "server overloaded while Chunky runs" chat message with two clickable choices.
 *
 * <p>Every text uses a translation key with an English fallback, so players whose client lacks the mod's
 * language file (for example on a dedicated server) still read proper English instead of raw keys.
 */
final class ChunkyWarning {

    static final String CONTINUE_COMMAND = "/disconnectum chunky continue";
    static final String STOP_COMMAND = "/disconnectum chunky stop";

    private ChunkyWarning() {
    }

    static void broadcast(MinecraftServer server, ServerLoadSnapshot snapshot, int ignoreMinutes) {
        Component message = build(snapshot, ignoreMinutes);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static Component build(ServerLoadSnapshot snapshot, int ignoreMinutes) {
        MutableComponent headline = Component.translatableWithFallback(
                "disconnectum.chunky.warning",
                "[Disconnectum] The server is overloaded (tick %s ms). Running Chunky now risks a \"server disconnected\" crash.",
                Math.round(snapshot.smoothedTickMillis())).withStyle(ChatFormatting.YELLOW);

        MutableComponent continueButton = button(
                "disconnectum.chunky.continue", "[Continue Chunky]", ChatFormatting.RED, CONTINUE_COMMAND,
                Component.translatableWithFallback("disconnectum.chunky.continue.hover",
                        "High risk. Keep Chunky running and stand still so no new chunks load. Warnings are muted for %s minutes.",
                        ignoreMinutes));
        MutableComponent stopButton = button(
                "disconnectum.chunky.stop", "[Stop Chunky]", ChatFormatting.GREEN, STOP_COMMAND,
                Component.translatableWithFallback("disconnectum.chunky.stop.hover",
                        "Pause Chunky now. Disconnectum continues it when the server recovers, or use /disconnectum chunky continue."));

        return headline.append(Component.literal("\n")).append(continueButton).append(Component.literal("  ")).append(stopButton);
    }

    private static MutableComponent button(String key, String fallback, ChatFormatting color,
                                           String command, Component hover) {
        Style style = Style.EMPTY
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        return Component.translatableWithFallback(key, fallback).withStyle(style);
    }
}
