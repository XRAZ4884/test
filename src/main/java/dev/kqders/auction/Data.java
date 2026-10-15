package dev.kqders.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persistent data (draft, presets, history, owner name) stored as JSON in the config folder. */
final class Data {
	private Data() {}

	static final String DEFAULT_REGEX =
		"(?i)(?<player>[A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?(?:paid|sent|gave)\\s+you\\s+\\$?(?<amount>[0-9][0-9,.]*\\s?[kmb]?)";

	static class Item {
		public String id;
		public int qty = 1;
		public long price = 1000;

		public Item() {}

		public Item(String id, int qty, long price) {
			this.id = id;
			this.qty = qty;
			this.price = price;
		}

		Item copy() {
			return new Item(id, qty, price);
		}
	}

	static class Settings {
		public List<Item> items = new ArrayList<>();
		public int duration = 60;
		public long minPayment = 5000;
		public String mode = "ALL_AT_ONCE";

		Settings copy() {
			Settings o = new Settings();
			o.assign(this);
			return o;
		}

		void assign(Settings o) {
			items = new ArrayList<>();
			for (Item i : o.items) items.add(i.copy());
			duration = o.duration;
			minPayment = o.minPayment;
			mode = o.mode;
		}

		long total() {
			long t = 0;
			for (Item i : items) t += i.price * i.qty;
			return t;
		}

		boolean oneByOne() {
			return "ONE_BY_ONE".equals(mode);
		}
	}

	static class Preset {
		public String name;
		public Settings settings = new Settings();
	}

	static class Entry {
		public long time;
		public String owner = "";
		public String status = "";
		public String winner = "";
		public long payment;
		public String mode = "ALL_AT_ONCE";
		public int duration;
		public long minPayment;
		public long listedTotal;
		public String lot = "";
		public List<Item> items = new ArrayList<>();
	}

	static class Root {
		public String owner = "KQders";
		public Settings draft = new Settings();
		public List<Preset> presets = new ArrayList<>();
		public List<Entry> history = new ArrayList<>();
		public boolean chatDetect = true;
		public boolean listenPlayerChat = false;
		public String paymentRegex = DEFAULT_REGEX;
		public float hudScale = 1.0f;
	}

	static Root root = new Root();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("kqders_auction.json");
	}

	static void load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				Root r = GSON.fromJson(Files.readString(p), Root.class);
				if (r != null) root = r;
			}
		} catch (Exception e) {
			System.err.println("[KQders Auction] Could not read config, using defaults: " + e);
		}
		if (root.owner == null || root.owner.isBlank()) root.owner = "KQders";
		if (root.draft == null) root.draft = new Settings();
		if (root.draft.items == null) root.draft.items = new ArrayList<>();
		if (root.presets == null) root.presets = new ArrayList<>();
		if (root.history == null) root.history = new ArrayList<>();
		if (root.paymentRegex == null || root.paymentRegex.isBlank()) root.paymentRegex = DEFAULT_REGEX;
		if (root.hudScale < 0.5f || root.hudScale > 2.5f) root.hudScale = 1.0f;
		if (root.draft.mode == null) root.draft.mode = "ALL_AT_ONCE";
		save();
	}

	static void save() {
		try {
			Files.writeString(path(), GSON.toJson(root));
		} catch (Exception e) {
			System.err.println("[KQders Auction] Could not save config: " + e);
		}
	}
}
