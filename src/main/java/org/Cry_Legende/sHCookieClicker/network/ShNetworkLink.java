package org.Cry_Legende.sHCookieClicker.network;

import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Pont vers SHNetwork, chargé seulement quand SHNetwork est activé. */
final class ShNetworkLink implements NetworkLink {
    private final SHNetworkAPI api = SHNetworkAPI.get();

    static NetworkLink open() {
        return new ShNetworkLink();
    }

    @Override
    public String serverName() {
        return api.serverName();
    }

    @Override
    public CompletableFuture<Boolean> awaitSaved(UUID uuid, Duration timeout) {
        return api.awaitSaved(uuid, timeout);
    }

    @Override
    public void registerSaveTask(Function<Player, CompletableFuture<Void>> task) {
        api.registerSaveTask("SHCookieClicker", task);
    }

    @Override
    public Optional<Remote> find(String nameOrUuid) {
        return api.find(nameOrUuid).map(player -> new Remote(player.uuid(), player.name(), player.server()));
    }

    @Override
    public void runFor(String nameOrUuid, String consoleCommand) {
        api.runFor(nameOrUuid, consoleCommand);
    }
}
