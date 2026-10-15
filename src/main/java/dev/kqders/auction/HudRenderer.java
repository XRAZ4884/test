package dev.kqders.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** The compact live overlay drawn on the HUD. */
final class HudRenderer {
	private HudRenderer() {}

	static void render(DrawContext c, RenderTickCounter tick) {
		AuctionManager.Status st = AuctionManager.status;
		if (st == AuctionManager.Status.READY) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden) return;
		AuctionManager.Lot lot = AuctionManager.currentLot();
		if (lot == null) return;
		TextRenderer tr = mc.textRenderer;

		float sc = Data.root.hudScale;
		MatrixStack m = c.getMatrices();
		m.push();
		m.scale(sc, sc, 1f);

		final int W = 176;
		int sw = (int) (c.getScaledWindowWidth() / sc);
		int x = sw - W - 6, y = 6;
		boolean single = lot.items.size() == 1;
		int zoneH = single ? 54 : 50;
		int zoneY = y + 32;
		int rowsY = zoneY + zoneH + 5;
		int timerY = rowsY + 26;
		int footY = timerY + 22;
		int H = footY + 12 - y;

		c.fill(x + 3, y + 3, x + W + 3, y + H + 3, 0x66000000);
		Ui.panel(c, x, y, W, H, 0xF0120F1A, Ui.PURPLE);
		c.fill(x + 2, y + 1, x + W - 2, y + 2, Ui.CYAN);

		c.drawText(tr, "KQDERS AUCTION", x + 8, y + 6, Ui.CYAN, true);
		String label = st.label.toUpperCase();
		if (st == AuctionManager.Status.LIVE && AuctionManager.paused) label = "PAUSED";
		int pillCol = switch (st) {
			case LIVE -> AuctionManager.paused ? Ui.MUTED : Ui.GREEN;
			case TIME_UP -> Ui.ACCENT;
			case SOLD -> Ui.GREEN;
			case CANCELLED -> Ui.RED;
			default -> Ui.MUTED;
		};
		int tw = tr.getWidth(label);
		int pillX = x + W - 8 - tw - 8;
		Ui.round(c, pillX, y + 4, tw + 8, 12, pillCol);
		c.drawText(tr, label, pillX + 4, y + 6, 0xFF101010, false);

		// Owner name with the OWNER label directly beside it, same line, always visible.
		String name = AuctionManager.owner;
		c.drawText(tr, name, x + 8, y + 19, Ui.TEXT, true);
		int chipX = x + 8 + tr.getWidth(name) + 5;
		int chipW = tr.getWidth("[OWNER]") + 6;
		Ui.panel(c, chipX, y + 17, chipW, 12, 0xFF2A2110, Ui.ACCENT);
		c.drawText(tr, "[OWNER]", chipX + 3, y + 19, Ui.ACCENT, false);
		if (AuctionManager.lots.size() > 1) {
			String lotTxt = "Lot " + (AuctionManager.lotIndex + 1) + "/" + AuctionManager.lots.size();
			c.drawText(tr, lotTxt, x + W - 8 - tr.getWidth(lotTxt), y + 19, Ui.MUTED, false);
		}

		// Items
		if (single) {
			Data.Item it = lot.items.get(0);
			ItemStack stack = ItemCatalog.stackFor(it.id);
			Ui.bevel(c, x + 8, zoneY, 52, 52);
			m.push();
			m.translate(x + 10, zoneY + 2, 0);
			m.scale(3f, 3f, 1f);
			c.drawItem(stack, 0, 0);
			m.pop();
			int ty = zoneY + 4;
			List<OrderedText> lines = tr.wrapLines(Text.literal(ItemCatalog.nameFor(it.id)), W - 66 - 8);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				c.drawText(tr, lines.get(i), x + 66, ty, Ui.TEXT, true);
				ty += 10;
			}
			c.drawText(tr, "Qty x" + it.qty, x + 66, ty + 2, Ui.CYAN, false);
		} else {
			int n = lot.items.size();
			int shown = n > 4 ? 3 : n;
			for (int i = 0; i < shown; i++) {
				int px = x + 8 + i * 40;
				Ui.bevel(c, px, zoneY, 36, 36);
				m.push();
				m.translate(px + 2, zoneY + 2, 0);
				m.scale(2f, 2f, 1f);
				c.drawItem(ItemCatalog.stackFor(lot.items.get(i).id), 0, 0);
				m.pop();
			}
			if (n > 4) {
				int px = x + 8 + 3 * 40;
				Ui.bevel(c, px, zoneY, 36, 36);
				String more = "+" + (n - 3);
				c.drawText(tr, more, px + (36 - tr.getWidth(more)) / 2, zoneY + 14, Ui.PURPLE, true);
			}
			int totalQty = 0;
			for (Data.Item i : lot.items) totalQty += i.qty;
			c.drawText(tr, n + " items, " + totalQty + " total qty", x + 8, zoneY + 40, Ui.MUTED, false);
		}

		// Price and minimum payment
		String pl = single ? "Price" : "Bundle price";
		c.drawText(tr, pl, x + 8, rowsY, Ui.MUTED, false);
		String pv = Money.format(lot.price);
		c.drawText(tr, pv, x + W - 8 - tr.getWidth(pv), rowsY, Ui.ACCENT_HI, true);
		c.drawText(tr, "Min payment", x + 8, rowsY + 11, Ui.MUTED, false);
		String mv = Money.format(AuctionManager.active.minPayment);
		c.drawText(tr, mv, x + W - 8 - tr.getWidth(mv), rowsY + 11, Ui.CYAN, true);

		// Timer / result
		String big;
		int bigCol;
		switch (st) {
			case LIVE, TIME_UP -> {
				long secs = (AuctionManager.remainingMs + 999) / 1000;
				big = String.format("%02d:%02d", secs / 60, secs % 60);
				if (st == AuctionManager.Status.TIME_UP) bigCol = Ui.RED;
				else if (AuctionManager.paused) bigCol = Ui.MUTED;
				else bigCol = secs <= 10 ? Ui.RED : Ui.CYAN;
			}
			case SOLD -> { big = "SOLD"; bigCol = Ui.GREEN; }
			case NO_WINNER -> { big = "NO WINNER"; bigCol = Ui.MUTED; }
			default -> { big = "CANCELLED"; bigCol = Ui.RED; }
		}
		int bw = tr.getWidth(big) * 2;
		m.push();
		m.translate(x + W / 2f - bw / 2f, timerY, 0);
		m.scale(2f, 2f, 1f);
		c.drawText(tr, big, 0, 0, bigCol, true);
		m.pop();

		String foot;
		int footCol = Ui.MUTED;
		switch (st) {
			case LIVE -> foot = AuctionManager.paused ? "Timer paused" : "Pay " + AuctionManager.owner + " to buy";
			case TIME_UP -> { foot = "Time up - owner is confirming"; footCol = Ui.ACCENT; }
			case SOLD -> { foot = "Winner: " + AuctionManager.winner; footCol = Ui.CYAN; }
			case NO_WINNER -> foot = "Nobody paid the minimum";
			default -> foot = "Auction cancelled";
		}
		foot = Ui.fit(tr, foot, W - 12);
		c.drawText(tr, foot, x + (W - tr.getWidth(foot)) / 2, footY, footCol, true);

		m.pop();
	}
}
