package me.thanhtnt2.disconnectum.optimization.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalChannel;

import me.thanhtnt2.disconnectum.optimization.Disconnectum;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import me.thanhtnt2.disconnectum.optimization.network.FastKeepAliveHandler;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = Disconnectum.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Disconnectum.MOD_ID, value = Dist.CLIENT)
public final class DisconnectumClient {

    private static final String VANILLA_HANDLER_NAME = "packet_handler";

    public DisconnectumClient(ModContainer container) {
        // Adds a "Config" button in the Mods screen, generated from the ModConfigSpec.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        DisconnectumConfig.Settings settings = DisconnectumConfig.get();
        if (!settings.enabled() || !settings.keepAlive().clientFastReply()) {
            return;
        }
        Connection connection = event.getConnection();
        if (connection == null) {
            return;
        }
        Channel channel = connection.channel();
        // Singleplayer uses an in-memory channel and is covered by the server-side keep-alive mixin.
        if (channel instanceof LocalChannel) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(FastKeepAliveHandler.HANDLER_NAME) != null) {
            return;
        }
        // Must sit in front of vanilla's handler, otherwise vanilla would see (and answer) the packet first.
        if (pipeline.get(VANILLA_HANDLER_NAME) != null) {
            pipeline.addBefore(VANILLA_HANDLER_NAME, FastKeepAliveHandler.HANDLER_NAME, new FastKeepAliveHandler());
        } else {
            pipeline.addLast(FastKeepAliveHandler.HANDLER_NAME, new FastKeepAliveHandler());
        }
    }
}
