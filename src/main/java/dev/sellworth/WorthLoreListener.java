package dev.sellworth;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds "Worth: $X" (whole stack) to the tooltip of every sellable item.
 * Done at packet level, so the real items on the server are never modified.
 */
public final class WorthLoreListener {

    private WorthLoreListener() { }

    public static void register(JavaPlugin plugin, PriceManager prices) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {

            @Override
            public void onPacketSending(PacketEvent event) {
                // Creative clients send their items back to the server, which would bake the lore in.
                if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

                PacketContainer packet = event.getPacket().deepClone();
                event.setPacket(packet);

                if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                    packet.getItemModifier().write(0, decorate(prices, packet.getItemModifier().read(0)));
                } else {
                    List<ItemStack> items = packet.getItemListModifier().read(0);
                    List<ItemStack> out = new ArrayList<>(items.size());
                    for (ItemStack it : items) out.add(decorate(prices, it));
                    packet.getItemListModifier().write(0, out);

                    if (packet.getItemModifier().size() > 0) { // carried (cursor) item
                        packet.getItemModifier().write(0, decorate(prices, packet.getItemModifier().read(0)));
                    }
                }
            }
        });
    }

    private static ItemStack decorate(PriceManager prices, ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        double unit = prices.getPrice(item.getType());
        if (unit <= 0) return item;

        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return item;
        if (meta.getPersistentDataContainer().has(prices.noLoreKey)) return item; // GUI items

        double total = PriceManager.round(unit * copy.getAmount());

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("Worth: ", NamedTextColor.GRAY)
                .append(Component.text("$" + PriceManager.format(total), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }
}
