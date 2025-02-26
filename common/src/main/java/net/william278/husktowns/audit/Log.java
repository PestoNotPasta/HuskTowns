package net.william278.husktowns.audit;

import com.google.gson.annotations.Expose;
import net.william278.husktowns.user.User;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents the audit log of actions taken in a {@link net.william278.husktowns.town.Town}
 */
public class Log {

    @Expose
    private Map<OffsetDateTime, Action> actions;

    private Log(@NotNull Map<OffsetDateTime, Action> actions) {
        this.actions = actions;
    }

    @SuppressWarnings("unused")
    private Log() {
    }

    /**
     * Create a new Log instance for a newly created town
     *
     * @param creator the creator of the town
     * @return the new {@code Log} instance.
     * <p>
     * This will contain a log with a single {@link Action.Type#CREATE_TOWN} mapped to the creator
     */
    @NotNull
    public static Log newTownLog(@NotNull User creator) {
        final Log log = new Log(new TreeMap<>());
        log.log(Action.of(creator, Action.Type.CREATE_TOWN));
        return log;
    }

    /**
     * Create a new, empty Log instance
     *
     * @return the new {@code Log} instance
     */
    @NotNull
    public static Log empty() {
        return new Log(new TreeMap<>());
    }

    /**
     * Create a new Log instance from a migrated town
     *
     * @param foundedTime the time the town was founded
     * @return the new {@code Log} instance
     * <p>
     * This will contain a log with two action entries: One, when the migrated town was originally created
     * ({@link Action.Type#CREATE_TOWN}), and another ({@link Action.Type#TOWN_DATA_MIGRATED}), when the migration occurred.
     */
    @NotNull
    public static Log migratedLog(@NotNull OffsetDateTime foundedTime) {
        final Log log = new Log(new TreeMap<>());
        log.actions.put(foundedTime, Action.of(Action.Type.CREATE_TOWN));
        log.log(Action.of(Action.Type.TOWN_DATA_MIGRATED));
        return log;
    }

    /**
     * Log an {@link Action}
     *
     * @param action the action to add to the log.
     * @apiNote The action will be logged as having occurred just now
     */
    public void log(@NotNull Action action) {
        this.actions.put(OffsetDateTime.now(), action);
    }

    /**
     * Get the map of {@link OffsetDateTime}s to their respective {@link Action}s
     *
     * @return the map of actions to when they occurred
     */
    @NotNull
    public Map<OffsetDateTime, Action> getActions() {
        return actions;
    }

    /**
     * Returns when the town was founded
     *
     * @return the {@link OffsetDateTime} of the first found {@link Action.Type#CREATE_TOWN} action
     */
    @NotNull
    public OffsetDateTime getFoundedTime() {
        return actions.entrySet().stream()
                .filter(entry -> entry.getValue().getType() == Action.Type.CREATE_TOWN)
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(OffsetDateTime.now());
    }

}
