package net.william278.husktowns.manager;

import net.kyori.adventure.text.Component;
import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.claim.Chunk;
import net.william278.husktowns.claim.ClaimWorld;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.claim.World;
import net.william278.husktowns.network.Message;
import net.william278.husktowns.network.Payload;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.user.OnlineUser;
import net.william278.husktowns.user.Preferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manager, for interfacing and editing town, claim and user data
 */
public class Manager {

    private final HuskTowns plugin;
    private final TownsManager towns;
    private final ClaimsManager claims;
    private final AdminManager admin;

    public Manager(@NotNull HuskTowns plugin) {
        this.plugin = plugin;
        this.towns = new TownsManager(plugin);
        this.claims = new ClaimsManager(plugin);
        this.admin = new AdminManager(plugin);
    }

    @NotNull
    public TownsManager towns() {
        return towns;
    }

    @NotNull
    public ClaimsManager claims() {
        return claims;
    }

    @NotNull
    public AdminManager admin() {
        return admin;
    }

    public void editTown(@NotNull OnlineUser user, @NotNull Town town, @NotNull Consumer<Town> editor) {
        editTown(user, town, editor, null);
    }

    public void editTown(@NotNull OnlineUser user, @NotNull Town town, @NotNull Consumer<Town> editor,
                         @Nullable Consumer<Town> callback) {
        plugin.runAsync(() -> {
            editor.accept(town);
            updateTownData(user, town);
            if (callback != null) {
                callback.accept(town);
            }
        });
    }

    public void memberEditTown(@NotNull OnlineUser user, @Nullable Privilege privilege,
                               @NotNull Function<Member, Boolean> editor, @Nullable Consumer<Member> callback) {
        this.ifMember(user, privilege, (member -> plugin.runAsync(() -> {
            if (editor.apply(member)) {
                updateTownData(user, member.town());
                if (callback != null) {
                    callback.accept(member);
                }
            }
        })));
    }

    public void memberEditTown(@NotNull OnlineUser user, @NotNull Privilege privilege, @NotNull Function<Member, Boolean> editor) {
        this.memberEditTown(user, privilege, editor, null);
    }

    /**
     * If the user is the mayor of a town, edit it with the given editor, update the town, then run the callback
     *
     * @param user     the user to check
     * @param editor   the editor to run
     * @param callback the callback to run
     */
    public void mayorEditTown(@NotNull OnlineUser user, @NotNull Function<Member, Boolean> editor, @Nullable Consumer<Member> callback) {
        this.ifMayor(user, (mayor -> plugin.runSync(() -> {
            if (editor.apply(mayor)) {
                plugin.runAsync(() -> {
                    updateTownData(user, mayor.town());
                    if (callback != null) {
                        callback.accept(mayor);
                    }
                });
            }
        })));
    }

    /**
     * If the user is a member of a town, and has privileges, run the callback
     *
     * @param user      the user
     * @param privilege the privilege to check for
     * @param callback  the callback to run
     */
    protected void ifMember(@NotNull OnlineUser user, @Nullable Privilege privilege, @NotNull Consumer<Member> callback) {
        final Optional<Member> member = plugin.getUserTown(user);
        if (member.isEmpty()) {
            plugin.getLocales().getLocale("error_not_in_town")
                    .ifPresent(user::sendMessage);
            return;
        }

        if (privilege != null && !member.get().hasPrivilege(plugin, privilege)) {
            plugin.getLocales().getLocale("error_insufficient_privileges", member.get().town().getName())
                    .ifPresent(user::sendMessage);
            return;
        }

        callback.accept(member.get());
    }

    /**
     * If the user is the member of a town, run the callback with the member
     *
     * @param user     The user
     * @param callback The callback
     */
    protected void ifMember(@NotNull OnlineUser user, @NotNull Consumer<Member> callback) {
        this.ifMember(user, null, callback);
    }

    /**
     * If the user is a town mayor, run the callback
     *
     * @param user     The user
     * @param callback The callback
     */
    protected void ifMayor(@NotNull OnlineUser user, @NotNull Consumer<Member> callback) {
        final Optional<Member> member = plugin.getUserTown(user);
        if (member.isEmpty()) {
            plugin.getLocales().getLocale("error_not_in_town")
                    .ifPresent(user::sendMessage);
            return;
        }

        if (!member.get().role().equals(plugin.getRoles().getMayorRole())) {
            plugin.getLocales().getLocale("error_not_town_mayor", member.get().town().getName())
                    .ifPresent(user::sendMessage);
            return;
        }

        callback.accept(member.get());
    }

    /**
     * Check if a member is the owner of the claim at a chunk in a world, and if so, run a callback
     *
     * @param member   The member to check
     * @param user     The user to send messages to
     * @param chunk    The chunk to check
     * @param world    The world to check
     * @param callback The callback to run if the member is the owner
     */
    protected void ifClaimOwner(@NotNull Member member, @NotNull OnlineUser user, @NotNull Chunk chunk,
                                @NotNull World world, @NotNull Consumer<TownClaim> callback) {
        final Optional<TownClaim> existingClaim = plugin.getClaimAt(chunk, world);
        if (existingClaim.isEmpty()) {
            plugin.getLocales().getLocale("error_chunk_not_claimed")
                    .ifPresent(user::sendMessage);
            return;
        }

        final TownClaim claim = existingClaim.get();
        final Optional<ClaimWorld> claimWorld = plugin.getClaimWorld(world);
        if (claimWorld.isEmpty()) {
            plugin.getLocales().getLocale("error_world_not_claimable")
                    .ifPresent(user::sendMessage);
            return;
        }

        final Town town = member.town();
        if (!claim.town().equals(town)) {
            plugin.getLocales().getLocale("error_chunk_claimed_by", claim.town().getName())
                    .ifPresent(user::sendMessage);
            return;
        }

        callback.accept(claim);
    }

    /**
     * Update a town's data to the database and propagate cross-server
     *
     * @param actor The user who is updating the town's data
     * @param town  The town to update
     */
    public void updateTownData(@NotNull OnlineUser actor, @NotNull Town town) {
        plugin.getDatabase().updateTown(town);
        if (plugin.getTowns().contains(town)) {
            plugin.getTowns().replaceAll(t -> t.getId() == town.getId() ? town : t);
        } else {
            plugin.getTowns().add(town);
        }
        plugin.getMessageBroker().ifPresent(broker -> Message.builder()
                .type(Message.Type.TOWN_UPDATE)
                .payload(Payload.integer(town.getId()))
                .target(Message.TARGET_ALL, Message.TargetType.SERVER)
                .build()
                .send(broker, actor));
    }

    /**
     * Send a town notification to all online users
     *
     * @param town    The town to send the notification for
     * @param message The message to send
     */
    public void sendTownNotification(@NotNull Town town, @NotNull Component message) {
        plugin.getOnlineUsers().stream()
                .filter(user -> town.getMembers().containsKey(user.getUuid()))
                .filter(user -> plugin.getUserPreferences(user.getUuid())
                        .map(Preferences::isTownNotifications).orElse(true))
                .forEach(user -> user.sendMessage(message));
    }

}
