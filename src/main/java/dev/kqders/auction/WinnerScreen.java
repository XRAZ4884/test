package dev.kqders.auction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Where the owner records the winner. Everything here is manual confirmation:
 * chat-detected payments are only shown as suggestions you can click to fill the fields.
 */
final class WinnerScreen extends Screen {
	private TextFieldWidget nameField, payField;
	private int px, py;
	private static final int PW = 330, PH = 214;
	private String message = "";
	private int msgColor = Ui.MUTED;
	private boolean bgDrawn;

	WinnerScreen() {
		super(Text.literal("Record winner"));
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	protected void init() {
		px = (width - PW) / 2;
		py = (height - PH) / 2;

		nameField = new TextFieldWidget(textRenderer, px + 10, py + 118, 150, 16, Text.literal("Winner username"));
		nameField.setPlaceholder(Text.literal("Minecraft username"));
		nameField.setMaxLength(17);
		nameField.setText(AuctionManager.winner == null ? "" : AuctionManager.winner);
		addDrawableChild(nameField);

		payField = new TextFieldWidget(textRenderer, px + 170, py + 118, 150, 16, Text.literal("Amount paid"));
		payField.setPlaceholder(Text.literal("Amount paid, e.g. 5K"));
		payField.setMaxLength(14);
		if (AuctionManager.winnerPayment > 0) payField.setText(Money.format(AuctionManager.winnerPayment));
		addDrawableChild(payField);

		int y1 = py + 144, y2 = py + 168;
		addDrawableChild(new FlatButton(px + 10, y1, 150, 20, "SET WINNER", FlatButton.Style.PRIMARY, this::setWinner));
		addDrawableChild(new FlatButton(px + 170, y1, 68, 20, "NO WINNER", FlatButton.Style.SECONDARY, () -> {
			String err = AuctionManager.noWinner();
			if (err != null) say(err, Ui.RED);
			else say("Saved to history as No winner.", Ui.MUTED);
		}));
		addDrawableChild(new FlatButton(px + 244, y1, 76, 20, "RESET", FlatButton.Style.DANGER, this::resetAll));

		addDrawableChild(new FlatButton(px + 10, y2, 150, 20, "COPY WINNER USERNAME", FlatButton.Style.CYAN, () -> {
			if (AuctionManager.copyCurrentWinner()) {
				say("Copied " + AuctionManager.winner, Ui.GREEN);
			} else {
				say("No winner recorded yet. Press Set winner first.", Ui.RED);
			}
		}));
		addDrawableChild(new FlatButton(px + 170, y2, 68, 20, "NEXT LOT", FlatButton.Style.SECONDARY, () -> {
			if (AuctionManager.nextLot()) { close(); Ui.toast("Next lot started", "Lot " + (AuctionManager.lotIndex + 1) + "/" + AuctionManager.lots.size()); }
			else say("There is no next lot to start.", Ui.RED);
		}));
		addDrawableChild(new FlatButton(px + 244, y2, 76, 20, "CLOSE", FlatButton.Style.SECONDARY, this::close));
	}

	private void say(String t, int col) { message = t; msgColor = col; }

	private void setWinner() {
		String name = nameField.getText().trim();
		long pay = Money.parse(payField.getText());
		String err = AuctionManager.acceptWinner(name, pay);
		if (err != null) { say(err, Ui.RED); return; }
		say("Winner recorded: " + name, Ui.GREEN);
		Ui.chat("[KQders Auction] Winner: " + name + " (paid " + Money.format(pay) + ")");
		nameField.setText(name);
		setFocused(nameField);
		nameField.setFocused(true);
		nameField.setCursorToStart(false);
		nameField.setCursorToEnd(true);
	}

	private void resetAll() {
		AuctionManager.reset();
		nameField.setText("");
		payField.setText("");
		say(AuctionManager.status == AuctionManager.Status.READY ? "Winner info cleared. Ready for the next auction." : "Fields cleared.", Ui.MUTED);
	}

	@Override
	public void render(DrawContext c, int mx, int my, float d) {
		bgDrawn = false;
		renderBackground(c, mx, my, d);
		super.render(c, mx, my, d);
	}

	@Override
	public void renderBackground(DrawContext c, int mx, int my, float d) {
		if (bgDrawn) return;
		bgDrawn = true;
		c.fill(0, 0, width, height, 0xB0000000);
		Ui.panel(c, px, py, PW, PH, Ui.PANEL, Ui.BORDER);
		c.fill(px + 6, py + 7, px + 8, py + 22, Ui.ACCENT);

		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(px + 13, py + 7, 0);
		m.scale(1.3f, 1.3f, 1f);
		c.drawText(textRenderer, "Record winner", 0, 0, Ui.TEXT, true);
		m.pop();

		AuctionManager.Status st = AuctionManager.status;
		String label = st.label.toUpperCase();
		int tw = textRenderer.getWidth(label);
		Ui.round(c, px + PW - 10 - tw - 8, py + 8, tw + 8, 12, st == AuctionManager.Status.SOLD ? Ui.GREEN : (st == AuctionManager.Status.TIME_UP ? Ui.ACCENT : (st == AuctionManager.Status.LIVE ? Ui.CYAN : Ui.MUTED)));
		c.drawText(textRenderer, label, px + PW - 10 - tw - 4, py + 10, 0xFF101010, false);

		AuctionManager.Lot lot = AuctionManager.currentLot();
		if (st == AuctionManager.Status.READY || lot == null) {
			c.drawText(textRenderer, "No auction is running. Start one from the Auction Builder.", px + 12, py + 34, Ui.MUTED, false);
		} else {
			int n = Math.min(8, lot.items.size());
			for (int i = 0; i < n; i++) c.drawItem(ItemCatalog.stackFor(lot.items.get(i).id), px + 12 + i * 18, py + 30);
			if (lot.items.size() > n) c.drawText(textRenderer, "+" + (lot.items.size() - n), px + 12 + n * 18, py + 34, Ui.PURPLE, false);
			String lotTxt = AuctionManager.lots.size() > 1 ? "Lot " + (AuctionManager.lotIndex + 1) + "/" + AuctionManager.lots.size() + "   " : "";
			c.drawText(textRenderer, lotTxt + "Listed " + Money.format(lot.price) + "   Min payment " + Money.format(AuctionManager.active.minPayment), px + 12, py + 50, Ui.MUTED, false);
		}

		c.drawText(textRenderer, "PAYMENTS SPOTTED IN CHAT (verify before confirming)", px + 12, py + 64, Ui.ACCENT, false);
		List<AuctionManager.Payment> ps = AuctionManager.payments;
		if (ps.isEmpty()) {
			c.drawText(textRenderer, "None. Enter the winner manually below.", px + 12, py + 76, Ui.MUTED, false);
		} else {
			int shown = Math.min(3, ps.size());
			for (int i = 0; i < shown; i++) {
				AuctionManager.Payment p = ps.get(ps.size() - 1 - i);
				int ry = py + 75 + i * 11;
				boolean hov = Ui.in(mx, my, px + 10, ry - 1, PW - 20, 11);
				if (hov) c.fill(px + 10, ry - 1, px + PW - 10, ry + 9, Ui.ROW_HOVER);
				c.drawText(textRenderer, p.player + "  " + Money.format(p.amount), px + 14, ry, p.valid ? Ui.TEXT : Ui.MUTED, false);
				String tag = p.valid ? "meets minimum - click to fill" : "below minimum";
				c.drawText(textRenderer, tag, px + PW - 14 - textRenderer.getWidth(tag), ry, p.valid ? Ui.GREEN : Ui.RED, false);
			}
		}

		if (st == AuctionManager.Status.SOLD && !AuctionManager.winner.isEmpty()) {
			String w = "WINNER: " + AuctionManager.winner;
			m.push();
			m.translate(px + PW / 2f - textRenderer.getWidth(w) * 1.2f / 2f, py + 100, 0);
			m.scale(1.2f, 1.2f, 1f);
			c.drawText(textRenderer, w, 0, 0, Ui.CYAN, true);
			m.pop();
		}

		c.drawText(textRenderer, "Winner username", px + 10, py + 108, Ui.MUTED, false);
		c.drawText(textRenderer, "Amount paid", px + 170, py + 108, Ui.MUTED, false);

		if (!message.isEmpty())
			c.drawText(textRenderer, Ui.fit(textRenderer, message, PW - 24), px + 12, py + PH - 14, msgColor, false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int btn) {
		if (btn == 0 && !AuctionManager.payments.isEmpty()) {
			List<AuctionManager.Payment> ps = AuctionManager.payments;
			int shown = Math.min(3, ps.size());
			for (int i = 0; i < shown; i++) {
				int ry = py + 75 + i * 11;
				if (Ui.in(mx, my, px + 10, ry - 1, PW - 20, 11)) {
					AuctionManager.Payment p = ps.get(ps.size() - 1 - i);
					nameField.setText(p.player);
					payField.setText(Money.format(p.amount));
					say("Filled from chat. Check it, then press Set winner.", Ui.MUTED);
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, btn);
	}
}
