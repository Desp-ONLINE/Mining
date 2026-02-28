package org.desp.mining.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningRepository;

public class MiningScheduler {

    private static final MiningRepository repository = MiningRepository.getInstance();

    private static BukkitTask bukkitTask;

    public static void startFatigueReductionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                repository.reduceFatigue();

            }
        }.runTaskTimerAsynchronously(Mining.getInstance(), 400L, 400L);
    }
    public static void closeScheduler(){
        bukkitTask.cancel();
    }

}
