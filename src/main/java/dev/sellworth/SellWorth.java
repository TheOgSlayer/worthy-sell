package dev.sellworth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SellWorth extends JavaPlugin {

    private PriceManager prices;
    private SellGui sellGui;
    private WorthGui worthGui;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No Vault economy provider found (install Vault + an economy plugin such as EssentialsX). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        prices = new PriceManager(this);
        prices.load();

        sellGui = new SellGui(prices, rsp.getProvider());
        worthGui = new WorthGui(this, prices);
        getServer().getPluginManager().registerEvents(sellGui, this);
        getServer().getPluginManager().registerEvents(worthGui, this);

        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            WorthLoreListener.register(this, prices);
        } else {
            getLogger().warning("ProtocolLib not found: item hover worth is disabled.");
        }
    }

    @Override
    public void onDisable() {
        // return items from any open sell menus
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof SellGui.Holder) {
                p.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "sellreload" -> {
                prices.load();
                sender.sendMessage(Component.text("prices.yml reloaded.", NamedTextColor.GREEN));
            }
            case "sell" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                sellGui.open(p);
            }
            case "worth" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                worthGui.open(p, String.join(" ", args));
            }
        }
        return true;
    }
}
