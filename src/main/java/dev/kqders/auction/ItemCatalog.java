package dev.kqders.auction;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every item in the game's item registry (vanilla and any other installed mods). */
final class ItemCatalog {
	private ItemCatalog() {}

	record Slot(String id, String name, String search, ItemStack stack) {}

	private static List<Slot> all;
	private static final Map<String, Slot> BY_ID = new HashMap<>();

	static List<Slot> all() {
		if (all == null) {
			List<Slot> list = new ArrayList<>();
			for (Item it : Registries.ITEM) {
				if (it == Items.AIR) continue;
				ItemStack st = new ItemStack(it);
				String id = Registries.ITEM.getId(it).toString();
				String name = st.getName().getString();
				Slot sl = new Slot(id, name, (name + " " + id).toLowerCase(Locale.ROOT), st);
				list.add(sl);
				BY_ID.put(id, sl);
			}
			list.sort(Comparator.comparing(Slot::name, String.CASE_INSENSITIVE_ORDER));
			all = list;
		}
		return all;
	}

	static Slot byId(String id) {
		all();
		return BY_ID.get(id);
	}

	static String nameFor(String id) {
		Slot s = byId(id);
		return s != null ? s.name() : id;
	}

	static ItemStack stackFor(String id) {
		Slot s = byId(id);
		return s != null ? s.stack() : new ItemStack(Items.BARRIER);
	}
}
