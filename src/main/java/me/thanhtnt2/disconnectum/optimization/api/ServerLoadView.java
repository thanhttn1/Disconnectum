package me.thanhtnt2.disconnectum.optimization.api;

/** Read-only access to the server load measured by Disconnectum. */
public interface ServerLoadView {

    /** Returns the latest published snapshot; callable from any thread. */
    ServerLoadSnapshot snapshot();

    default boolean isOverloaded() {
        return snapshot().overloaded();
    }
}
