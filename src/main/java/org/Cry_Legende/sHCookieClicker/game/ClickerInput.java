package org.Cry_Legende.sHCookieClicker.game;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.UUID;

/**
 * Clics sur la zone de clic du clicker. Elle n'existe que chez le joueur : le serveur ne la connaît pas, le paquet
 * s'arrête ici. Les clics arrivent sur le fil réseau et sont remis au minuteur du fil principal (ClickerDisplay).
 * Paquets rares : un seul test de type pour tous les autres.
 */
final class ClickerInput extends PacketListenerAbstract {
    private final ClickerDisplay display;

    ClickerInput(ClickerDisplay display) {
        super(PacketListenerPriority.NORMAL);
        this.display = display;
    }

    static PacketListenerCommon register(ClickerDisplay display) {
        return PacketEvents.getAPI().getEventManager().registerListener(new ClickerInput(display));
    }

    static void unregister(PacketListenerCommon listener) {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int entityId;
        boolean left;
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            // Depuis 26.1, le clic gauche sur une entité a son propre paquet
            entityId = new WrapperPlayClientAttack(event).getEntityId();
            left = true;
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            entityId = packet.getEntityId();
            left = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
            if (!left && packet.getHand() != InteractionHand.MAIN_HAND) {
                // Seule la main principale compte ; le paquet de l'autre main est tout de même bloqué
                if (event.getUser() != null && display.owns(event.getUser().getUUID(), entityId)) {
                    event.setCancelled(true);
                }
                return;
            }
        } else {
            return;
        }
        UUID uuid = event.getUser() == null ? null : event.getUser().getUUID();
        if (uuid == null || !display.owns(uuid, entityId)) {
            return;
        }
        event.setCancelled(true);
        display.click(uuid, entityId, left);
    }
}
