package me.thanhtnt2.disconnectum.optimization.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

/**
 * Client-side Netty handler that answers keep-alive packets on the network thread.
 *
 * <p>Vanilla answers on the render thread, so a frame that takes longer than the server's 15 s patience gets
 * the player kicked. Replying here does not depend on the render thread at all.
 *
 * <h2>Concurrency</h2>
 * {@code channelRead} always runs on the channel's own event loop, and Netty allows {@code writeAndFlush} from
 * that thread. The handler holds no state, so one instance per connection is trivially safe.
 *
 * <h2>Why the packet is swallowed</h2>
 * The server accepts exactly one reply per challenge and disconnects on a second one. Letting vanilla reply
 * as well would turn this safeguard into a guaranteed kick.
 */
public final class FastKeepAliveHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "disconnectum_fast_keepalive";

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        if (message instanceof ClientboundKeepAlivePacket keepAlive) {
            context.channel().writeAndFlush(new ServerboundKeepAlivePacket(keepAlive.getId()));
            return;
        }
        context.fireChannelRead(message);
    }
}
