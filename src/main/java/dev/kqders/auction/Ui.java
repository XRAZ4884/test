package dev.kqders.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

final class Ui {
	private Ui() {}

	static final int PANEL = 0xFF16131F;
	static final int PANEL2 = 0xFF1D1A2A;
	static final int ROW = 0xFF242036;
	static final int ROW_HOVER = 0xFF2F2A48;
	static final int BORDER = 0xFF38324F;
	static final int ACCENT = 0xFFF5A623;
	static final int ACCENT_HI = 0xFFFFC14D;
	static final int PURPLE = 0xFFA878FF;
	static final int CYAN = 0xFF45E3F2;
	static final int TEXT = 0xFFECEAF5;
	static final int MUTED = 0xFF8F8AA8;
	static final int GREEN = 0xFF5CD68A;
	static final int RED = 0xFFFF5C6A;

	static void round(DrawContext c, int x, int y, int w, int h, int col) {
		c.fill(x + 1, y, x + w - 1, y + h, col);
		c.fill(x, y + 1, x + w, y + h - 1, col);
	}

	static void panel(DrawContext c, int x, int y, int w, int h, int fill, int border) {
		round(c, x, y, w, h, border);
		round(c, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** Beveled plate used behind item icons on the overlay (gives a subtle 3D look). */
	static void bevel(DrawContext c, int x, int y, int w, int h) {
		c.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
		c.fill(x, y, x + w, y + h, 0xFF0E0C14);
		c.fill(x, y, x + w, y + 1, 0xFF4A4468);
		c.fill(x, y, x + 1, y + h, 0xFF4A4468);
		c.fill(x, y + h - 1, x + w, y + h, 0xFF050408);
		c.fill(x + w - 1, y, x + w, y + h, 0xFF050408);
	}

	static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	static String fit(TextRenderer tr, String s, int maxW) {
		if (tr.getWidth(s) <= maxW) return s;
		String e = "...";
		while (s.length() > 1 && tr.getWidth(s + e) > maxW) s = s.substring(0, s.length() - 1);
		return s + e;
	}

	static int wrap(DrawContext c, TextRenderer tr, String text, int x, int y, int w, int color) {
		int yy = y;
		for (OrderedText line : tr.wrapLines(Text.literal(text), w)) {
			c.drawText(tr, line, x, yy, color, false);
			yy += 10;
		}
		return yy;
	}

	static void mini(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, int fill, int border, int text, boolean hover) {
		panel(c, x, y, w, h, hover ? lighten(fill) : fill, border);
		c.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2 + 1, text, false);
	}

	static int lighten(int argb) {
		int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		r = Math.min(255, r + 28);
		g = Math.min(255, g + 28);
		b = Math.min(255, b + 28);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	static int statusColor(String label) {
		return switch (label) {
			case "Sold" -> GREEN;
			case "Cancelled" -> RED;
			case "No winner" -> MUTED;
			case "Live" -> CYAN;
			case "Time up" -> ACCENT;
			default -> TEXT;
		};
	}

	static void toast(String title, String msg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal(title), Text.literal(msg));
	}

	static void copy(String text) {
		MinecraftClient.getInstance().keyboard.setClipboard(text);
	}

	/** Local-only chat line (never sent to the server). */
	static void chat(String s) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
	}

	static void ping() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.4f));
	}
}
