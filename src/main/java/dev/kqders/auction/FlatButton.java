package dev.kqders.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class FlatButton extends ButtonWidget {
	enum Style { PRIMARY, SECONDARY, DANGER, CYAN }

	Style style;

	FlatButton(int x, int y, int w, int h, String label, Style style, Runnable onClick) {
		super(x, y, w, h, Text.literal(label), b -> onClick.run(), DEFAULT_NARRATION_SUPPLIER);
		this.style = style;
	}

	@Override
	public void renderWidget(DrawContext c, int mx, int my, float delta) {
		boolean hov = isHovered();
		int fill, border, text;
		switch (style) {
			case PRIMARY -> { fill = hov ? 0xFFFFB93A : 0xFFF5A623; border = 0xFFFFD27A; text = 0xFF1B1200; }
			case DANGER -> { fill = hov ? 0xFF6A2430 : 0xFF4A1A24; border = 0xFFA33A4A; text = 0xFFFFB3BC; }
			case CYAN -> { fill = hov ? 0xFF1F5560 : 0xFF163F48; border = 0xFF45E3F2; text = 0xFFB8F6FC; }
			default -> { fill = hov ? 0xFF332E4C : 0xFF26213A; border = 0xFF4A4468; text = 0xFFECEAF5; }
		}
		Ui.panel(c, getX(), getY(), getWidth(), getHeight(), fill, border);
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		Text label = getMessage();
		c.drawText(tr, label, getX() + (getWidth() - tr.getWidth(label)) / 2, getY() + (getHeight() - 8) / 2 + 1, text, false);
	}
}
