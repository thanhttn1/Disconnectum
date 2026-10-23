package me.thanhtnt2.disconnectum.optimization.config;

/** What Disconnectum does when the server is overloaded while Chunky is generating. */
public enum ChunkyMode {
    /** Never touches Chunky on its own; shows a clickable warning and lets the player decide. */
    ASK,
    /** Pauses Chunky automatically and continues it when the server has recovered. */
    AUTO,
    /** Leaves Chunky completely alone (the concurrency cap still applies). */
    OFF
}
