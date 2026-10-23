package me.thanhtnt2.disconnectum.optimization.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import me.thanhtnt2.disconnectum.optimization.monitor.ServerLoadMonitor;
import me.thanhtnt2.disconnectum.optimization.throttle.ChunkyThrottle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /disconnectum status}: shows what the monitor sees, handy when tuning the config. */
public final class DisconnectumCommand {

    private DisconnectumCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ChunkyThrottle chunkyThrottle) {
        dispatcher.register(Commands.literal("disconnectum")
                .then(Commands.literal("status")
                        .executes(context -> printStatus(context, chunkyThrottle))));
    }

    private static int printStatus(CommandContext<CommandSourceStack> context, ChunkyThrottle chunkyThrottle) {
        ServerLoadSnapshot snapshot = ServerLoadMonitor.INSTANCE.snapshot();
        List<String> held = chunkyThrottle.heldWorlds();
        String line = String.format("Disconnectum: tick %.1f ms, lag %.1f ms, peak %.1f ms, %s%s",
                snapshot.smoothedTickMillis(), snapshot.smoothedLagMillis(), snapshot.peakTickMillis(),
                snapshot.overloaded() ? "OVERLOADED for " + snapshot.overloadedForMillis() + " ms" : "healthy",
                held.isEmpty() ? "" : ", Chunky held: " + String.join(", ", held));
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
