package dev.sellworth;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class WorthGui implements Listener {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45, SLOT_SEARCH = 47, SLOT_CLOSE = 49, SLOT_SORT = 51, SLOT_NEXT = 53;

    public enum Sort {
        ALPHABETICAL("Alphabetical (A-Z)"),
        HIGH_LOW("Highest to Lowest"),
        LOW_HIGH("Lowest to Highest");

        final String label;
        Sort(String label) { this.label = label; }
        Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static class Holder implements InventoryHolder {
        Inventory inv;
        String search = "";
        Sort sort = Sort.ALPHABETICAL;
        int page = 0;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final JavaPlugin plugin;
    private final PriceManager prices;
    /** Players currently typing a search in chat -> the menu state to return to. */
    private final Map<UUID, Holder> awaitingSearch = new HashMap<>();

    public WorthGui(JavaPlugin plugin, PriceManager prices) {
        this.plugin = plugin;
        this.prices = prices;
    }

    // ------------------------------------------------------------------ open / render

    public void open(Player p, String search) {
        Holder h = new Holder();
        h.search = search == null ? "" : search;
        openHolder(p, h);
    }

    private void openHolder(Player p, Holder state) {
        Holder h = new Holder();
        h.search = state.search;
        h.sort = state.sort;
        h.page = state.page;
        h.inv = Bukkit.createInventory(h, 54, Component.text("Item Worth", NamedTextColor.DARK_GREEN));
        render(h);
        p.openInventory(h.inv);
    }

    private void render(Holder h) {
        String q = h.search.toLowerCase(Locale.ROOT).replace('_', ' ').trim();

        List<Map.Entry<Material, Double>> list = new ArrayList<>();
        for (Map.Entry<Material, Double> en : prices.all().entrySet()) {
            if (!en.getKey().isItem()) continue;
            if (!q.isEmpty() && !PriceManager.pretty(en.getKey()).toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(en);
        }

        Comparator<Map.Entry<Material, Double>> byName =
                Comparator.comparing(en -> PriceManager.pretty(en.getKey()));
        switch (h.sort) {
            case ALPHABETICAL -> list.sort(byName);
            case HIGH_LOW -> list.sort(Comparator.<Map.Entry<Material, Double>>comparingDouble(Map.Entry::getValue)
                    .reversed().thenComparing(byName));
            case LOW_HIGH -> list.sort(Comparator.<Map.Entry<Material, Double>>comparingDouble(Map.Entry::getValue)
                    .thenComparing(byName));
        }

        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        h.page = Math.max(0, Math.min(h.page, pages - 1));

        Inventory inv = h.inv;
        inv.clear();

        int start = h.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            Map.Entry<Material, Double> en = list.get(start + i);
            ItemStack it = new ItemStack(en.getKey());
            ItemMeta meta = it.getItemMeta();
            meta.lore(List.of(
                    Component.text("Worth: ", NamedTextColor.GRAY)
                            .append(Component.text("$" + PriceManager.format(en.getValue()), NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(prices.noLoreKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
            inv.setItem(i, it);
        }

        // bottom bar
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);

        inv.setItem(SLOT_PREV, button(Material.ARROW,
                Component.text("Previous Page", NamedTextColor.YELLOW),
                Component.text("Page " + (h.page + 1) + " / " + pages, NamedTextColor.GRAY)));

        inv.setItem(SLOT_SEARCH, button(Material.OAK_SIGN,
                Component.text("Search", NamedTextColor.AQUA),
                Component.text("Current: " + (h.search.isEmpty() ? "none" : h.search), NamedTextColor.GRAY),
                Component.text("Left-click: type a name in chat", NamedTextColor.DARK_GRAY),
                Component.text("Right-click: clear search", NamedTextColor.DARK_GRAY)));

        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, Component.text("Close", NamedTextColor.RED)));

        inv.setItem(SLOT_SORT, button(Material.HOPPER,
                Component.text("Sort", NamedTextColor.GOLD),
                Component.text("Mode: " + h.sort.label, NamedTextColor.GRAY),
                Component.text("Click to change", NamedTextColor.DARK_GRAY)));

        inv.setItem(SLOT_NEXT, button(Material.ARROW,
                Component.text("Next Page", NamedTextColor.YELLOW),
                Component.text("Page " + (h.page + 1) + " / " + pages, NamedTextColor.GRAY)));
    }

    private ItemStack button(Material mat, Component name, Component... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (Component c : lore) l.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        meta.getPersistentDataContainer().set(prices.noLoreKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true); // nothing can be taken or inserted
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        switch (e.getSlot()) {
            case SLOT_PREV -> {
                h.page--;
                render(h);
                click(p);
            }
            case SLOT_NEXT -> {
                h.page++;
                render(h);
                click(p);
            }
            case SLOT_SORT -> {
                h.sort = h.sort.next();
                h.page = 0;
                render(h);
                click(p);
            }
            case SLOT_SEARCH -> {
                if (e.getClick() == ClickType.RIGHT) {
                    h.search = "";
                    h.page = 0;
                    render(h);
                    click(p);
                } else {
                    awaitingSearch.put(p.getUniqueId(), h);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.closeInventory();
                        p.sendMessage(Component.text("Type an item name in chat to search ", NamedTextColor.YELLOW)
                                .append(Component.text("(or type 'cancel')", NamedTextColor.GRAY)));
                    });
                }
            }
            case SLOT_CLOSE -> Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            default -> { }
        }
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ chat search

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Holder state;
        synchronized (awaitingSearch) {
            state = awaitingSearch.remove(p.getUniqueId());
        }
        if (state == null) return;

        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!text.equalsIgnoreCase("cancel")) {
                state.search = text;
                state.page = 0;
            }
            openHolder(p, state);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingSearch.remove(e.getPlayer().getUniqueId());
    }
}
