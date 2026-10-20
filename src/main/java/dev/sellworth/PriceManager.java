package dev.sellworth;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Loads prices.yml. The single source of truth for both /sell and /worth. */
public class PriceManager {

    private final JavaPlugin plugin;
    /** Items carrying this key already show their own worth lore, so the hover listener skips them. */
    public final NamespacedKey noLoreKey;
    private volatile Map<Material, Double> prices = Collections.emptyMap();

    public PriceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.noLoreKey = new NamespacedKey(plugin, "no_worth_lore");
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "prices.yml");
        if (!file.exists()) plugin.saveResource("prices.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<Material, Double> map = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("prices");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) {
                    plugin.getLogger().warning("prices.yml: unknown material '" + key + "'");
                    continue;
                }
                double v = sec.getDouble(key, 0);
                if (v > 0) map.put(m, v);
            }
        }
        prices = Collections.unmodifiableMap(map);
        plugin.getLogger().info("Loaded " + prices.size() + " prices.");
    }

    /** Price of one item, or 0 if it can't be sold. */
    public double getPrice(Material m) {
        return prices.getOrDefault(m, 0.0);
    }

    public Map<Material, Double> all() {
        return prices;
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String format(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    /** ENDER_PEARL -> Ender Pearl */
    public static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
