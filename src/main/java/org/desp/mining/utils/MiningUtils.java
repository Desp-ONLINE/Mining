package org.desp.mining.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public class MiningUtils {

    public static String getCurrentTime() {
        Date now = new Date();
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateTime = "-";
        dateTime = dateFormatter.format(now.getTime());
        return dateTime;
    }

    public static @NotNull List<Material> getMaterials() {
        List<Material> materialList = new ArrayList<>();
        materialList.add(Material.GOLD_ORE);
        materialList.add(Material.COAL_ORE);
        materialList.add(Material.IRON_ORE);
        materialList.add(Material.DIAMOND_ORE);
        materialList.add(Material.EMERALD_ORE);
        materialList.add(Material.LAPIS_ORE);
        materialList.add(Material.REDSTONE_ORE);
        return materialList;
    }
}
