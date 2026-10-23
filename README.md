# Disconnectum

NeoForge 1.21.1 mod that keeps very large modpacks from kicking you out of your own world.
Author: thanhtnt2 · License: MIT · Java 21 · NeoForge 21.1.264

* Pauses **Chunky** while the server is overloaded, continues it afterwards, and caps its parallel chunk requests.
* Lowers the priority of background worker threads (`Worker-Main-N`, `Chunky-*`).
* Postpones vanilla keep-alive timeouts that are caused by lag (server side), and answers keep-alives from the
  network thread on remote servers (client side).
* Small addon API in `me.thanhtnt2.disconnectum.optimization.api`.

Build: `./gradlew build` → `build/libs/disconnectum-<version>.jar`.
Config: `config/disconnectum-common.toml`. Diagnostics: `/disconnectum status`.
