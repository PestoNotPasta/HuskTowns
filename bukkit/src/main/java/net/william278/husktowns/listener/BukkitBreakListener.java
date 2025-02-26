package net.william278.husktowns.listener;

import net.william278.husktowns.claim.Position;
import net.william278.husktowns.user.BukkitUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface BukkitBreakListener extends BukkitListener {

    @EventHandler(ignoreCancelled = true)
    default void onPlayerBreakBlock(@NotNull BlockBreakEvent e) {
        if (getListener().handler().cancelOperation(Operation.of(
                BukkitUser.adapt(e.getPlayer()),
                getPlugin().getSpecialTypes().isFarmBlock(e.getBlock().getType().getKey().toString())
                        ? Operation.Type.FARM_BLOCK_BREAK : Operation.Type.BLOCK_BREAK,
                getPosition(e.getBlock().getLocation()))
        )) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    default void onPlayerFillBucket(@NotNull PlayerBucketFillEvent e) {
        if (getListener().handler().cancelOperation(Operation.of(
                BukkitUser.adapt(e.getPlayer()),
                Operation.Type.FILL_BUCKET,
                getPosition(e.getBlockClicked().getLocation()))
        )) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    default void onPlayerBreakHangingEntity(@NotNull HangingBreakByEntityEvent e) {
        switch (e.getCause()) {
            case ENTITY -> {
                if (e.getRemover() == null) {
                    return;
                }

                final Optional<Player> player = getPlayerSource(e.getRemover());
                if (player.isPresent()) {
                    if (getListener().handler().cancelOperation(Operation.of(
                            BukkitUser.adapt(player.get()),
                            Operation.Type.BREAK_HANGING_ENTITY,
                            getPosition(e.getEntity().getLocation())
                    ))) {
                        e.setCancelled(true);
                    }
                    return;
                }
                final Position damaged = getPosition(e.getEntity().getLocation());
                final Position damaging = getPosition(e.getRemover().getLocation());
                if (getListener().handler().cancelNature(damaged.getChunk(), damaging.getChunk(), damaging.getWorld())) {
                    e.setCancelled(true);
                }
            }
            case EXPLOSION -> {
                if (getListener().handler().cancelOperation(Operation.of(
                        Operation.Type.EXPLOSION_DAMAGE_TERRAIN,
                        getPosition(e.getEntity().getLocation())
                ))) {
                    e.setCancelled(true);
                }
            }
        }
    }

}
