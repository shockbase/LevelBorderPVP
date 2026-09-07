package de.shockbase.levelborderpvp.border;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.IntConsumer;

/** Owns start-countdown scheduling, cancellation and completion. */
final class StartCountdownService {
    private final Plugin plugin;
    private final RoundSession session;
    private BukkitTask task;

    StartCountdownService(Plugin plugin, RoundSession session) {
        this.plugin = plugin;
        this.session = session;
    }

    void start(int seconds, IntConsumer announce, Runnable activate) {
        cancel();
        BukkitRunnable countdown = new BukkitRunnable() {
            private int remaining = seconds;

            @Override public void run() {
                if (session.state() != RoundState.COUNTDOWN) {
                    task = null;
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    task = null;
                    cancel();
                    activate.run();
                    return;
                }
                announce.accept(remaining--);
            }
        };
        task = session.track(countdown.runTaskTimer(plugin, 0L, 20L));
    }

    void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
