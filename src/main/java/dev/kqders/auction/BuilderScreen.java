package dev.kqders.auction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Auction Builder (Sell tab) and History tab. */
final class BuilderScreen extends Screen {
	private enum Tab { SELL, HISTORY }

	private static Tab lastTab = Tab.SELL;
	private static final int ROW_H = 30, PRESET_H = 38, SLOT = 20, ENTRY_H = 28;

	private Tab tab = lastTab;
	private final Data.Settings s = Data.root.draft;

	private int px, py, pw, ph, bodyY, bodyH, footY;
	private int lx, lw, cx0, cw, rx, rw, setY;
	private int selTop, selBottom;

	private TextFieldWidget searchField, nameField, minField, durField, ownerField, priceEditor;
	private FlatButton modeBtn;

	private String search = "";
	private final List<ItemCatalog.Slot> filtered = new ArrayList<>();
	private int pickScroll, selScroll, presetScroll, histScroll, histSel, histItemScroll;
	private int editing = -1;

	private String message = "";
	private int msgColor = Ui.MUTED;
	private long msgUntil;
	private String tip;
	private boolean bgDrawn;

	private static final SimpleDateFormat FULL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final SimpleDateFormat SHORT = new SimpleDateFormat("MM-dd HH:mm");

	BuilderScreen() {
		super(Text.literal("Auction Builder"));
		refilter();
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void removed() { Data.save(); }

	// ---------------------------------------------------------------- layout / init

	private void layout() {
		pw = Math.min(width - 12, 640);
		ph = Math.min(height - 12, 330);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		bodyY = py + 32;
		footY = py + ph - 20;
		bodyH = footY - 4 - bodyY;
		int usable = pw - 24;
		lx = px + 6;
		if (tab == Tab.SELL) {
			lw = usable * 24 / 100;
			cw = usable * 34 / 100;
			rw = usable - lw - cw;
			cx0 = lx + lw + 6;
			rx = cx0 + cw + 6;
		} else {
			lw = usable * 40 / 100;
			cw = 0;
			rw = usable - lw;
			cx0 = lx + lw + 6;
			rx = cx0;
		}
		setY = bodyY + bodyH - 90;
	}

	@Override
	protected void init() {
		layout();
		editing = -1;
		Tab t = tab;

		addDrawableChild(new FlatButton(px + pw - 102, py + 7, 46, 16, "Sell",
			t == Tab.SELL ? FlatButton.Style.PRIMARY : FlatButton.Style.SECONDARY, () -> switchTab(Tab.SELL)));
		addDrawableChild(new FlatButton(px + pw - 52, py + 7, 46, 16, "History",
			t == Tab.HISTORY ? FlatButton.Style.PRIMARY : FlatButton.Style.SECONDARY, () -> switchTab(Tab.HISTORY)));

		addDrawableChild(new FlatButton(px + 6, footY, 48, 16, "Back", FlatButton.Style.SECONDARY, this::close));

		ownerField = new TextFieldWidget(textRenderer, px + 6 + 48 + 8 + 34, footY + 1, 84, 14, Text.literal("Owner"));
		ownerField.setMaxLength(16);
		ownerField.setText(Data.root.owner);
		ownerField.setChangedListener(v -> {
			if (AuctionManager.NAME.matcher(v.trim()).matches()) Data.root.owner = v.trim();
		});
		addDrawableChild(ownerField);

		if (t == Tab.SELL) initSell(); else initHistory();
	}

	private void initSell() {
		searchField = new TextFieldWidget(textRenderer, cx0 + 6, bodyY + 16, cw - 12, 14, Text.literal("Search"));
		searchField.setPlaceholder(Text.literal("Search items..."));
		searchField.setMaxLength(48);
		searchField.setText(search);
		searchField.setChangedListener(v -> { search = v; refilter(); });
		addDrawableChild(searchField);

		nameField = new TextFieldWidget(textRenderer, lx + 4, bodyY + bodyH - 32, lw - 8, 14, Text.literal("Preset name"));
		nameField.setPlaceholder(Text.literal("Preset name"));
		nameField.setMaxLength(24);
		addDrawableChild(nameField);
		addDrawableChild(new FlatButton(lx + 4, bodyY + bodyH - 16, lw - 8, 13, "Save", FlatButton.Style.CYAN, this::savePreset));

		addDrawableChild(new FlatButton(rx + rw - 6 - 46, bodyY + 3, 46, 12, "Clear all", FlatButton.Style.DANGER, () -> {
			commitEdit();
			s.items.clear();
			selScroll = 0;
			msg("Cleared all items.", Ui.MUTED);
		}));

		int fx = rx + rw - 6 - 64;
		minField = new TextFieldWidget(textRenderer, fx, setY + 2, 64, 13, Text.literal("Minimum payment"));
		minField.setMaxLength(14);
		minField.setText(Money.format(s.minPayment));
		minField.setChangedListener(v -> { long p = Money.parse(v); if (p > 0) s.minPayment = p; });
		addDrawableChild(minField);

		durField = new TextFieldWidget(textRenderer, fx, setY + 17, 64, 13, Text.literal("Duration"));
		durField.setMaxLength(5);
		durField.setText(String.valueOf(s.duration));
		durField.setChangedListener(v -> {
			try { int d = Integer.parseInt(v.trim()); if (d > 0) s.duration = d; } catch (NumberFormatException ignored) {}
		});
		addDrawableChild(durField);

		modeBtn = new FlatButton(rx + rw - 6 - 96, setY + 32, 96, 13, modeLabel(), FlatButton.Style.SECONDARY, () -> {
			s.mode = s.oneByOne() ? "ALL_AT_ONCE" : "ONE_BY_ONE";
			modeBtn.setMessage(Text.literal(modeLabel()));
		});
		addDrawableChild(modeBtn);

		addDrawableChild(new FlatButton(rx + 6, setY + 64, rw - 12, 20, "START AUCTION", FlatButton.Style.PRIMARY, this::startAuction));

		priceEditor = new TextFieldWidget(textRenderer, 0, 0, 56, 12, Text.literal("Price"));
		priceEditor.setMaxLength(14);
		priceEditor.setVisible(false);
		priceEditor.setChangedListener(v -> {
			if (editing >= 0 && editing < s.items.size()) {
				long p = Money.parse(v);
				if (p > 0) s.items.get(editing).price = p;
			}
		});
		addDrawableChild(priceEditor);
	}

	private void initHistory() {
		int bw = Math.min(150, rw - 12 - 84);
		addDrawableChild(new FlatButton(rx + 6, bodyY + bodyH - 19, bw, 14, "COPY WINNER USERNAME", FlatButton.Style.CYAN, () -> {
			List<Data.Entry> h = Data.root.history;
			if (h.isEmpty()) return;
			Data.Entry e = h.get(clamp(histSel, 0, h.size() - 1));
			if (e.winner == null || e.winner.isEmpty()) { msg("That auction has no winner to copy.", Ui.RED); return; }
			Ui.copy(e.winner);
			Ui.toast("Winner copied", e.winner + " is on your clipboard.");
			msg("Copied " + e.winner, Ui.GREEN);
		}));
		addDrawableChild(new FlatButton(rx + rw - 6 - 78, bodyY + bodyH - 19, 78, 14, "Clear history", FlatButton.Style.DANGER, () -> {
			if (Data.root.history.isEmpty()) { msg("History is already empty.", Ui.MUTED); return; }
			client.setScreen(new ConfirmScreen(yes -> {
				if (yes) { Data.root.history.clear(); Data.save(); }
				client.setScreen(new BuilderScreen());
			}, Text.literal("Clear auction history?"),
				Text.literal("This permanently deletes all " + Data.root.history.size() + " saved auctions.")));
		}));
	}

	private String modeLabel() { return s.oneByOne() ? "One item at a time" : "All at once"; }

	private void switchTab(Tab t) {
		commitEdit();
		lastTab = t;
		tab = t;
		clearAndInit();
	}

	// ---------------------------------------------------------------- helpers

	private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

	private void msg(String t, int col) {
		message = t;
		msgColor = col;
		msgUntil = System.currentTimeMillis() + 4500;
	}

	private void refilter() {
		filtered.clear();
		String q = search.toLowerCase(Locale.ROOT).trim();
		String[] toks = q.isEmpty() ? new String[0] : q.split("\\s+");
		for (ItemCatalog.Slot sl : ItemCatalog.all()) {
			boolean ok = true;
			for (String t : toks) if (!sl.search().contains(t)) { ok = false; break; }
			if (ok) filtered.add(sl);
		}
		pickScroll = 0;
	}

	private int cols() { return Math.max(1, (cw - 14) / SLOT); }
	private int gridTop() { return bodyY + 34; }
	private int gridBottom() { return bodyY + bodyH - 14; }
	private int presetTop() { return bodyY + 16; }
	private int presetBottom() { return bodyY + bodyH - 36; }

	private void addItem(ItemCatalog.Slot sl) {
		commitEdit();
		for (Data.Item i : s.items) {
			if (i.id.equals(sl.id())) { i.qty = Math.min(2304, i.qty + 1); return; }
		}
		s.items.add(new Data.Item(sl.id(), 1, 1000));
		selScroll = Math.max(0, s.items.size() * ROW_H - (selBottom - selTop));
	}

	private void commitEdit() {
		if (editing < 0 || priceEditor == null) return;
		int i = editing;
		editing = -1;
		priceEditor.setVisible(false);
		priceEditor.setFocused(false);
		if (i < s.items.size()) {
			long v = Money.parse(priceEditor.getText());
			if (v > 0) s.items.get(i).price = v;
			else msg("Price must be above 0, like 5K. Kept the old price.", Ui.RED);
		}
	}

	private void startEdit(int i, int bx, int by, int bw) {
		commitEdit();
		priceEditor.setText(Money.format(s.items.get(i).price));
		priceEditor.setX(bx);
		priceEditor.setY(by);
		priceEditor.setWidth(bw);
		editing = i;
		priceEditor.setVisible(true);
		setFocused(priceEditor);
		priceEditor.setFocused(true);
		priceEditor.setCursorToStart(false);
		priceEditor.setCursorToEnd(true);
	}

	private void loadPreset(Data.Preset p) {
		commitEdit();
		s.assign(p.settings);
		Data.save();
		selScroll = 0;
		clearAndInit();
		msg("Loaded preset \"" + p.name + "\".", Ui.GREEN);
	}

	private void savePreset() {
		commitEdit();
		String name = nameField.getText().trim();
		if (name.isEmpty()) { msg("Enter a preset name first.", Ui.RED); return; }
		if (s.items.isEmpty()) { msg("Add at least one item before saving.", Ui.RED); return; }
		long min = Money.parse(minField.getText());
		if (min > 0) s.minPayment = min;
		Data.Preset existing = null;
		for (Data.Preset p : Data.root.presets) if (p.name.equalsIgnoreCase(name)) existing = p;
		if (existing == null) {
			existing = new Data.Preset();
			existing.name = name;
			Data.root.presets.add(existing);
			msg("Saved preset \"" + name + "\".", Ui.GREEN);
		} else {
			msg("Updated preset \"" + existing.name + "\".", Ui.GREEN);
		}
		existing.settings = s.copy();
		Data.save();
	}

	private void deletePreset(Data.Preset p) {
		client.setScreen(new ConfirmScreen(yes -> {
			if (yes) { Data.root.presets.remove(p); Data.save(); }
			client.setScreen(new BuilderScreen());
		}, Text.literal("Delete preset?"), Text.literal("Delete \"" + p.name + "\"? This can't be undone.")));
	}

	private void startAuction() {
		commitEdit();
		long min = Money.parse(minField.getText());
		if (min <= 0) { msg("Minimum payment must be a number like 5K.", Ui.RED); return; }
		int dur;
		try { dur = Integer.parseInt(durField.getText().trim()); }
		catch (NumberFormatException e) { msg("Duration must be a whole number of seconds.", Ui.RED); return; }
		s.minPayment = min;
		s.duration = dur;
		String o = ownerField.getText().trim();
		if (!AuctionManager.NAME.matcher(o).matches()) { msg("Owner must be a Minecraft username.", Ui.RED); return; }
		Data.root.owner = o;
		String err = AuctionManager.start(s);
		if (err != null) { msg(err, Ui.RED); return; }
		Data.save();
		close();
		Ui.toast("Auction started", Money.format(s.total()) + " worth of items, " + dur + "s.");
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		bgDrawn = false;
		tip = null;
		renderBackground(c, mx, my, d);
		super.render(c, mx, my, d);
		if (tip != null) c.drawTooltip(textRenderer, Text.literal(tip), mx, my);
	}

	@Override
	public void renderBackground(DrawContext c, int mx, int my, float d) {
		if (bgDrawn) return;
		bgDrawn = true;
		c.fill(0, 0, width, height, 0xB0000000);
		Ui.panel(c, px, py, pw, ph, Ui.PANEL, Ui.BORDER);

		c.fill(px + 6, py + 7, px + 8, py + 26, Ui.ACCENT);
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(px + 13, py + 6, 0);
		m.scale(1.4f, 1.4f, 1f);
		c.drawText(textRenderer, "Auction Builder", 0, 0, Ui.TEXT, true);
		m.pop();
		c.drawText(textRenderer,
			tab == Tab.SELL ? "Pick items, price each one, press Start." : "Review past auctions and copy winners again.",
			px + 13, py + 20, Ui.MUTED, false);

		if (tab == Tab.SELL) {
			drawPresets(c, mx, my);
			drawPicker(c, mx, my);
			drawSelected(c, mx, my);
		} else {
			drawHistoryList(c, mx, my);
			drawHistoryDetail(c, mx, my);
		}

		c.drawText(textRenderer, "Owner", px + 6 + 48 + 8, footY + 5, Ui.MUTED, false);
		if (System.currentTimeMillis() < msgUntil) {
			int mxp = px + 6 + 48 + 8 + 34 + 84 + 10;
			c.drawText(textRenderer, Ui.fit(textRenderer, message, px + pw - 8 - mxp), mxp, footY + 5, msgColor, false);
		}
	}

	private void drawPresets(DrawContext c, int mx, int my) {
		Ui.panel(c, lx, bodyY, lw, bodyH, Ui.PANEL2, Ui.BORDER);
		c.drawText(textRenderer, "SAVED PRESETS", lx + 6, bodyY + 5, Ui.ACCENT, false);
		int top = presetTop(), bottom = presetBottom();
		List<Data.Preset> ps = Data.root.presets;
		if (ps.isEmpty()) {
			Ui.wrap(c, textRenderer, "No presets yet. Build an auction, name it below, then press Save.", lx + 6, top + 2, lw - 12, Ui.MUTED);
			return;
		}
		c.enableScissor(lx + 2, top, lx + lw - 2, bottom);
		int rowX = lx + 4, rowW = lw - 8;
		int bw = (rowW - 8 - 4) / 3;
		for (int i = 0; i < ps.size(); i++) {
			int ry = top + i * PRESET_H - presetScroll;
			if (ry + PRESET_H < top || ry > bottom) continue;
			Data.Preset p = ps.get(i);
			Ui.panel(c, rowX, ry, rowW, PRESET_H - 3, Ui.ROW, Ui.BORDER);
			c.drawText(textRenderer, Ui.fit(textRenderer, p.name, rowW - 8), rowX + 4, ry + 4, Ui.TEXT, false);
			c.drawText(textRenderer, p.settings.items.size() + " items - " + p.settings.duration + "s", rowX + 4, ry + 14, Ui.MUTED, false);
			int by = ry + 24;
			boolean inList = my >= top && my < bottom;
			Ui.mini(c, textRenderer, rowX + 4, by, bw, 11, "Start", 0xFF8A5A10, Ui.ACCENT, 0xFFFFE2A8, inList && Ui.in(mx, my, rowX + 4, by, bw, 11));
			Ui.mini(c, textRenderer, rowX + 6 + bw, by, bw, 11, "Load", 0xFF26213A, 0xFF4A4468, Ui.TEXT, inList && Ui.in(mx, my, rowX + 6 + bw, by, bw, 11));
			Ui.mini(c, textRenderer, rowX + 8 + 2 * bw, by, bw, 11, "Del", 0xFF4A1A24, 0xFFA33A4A, 0xFFFFB3BC, inList && Ui.in(mx, my, rowX + 8 + 2 * bw, by, bw, 11));
		}
		c.disableScissor();
	}

	private void drawPicker(DrawContext c, int mx, int my) {
		Ui.panel(c, cx0, bodyY, cw, bodyH, Ui.PANEL2, Ui.BORDER);
		c.drawText(textRenderer, "PICK ITEMS", cx0 + 6, bodyY + 5, Ui.ACCENT, false);
		String cnt = Money.exact(filtered.size());
		c.drawText(textRenderer, cnt, cx0 + cw - 6 - textRenderer.getWidth(cnt), bodyY + 5, Ui.MUTED, false);

		int cols = cols(), top = gridTop(), bottom = gridBottom();
		int vis = Math.max(1, (bottom - top) / SLOT);
		int total = (filtered.size() + cols - 1) / cols;
		pickScroll = clamp(pickScroll, 0, Math.max(0, total - vis));
		for (int r = 0; r < vis; r++) {
			for (int k = 0; k < cols; k++) {
				int idx = (pickScroll + r) * cols + k;
				if (idx >= filtered.size()) break;
				int x = cx0 + 6 + k * SLOT, y = top + r * SLOT;
				boolean hov = Ui.in(mx, my, x, y, SLOT - 1, SLOT - 1);
				c.fill(x, y, x + SLOT - 1, y + SLOT - 1, hov ? Ui.ROW_HOVER : Ui.ROW);
				c.drawItem(filtered.get(idx).stack(), x + 2, y + 2);
				if (hov) tip = filtered.get(idx).name();
			}
		}
		if (total > vis) {
			int trackH = vis * SLOT;
			int barH = Math.max(10, trackH * vis / total);
			int barY = top + (trackH - barH) * pickScroll / Math.max(1, total - vis);
			c.fill(cx0 + cw - 6, top, cx0 + cw - 3, top + trackH, 0xFF120F1A);
			c.fill(cx0 + cw - 6, barY, cx0 + cw - 3, barY + barH, Ui.PURPLE);
		}
		if (filtered.isEmpty()) c.drawText(textRenderer, "No items match.", cx0 + 8, top + 4, Ui.MUTED, false);
		c.drawText(textRenderer, "Click to add. Click again for more.", cx0 + 6, bodyY + bodyH - 11, Ui.MUTED, false);
	}

	private int rowX() { return rx + 4; }
	private int rowW() { return rw - 10; }

	private void drawSelected(DrawContext c, int mx, int my) {
		Ui.panel(c, rx, bodyY, rw, bodyH, Ui.PANEL2, Ui.BORDER);
		c.drawText(textRenderer, "SELLING (" + s.items.size() + ")", rx + 6, bodyY + 5, Ui.ACCENT, false);
		selTop = bodyY + 18;
		selBottom = setY - 3;
		if (s.items.isEmpty()) {
			Ui.wrap(c, textRenderer, "Nothing selected. Click items in the picker to add them.", rx + 8, selTop + 4, rw - 16, Ui.MUTED);
		}
		int total = s.items.size() * ROW_H;
		selScroll = clamp(selScroll, 0, Math.max(0, total - (selBottom - selTop)));
		c.enableScissor(rx + 2, selTop, rx + rw - 2, selBottom);
		for (int i = 0; i < s.items.size(); i++) {
			int ry = selTop + i * ROW_H - selScroll;
			if (ry + ROW_H < selTop || ry > selBottom) continue;
			Data.Item it = s.items.get(i);
			int x = rowX(), w = rowW();
			boolean hovRow = Ui.in(mx, my, x, ry, w, ROW_H - 2) && my >= selTop && my < selBottom;
			Ui.panel(c, x, ry, w, ROW_H - 2, hovRow ? Ui.ROW_HOVER : Ui.ROW, Ui.BORDER);
			c.drawItem(ItemCatalog.stackFor(it.id), x + 4, ry + 6);

			String lineTotal = "= " + Money.format(it.price * it.qty);
			int ltw = textRenderer.getWidth(lineTotal);
			String nm = ItemCatalog.nameFor(it.id);
			c.drawText(textRenderer, Ui.fit(textRenderer, nm, w - 24 - 16 - ltw), x + 24, ry + 4, Ui.TEXT, false);
			c.drawText(textRenderer, lineTotal, x + w - 14 - ltw, ry + 4, Ui.MUTED, false);
			if (hovRow && Ui.in(mx, my, x, ry, w - 14, 14)) tip = nm + " (" + it.id + ")";

			boolean inView = my >= selTop && my < selBottom;
			Ui.mini(c, textRenderer, x + w - 12, ry + 3, 9, 9, "x", 0xFF4A1A24, 0xFFA33A4A, 0xFFFFB3BC, inView && Ui.in(mx, my, x + w - 12, ry + 3, 9, 9));
			Ui.mini(c, textRenderer, x + 24, ry + 15, 10, 11, "-", 0xFF26213A, 0xFF4A4468, Ui.TEXT, inView && Ui.in(mx, my, x + 24, ry + 15, 10, 11));
			String q = String.valueOf(it.qty);
			c.drawText(textRenderer, q, x + 35 + (24 - textRenderer.getWidth(q)) / 2, ry + 17, Ui.CYAN, false);
			Ui.mini(c, textRenderer, x + 60, ry + 15, 10, 11, "+", 0xFF26213A, 0xFF4A4468, Ui.TEXT, inView && Ui.in(mx, my, x + 60, ry + 15, 10, 11));

			int bx = x + w - 4 - 56, by = ry + 15;
			boolean ed = editing == i;
			Ui.panel(c, bx, by, 56, 11, 0xFF14111C, ed ? Ui.CYAN : Ui.BORDER);
			if (!ed) c.drawText(textRenderer, Money.format(it.price), bx + 4, by + 2, it.price > 0 ? Ui.ACCENT_HI : Ui.RED, false);
			c.drawText(textRenderer, "ea", bx - 12, by + 2, Ui.MUTED, false);
		}
		c.disableScissor();

		if (total > selBottom - selTop) {
			int trackH = selBottom - selTop;
			int barH = Math.max(10, trackH * trackH / total);
			int barY = selTop + (trackH - barH) * selScroll / Math.max(1, total - trackH);
			c.fill(rx + rw - 5, selTop, rx + rw - 3, selBottom, 0xFF120F1A);
			c.fill(rx + rw - 5, barY, rx + rw - 3, barY + barH, Ui.PURPLE);
		}

		Ui.round(c, rx + 3, setY, rw - 6, 87, Ui.PANEL);
		c.drawText(textRenderer, "Minimum payment", rx + 8, setY + 5, Ui.TEXT, false);
		c.drawText(textRenderer, "Duration (sec)", rx + 8, setY + 20, Ui.TEXT, false);
		c.drawText(textRenderer, "Selling mode", rx + 8, setY + 35, Ui.TEXT, false);
		c.drawText(textRenderer, "Total worth", rx + 8, setY + 50, Ui.TEXT, false);
		String tv = Money.exact(s.total());
		c.drawText(textRenderer, tv, rx + rw - 8 - textRenderer.getWidth(tv), setY + 50, Ui.ACCENT_HI, true);
		if (Ui.in(mx, my, rx + rw - 8 - textRenderer.getWidth(tv), setY + 48, textRenderer.getWidth(tv), 12))
			tip = Money.format(s.total()) + " (price x quantity for every item)";
	}

	private void drawHistoryList(DrawContext c, int mx, int my) {
		List<Data.Entry> h = Data.root.history;
		Ui.panel(c, lx, bodyY, lw, bodyH, Ui.PANEL2, Ui.BORDER);
		c.drawText(textRenderer, "COMPLETED AUCTIONS (" + h.size() + ")", lx + 6, bodyY + 5, Ui.ACCENT, false);
		int top = bodyY + 16, bottom = bodyY + bodyH - 4;
		if (h.isEmpty()) {
			Ui.wrap(c, textRenderer, "No completed auctions yet. Finished auctions show up here.", lx + 6, top + 2, lw - 12, Ui.MUTED);
			return;
		}
		int total = h.size() * ENTRY_H;
		histScroll = clamp(histScroll, 0, Math.max(0, total - (bottom - top)));
		histSel = clamp(histSel, 0, h.size() - 1);
		c.enableScissor(lx + 2, top, lx + lw - 2, bottom);
		for (int i = 0; i < h.size(); i++) {
			int ry = top + i * ENTRY_H - histScroll;
			if (ry + ENTRY_H < top || ry > bottom) continue;
			Data.Entry e = h.get(i);
			boolean hov = Ui.in(mx, my, lx + 4, ry, lw - 8, ENTRY_H - 2) && my >= top && my < bottom;
			Ui.panel(c, lx + 4, ry, lw - 8, ENTRY_H - 2, i == histSel ? Ui.ROW_HOVER : (hov ? Ui.ROW_HOVER : Ui.ROW), i == histSel ? Ui.PURPLE : Ui.BORDER);
			c.drawText(textRenderer, SHORT.format(new Date(e.time)), lx + 8, ry + 4, Ui.MUTED, false);
			String st = e.status;
			c.drawText(textRenderer, st, lx + lw - 8 - 4 - textRenderer.getWidth(st), ry + 4, Ui.statusColor(st), false);
			String sum = summary(e);
			String who = (e.winner == null || e.winner.isEmpty()) ? "" : "  > " + e.winner;
			c.drawText(textRenderer, Ui.fit(textRenderer, sum + who, lw - 20), lx + 8, ry + 15, Ui.TEXT, false);
		}
		c.disableScissor();
	}

	private String summary(Data.Entry e) {
		if (e.items.isEmpty()) return "(no items)";
		Data.Item f = e.items.get(0);
		String s0 = f.qty + "x " + ItemCatalog.nameFor(f.id);
		return e.items.size() > 1 ? s0 + " +" + (e.items.size() - 1) : s0;
	}

	private void kv(DrawContext c, int x, int y, String k, String v, int col) {
		c.drawText(textRenderer, k, x, y, Ui.MUTED, false);
		c.drawText(textRenderer, Ui.fit(textRenderer, v, rw - 16 - 74), x + 74, y, col, false);
	}

	private void drawHistoryDetail(DrawContext c, int mx, int my) {
		Ui.panel(c, rx, bodyY, rw, bodyH, Ui.PANEL2, Ui.BORDER);
		List<Data.Entry> h = Data.root.history;
		if (h.isEmpty()) {
			c.drawText(textRenderer, "Select an auction to see its details.", rx + 8, bodyY + 8, Ui.MUTED, false);
			return;
		}
		Data.Entry e = h.get(clamp(histSel, 0, h.size() - 1));
		int x = rx + 8, y = bodyY + 6;
		kv(c, x, y, "Date", FULL.format(new Date(e.time)), Ui.TEXT); y += 11;
		kv(c, x, y, "Status", e.status + (e.lot == null || e.lot.isEmpty() ? "" : " (" + e.lot + ")"), Ui.statusColor(e.status)); y += 11;
		kv(c, x, y, "Owner", e.owner + "  [OWNER]", Ui.ACCENT); y += 11;
		boolean hasW = e.winner != null && !e.winner.isEmpty();
		kv(c, x, y, "Winner", hasW ? e.winner : "-", hasW ? Ui.CYAN : Ui.MUTED); y += 11;
		kv(c, x, y, "Paid", hasW ? Money.exact(e.payment) : "-", Ui.TEXT); y += 11;
		kv(c, x, y, "Mode", "ONE_BY_ONE".equals(e.mode) ? "One item at a time" : "All at once", Ui.TEXT); y += 11;
		kv(c, x, y, "Duration", e.duration + "s", Ui.TEXT); y += 11;
		kv(c, x, y, "Min payment", Money.exact(e.minPayment), Ui.TEXT); y += 11;
		kv(c, x, y, "Listed total", Money.exact(e.listedTotal), Ui.ACCENT_HI); y += 14;

		c.drawText(textRenderer, "ITEMS SOLD", x, y, Ui.ACCENT, false);
		int top = y + 11, bottom = bodyY + bodyH - 24;
		int total = e.items.size() * 18;
		histItemScroll = clamp(histItemScroll, 0, Math.max(0, total - (bottom - top)));
		c.enableScissor(rx + 2, top, rx + rw - 2, bottom);
		for (int i = 0; i < e.items.size(); i++) {
			int ry = top + i * 18 - histItemScroll;
			if (ry + 18 < top || ry > bottom) continue;
			Data.Item it = e.items.get(i);
			c.drawItem(ItemCatalog.stackFor(it.id), x, ry);
			String line = it.qty + "x " + ItemCatalog.nameFor(it.id) + " @ " + Money.format(it.price);
			c.drawText(textRenderer, Ui.fit(textRenderer, line, rw - 40), x + 20, ry + 4, Ui.TEXT, false);
		}
		c.disableScissor();
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (editing >= 0 && !priceEditor.isMouseOver(mx, my)) commitEdit();
		if (super.mouseClicked(mx, my, btn)) return true;
		if (btn != 0) return false;
		return tab == Tab.SELL ? clickSell(mx, my) : clickHistory(mx, my);
	}

	private boolean clickSell(double mx, double my) {
		// selected rows
		if (Ui.in(mx, my, rx, selTop, rw - 4, selBottom - selTop)) {
			for (int i = 0; i < s.items.size(); i++) {
				int ry = selTop + i * ROW_H - selScroll;
				if (ry + ROW_H < selTop || ry > selBottom) continue;
				int x = rowX(), w = rowW();
				Data.Item it = s.items.get(i);
				if (Ui.in(mx, my, x + w - 12, ry + 3, 9, 9)) {
					s.items.remove(i);
					return true;
				}
				if (Ui.in(mx, my, x + 24, ry + 15, 10, 11)) { it.qty = Math.max(1, it.qty - 1); return true; }
				if (Ui.in(mx, my, x + 60, ry + 15, 10, 11)) { it.qty = Math.min(2304, it.qty + 1); return true; }
				int bx = x + w - 4 - 56, by = ry + 15;
				if (Ui.in(mx, my, bx, by, 56, 11)) { startEdit(i, bx, by - 1, 56); return true; }
			}
		}
		// item picker
		int cols = cols(), top = gridTop(), bottom = gridBottom();
		if (Ui.in(mx, my, cx0 + 6, top, cols * SLOT, bottom - top)) {
			int k = (int) ((mx - (cx0 + 6)) / SLOT);
			int r = (int) ((my - top) / SLOT);
			int idx = (pickScroll + r) * cols + k;
			if (idx >= 0 && idx < filtered.size()) { addItem(filtered.get(idx)); return true; }
		}
		// presets
		List<Data.Preset> ps = Data.root.presets;
		int pt = presetTop(), pb = presetBottom();
		if (my >= pt && my < pb) {
			int rowX = lx + 4, rowW = lw - 8, bw = (rowW - 8 - 4) / 3;
			for (int i = 0; i < ps.size(); i++) {
				int ry = pt + i * PRESET_H - presetScroll;
				int by = ry + 24;
				if (Ui.in(mx, my, rowX + 4, by, bw, 11)) { loadPreset(ps.get(i)); startAuction(); return true; }
				if (Ui.in(mx, my, rowX + 6 + bw, by, bw, 11)) { loadPreset(ps.get(i)); return true; }
				if (Ui.in(mx, my, rowX + 8 + 2 * bw, by, bw, 11)) { deletePreset(ps.get(i)); return true; }
			}
		}
		return false;
	}

	private boolean clickHistory(double mx, double my) {
		int top = bodyY + 16, bottom = bodyY + bodyH - 4;
		if (Ui.in(mx, my, lx + 4, top, lw - 8, bottom - top)) {
			int i = (int) ((my - top + histScroll) / ENTRY_H);
			if (i >= 0 && i < Data.root.history.size()) {
				histSel = i;
				histItemScroll = 0;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		int dir = v > 0 ? -1 : 1;
		if (tab == Tab.SELL) {
			if (Ui.in(mx, my, cx0, bodyY, cw, bodyH)) {
				int total = (filtered.size() + cols() - 1) / cols();
				int vis = Math.max(1, (gridBottom() - gridTop()) / SLOT);
				pickScroll = clamp(pickScroll + dir, 0, Math.max(0, total - vis));
				return true;
			}
			if (Ui.in(mx, my, rx, bodyY, rw, setY - bodyY)) {
				commitEdit();
				selScroll = clamp(selScroll + dir * ROW_H, 0, Math.max(0, s.items.size() * ROW_H - (selBottom - selTop)));
				return true;
			}
			if (Ui.in(mx, my, lx, bodyY, lw, bodyH)) {
				presetScroll = clamp(presetScroll + dir * PRESET_H, 0, Math.max(0, Data.root.presets.size() * PRESET_H - (presetBottom() - presetTop())));
				return true;
			}
		} else {
			if (Ui.in(mx, my, lx, bodyY, lw, bodyH)) {
				histScroll = clamp(histScroll + dir * ENTRY_H, 0, Math.max(0, Data.root.history.size() * ENTRY_H - (bodyH - 20)));
				return true;
			}
			if (Ui.in(mx, my, rx, bodyY, rw, bodyH)) {
				histItemScroll = Math.max(0, histItemScroll + dir * 18);
				return true;
			}
		}
		return super.mouseScrolled(mx, my, h, v);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (editing >= 0 && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
			commitEdit();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}
}
