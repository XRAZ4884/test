package dev.kqders.auction;

import java.util.Locale;

/** Parses and formats prices such as 5K, 1.5M, 2,500 or $100. */
final class Money {
	private Money() {}

	/** Returns the parsed amount, or -1 when the text is not a valid amount. */
	static long parse(String s) {
		if (s == null) return -1;
		String t = s.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "").replace(" ", "");
		if (t.isEmpty()) return -1;
		long mult = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k') mult = 1_000L;
		else if (last == 'm') mult = 1_000_000L;
		else if (last == 'b') mult = 1_000_000_000L;
		if (mult != 1) t = t.substring(0, t.length() - 1);
		if (t.isEmpty()) return -1;
		try {
			double v = Double.parseDouble(t);
			if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return -1;
			double r = v * mult;
			if (r > 9e15) return -1;
			return Math.round(r);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String trim(double d) {
		String s = String.format(Locale.ROOT, "%.2f", d);
		s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}

	/** Short form: 5K, 1.5M, 250. Falls back to plain digits when it can't be shortened exactly. */
	static String format(long v) {
		if (v >= 1_000_000_000L && v % 10_000_000L == 0) return trim(v / 1e9) + "B";
		if (v >= 1_000_000L && v % 10_000L == 0) return trim(v / 1e6) + "M";
		if (v >= 1_000L && v % 10L == 0) return trim(v / 1e3) + "K";
		return String.valueOf(v);
	}

	static String exact(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}
}
