package me.thanhtnt2.disconnectum.optimization.mixin;

import me.thanhtnt2.disconnectum.optimization.network.KeepAliveGuard;
import net.minecraft.Util;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Postpones vanilla's keep-alive timeout when it is caused by lag instead of a dead connection.
 *
 * <p>Deliberately a single {@code HEAD} inject that only adjusts a timestamp: it never cancels, redirects or
 * overwrites anything, so it stacks with other mods touching the same method. The decision lives in
 * {@link KeepAliveGuard}; this class is only the hook.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerKeepAliveMixin {

    @Shadow
    private long keepAliveTime;

    @Shadow
    private boolean keepAlivePending;

    @Shadow
    protected abstract boolean isSingleplayerOwner();

    @Inject(method = "keepConnection", at = @At("HEAD"))
    private void disconnectum$postponeLagTimeout(CallbackInfo callbackInfo) {
        if (!keepAlivePending) {
            return;
        }
        long now = Util.getMillis();
        if (now - keepAliveTime < KeepAliveGuard.VANILLA_TIMEOUT_MILLIS) {
            return;
        }
        if (KeepAliveGuard.shouldForgiveTimeout(isSingleplayerOwner())) {
            // Moving the timestamp makes vanilla's own check pass; the pending reply is still honoured later.
            keepAliveTime = now;
        }
    }
}
