package me.thanhtnt2.disconnectum.optimization.command;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import me.thanhtnt2.disconnectum.optimization.config.ChunkyMode;
import me.thanhtnt2.disconnectum.optimization.config.ConfigPreset;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import me.thanhtnt2.disconnectum.optimization.monitor.ServerLoadMonitor;
import me.thanhtnt2.disconnectum.optimization.throttle.ChunkyThrottle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /disconnectum} commands.
 *
 * <pre>
 * /disconnectum status
 * /disconnectum chunky continue | stop | mode ask|auto|off
 * /disconnectum preset relaxed|balanced|strict|off
 * </pre>
 *
 * Allowed for operators (permission level 2) and for the owner of a singleplayer world, even with cheats off,
 * so the clickable chat buttons work for everyone who needs them.
 */
public final class DisconnectumCommand {

    private DisconnectumCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ChunkyThrottle chunkyThrottle) {
        LiteralArgumentBuilder<CommandSourceStack> chunky = Commands.literal("chunky")
                .then(Commands.literal("continue").executes(context -> continueChunky(context, chunkyThrottle)))
                .then(Commands.literal("stop").executes(context -> stopChunky(context, chunkyThrottle)))
                .then(modeCommands());

        LiteralArgumentBuilder<CommandSourceStack> preset = Commands.literal("preset");
        for (ConfigPreset value : ConfigPreset.values()) {
            preset.then(Commands.literal(value.commandName()).executes(context -> applyPreset(context, value)));
        }

        dispatcher.register(Commands.literal("disconnectum")
                .requires(DisconnectumCommand::canUse)
                .then(Commands.literal("status").executes(context -> printStatus(context, chunkyThrottle)))
                .then(chunky)
                .then(preset));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modeCommands() {
        LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("mode");
        for (ChunkyMode value : ChunkyMode.values()) {
            mode.then(Commands.literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(context -> setMode(context, value)));
        }
        return mode;
    }

    private static boolean canUse(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        return source.getEntity() instanceof ServerPlayer player
                && source.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    private static int printStatus(CommandContext<CommandSourceStack> context, ChunkyThrottle chunkyThrottle) {
        ServerLoadSnapshot snapshot = ServerLoadMonitor.INSTANCE.snapshot();
        List<String> held = chunkyThrottle.heldWorlds();
        String line = String.format(Locale.ROOT, "Disconnectum: tick %.1f ms, lag %.1f ms, peak %.1f ms, %s%s",
                snapshot.smoothedTickMillis(), snapshot.smoothedLagMillis(), snapshot.peakTickMillis(),
                snapshot.overloaded() ? "OVERLOADED for " + snapshot.overloadedForMillis() + " ms" : "healthy",
                held.isEmpty() ? "" : ", Chunky held: " + String.join(", ", held));
        reply(context, Component.literal(line));
        return 1;
    }

    private static int continueChunky(CommandContext<CommandSourceStack> context, ChunkyThrottle chunkyThrottle) {
        int minutes = DisconnectumConfig.get().chunky().ignoreMinutes();
        chunkyThrottle.continueAndMute();
        reply(context, Component.translatableWithFallback("disconnectum.command.continue",
                "Chunky keeps running. Disconnectum stays quiet for %s minutes. Stand still to avoid loading more chunks.",
                minutes));
        return 1;
    }

    private static int stopChunky(CommandContext<CommandSourceStack> context, ChunkyThrottle chunkyThrottle) {
        int paused = chunkyThrottle.stopRunningTasks();
        reply(context, Component.translatableWithFallback("disconnectum.command.stop",
                "Paused %s Chunky task(s). Disconnectum continues them when the server recovers, or run /disconnectum chunky continue.",
                paused));
        return paused;
    }

    private static int setMode(CommandContext<CommandSourceStack> context, ChunkyMode mode) {
        try {
            DisconnectumConfig.setChunkyMode(mode);
        } catch (IllegalStateException exception) {
            return fail(context);
        }
        reply(context, Component.translatableWithFallback("disconnectum.command.mode",
                "Chunky mode is now %s (saved to the config file, no restart needed).", mode.name()));
        return 1;
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context, ConfigPreset preset) {
        try {
            DisconnectumConfig.applyPreset(preset);
        } catch (IllegalStateException exception) {
            return fail(context);
        }
        reply(context, Component.translatableWithFallback("disconnectum.command.preset",
                "Preset '%s' applied and saved. It takes effect immediately.", preset.commandName()));
        if (preset.settings().chunkyMaxWorkingCount() > 0) {
            reply(context, Component.translatableWithFallback("disconnectum.command.restart",
                    "Restart the game for the Chunky limit of %s simultaneous chunks to take effect.",
                    preset.settings().chunkyMaxWorkingCount()));
        }
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.translatableWithFallback("disconnectum.command.failed",
                "The Disconnectum config is not loaded yet; try again in a moment."));
        return 0;
    }

    private static void reply(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }
}
