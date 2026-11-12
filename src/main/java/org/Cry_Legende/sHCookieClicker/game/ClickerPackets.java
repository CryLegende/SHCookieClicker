package org.Cry_Legende.sHCookieClicker.game;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Paquets de l'objet du clicker : une entité d'affichage (item display pour une tête, block display pour un bloc)
 * et sa zone de clic (une entité interaction, invisible), qui n'existent que chez le joueur qui les reçoit : le
 * serveur n'en garde aucune. Avec ClickerInput, les seules classes qui touchent à packetevents.
 */
final class ClickerPackets {

    // Métadonnées (26.3) : drapeaux de l'entité, puis celles d'un display, d'un item display et d'un block display
    private static final int INTERPOLATION_DELAY = 8;
    private static final int TRANSFORMATION_DURATION = 9;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int LEFT_ROTATION = 13;
    // Interaction (depuis 1.19.4) : largeur, hauteur, réponse au clic
    private static final int WIDTH = 8;
    private static final int HEIGHT = 9;
    private static final int RESPONSIVE = 10;
    private static final int ITEM = 23;
    private static final int ITEM_CONTEXT = 24;
    private static final int BLOCK_STATE = 23;

    /** Contexte d'affichage « fixed » : la tête est centrée sur l'origine de l'entité. */
    private static final byte CONTEXT_FIXED = 8;

    private ClickerPackets() {
    }

    static int newEntityId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    /** Montre l'objet au joueur, à la pose donnée, centré en (x, y, z). */
    static void spawn(Player viewer, int entityId, double x, double y, double z, Look look, Pose.Frame frame) {
        List<EntityData<?>> data = new ArrayList<>(8);
        data.add(new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(TRANSFORMATION_DURATION, EntityDataTypes.INT, 0));
        pose(data, frame);
        if (look.isBlock()) {
            data.add(new EntityData<>(BLOCK_STATE, EntityDataTypes.BLOCK_STATE,
                    SpigotConversionUtil.fromBukkitBlockData(look.block().createBlockData()).getGlobalId()));
        } else {
            data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(head(look.profile()))));
            data.add(new EntityData<>(ITEM_CONTEXT, EntityDataTypes.BYTE, CONTEXT_FIXED));
        }
        send(viewer, new WrapperPlayServerSpawnEntity(entityId, Optional.of(UUID.randomUUID()),
                look.isBlock() ? EntityTypes.BLOCK_DISPLAY : EntityTypes.ITEM_DISPLAY,
                new Vector3d(x, y, z), 0.0f, 0.0f, 0.0f, 0, Optional.empty()));
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    /** Nouvelle pose, atteinte en {@code ticks} ticks : le client lisse le mouvement. */
    static void move(Player viewer, int entityId, Pose.Frame frame, int ticks) {
        List<EntityData<?>> data = new ArrayList<>(5);
        data.add(new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(TRANSFORMATION_DURATION, EntityDataTypes.INT, ticks));
        pose(data, frame);
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    /** Zone de clic : une entité interaction invisible, posée sur le sol de la zone (x, y, z : centre du pied). */
    static void spawnHitbox(Player viewer, int entityId, double x, double y, double z, float width, float height) {
        send(viewer, new WrapperPlayServerSpawnEntity(entityId, Optional.of(UUID.randomUUID()), EntityTypes.INTERACTION,
                new Vector3d(x, y, z), 0.0f, 0.0f, 0.0f, 0, Optional.empty()));
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, List.of(
                new EntityData<>(WIDTH, EntityDataTypes.FLOAT, width),
                new EntityData<>(HEIGHT, EntityDataTypes.FLOAT, height),
                new EntityData<>(RESPONSIVE, EntityDataTypes.BOOLEAN, true))));
    }

    static void destroy(Player viewer, int... entityIds) {
        send(viewer, new WrapperPlayServerDestroyEntities(entityIds));
    }

    private static void pose(List<EntityData<?>> data, Pose.Frame frame) {
        data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(frame.tx(), frame.ty(), frame.tz())));
        data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(frame.scale(), frame.scale(), frame.scale())));
        data.add(new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, new Quaternion4f(0, frame.qy(), 0, frame.qw())));
    }

    private static ItemStack head(PlayerProfile profile) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private static void send(Player viewer, PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, packet);
        } catch (RuntimeException ignored) {
            // joueur en cours de déconnexion
        }
    }
}
