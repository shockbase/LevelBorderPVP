package de.shockbase.levelborderpvp.border;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashSet;
import java.util.Set;

/** Owns round identity, time and delayed work. Access only on the server thread. */
final class RoundSession {
    private final Plugin plugin;
    private final Set<BukkitTask> tasks = new HashSet<>();
    private RoundState state = RoundState.IDLE;
    private long generation;
    private int startedAtTick;

    RoundSession(Plugin plugin) { this.plugin = plugin; }
    RoundState state() { return state; }

    void transition(RoundState next) {
        if (next == RoundState.ACTIVE && state != next) {
            startedAtTick = plugin.getServer().getCurrentTick();
        }
        state = next;
    }

    long elapsedTicks() {
        return Integer.toUnsignedLong(plugin.getServer().getCurrentTick() - startedAtTick);
    }

    BukkitTask later(Runnable action, long delay) {
        long expectedGeneration = generation;
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(holder[0]);
            if (generation == expectedGeneration) action.run();
        }, delay);
        track(holder[0]);
        return holder[0];
    }

    BukkitTask track(BukkitTask task) {
        tasks.removeIf(BukkitTask::isCancelled);
        tasks.add(task);
        return task;
    }

    void reset() {
        generation++;
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }
}
