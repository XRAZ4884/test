package dev.kqders.auction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the state of the running auction.
 *
 * Nothing here ever picks or invents a winner. The timer reaching zero only moves the lot to TIME_UP;
 * a lot becomes SOLD only when the owner records a username and a payment that meets the minimum.
 */
final class AuctionManager {
	private AuctionManager() {}

	static final Pattern NAME = Pattern.compile("^\\.?[A-Za-z0-9_]{3,16}$");

	enum Status {
		READY("Ready"), LIVE("Live"), TIME_UP("Time up"), SOLD("Sold"), NO_WINNER("No winner"), CANCELLED("Cancelled");
		final String label;
		Status(String l) { label = l; }
	}

	static class Lot {
		final List<Data.Item> items;
		final long price;
		Lot(List<Data.Item> items) {
			this.items = items;
			long p = 0;
			for (Data.Item i : items) p += i.price * i.qty;
			this.price = p;
		}
	}

	/** A payment message spotted in chat. It is only a hint; the owner must confirm it. */
	static class Payment {
		String player;
		long amount;
		boolean valid;
	}

	static Status status = Status.READY;
	static final List<Lot> lots = new ArrayList<>();
	static int lotIndex;
	static long remainingMs;
	static boolean paused;
	static Data.Settings active = new Data.Settings();
	static String owner = "KQders";
	static Data.Entry entry;
	static String winner = "";
	static long winnerPayment;
	static final List<Payment> payments = new ArrayList<>();
	private static long lastNanos = System.nanoTime();
	private static Pattern pattern;
	private static String patternSrc;

	static boolean isActive() { return status == Status.LIVE || status == Status.TIME_UP; }
	static boolean isResolved() { return status == Status.SOLD || status == Status.NO_WINNER || status == Status.CANCELLED; }
	static Lot currentLot() { return lotIndex >= 0 && lotIndex < lots.size() ? lots.get(lotIndex) : null; }
	static boolean hasNextLot() { return (status == Status.SOLD || status == Status.NO_WINNER) && lotIndex + 1 < lots.size(); }

	static String validate(Data.Settings s, String ownerName) {
		if (s.items.isEmpty()) return "Add at least one item to sell.";
		for (Data.Item i : s.items) {
			if (i.qty < 1) return "Quantity for " + ItemCatalog.nameFor(i.id) + " must be at least 1.";
			if (i.price <= 0) return "Set a price for " + ItemCatalog.nameFor(i.id) + ".";
		}
		if (s.minPayment <= 0) return "Minimum payment must be above 0.";
		if (s.duration < 5 || s.duration > 3600) return "Duration must be between 5 and 3600 seconds.";
		if (ownerName == null || !NAME.matcher(ownerName).matches())
			return "Owner name must be a Minecraft username (3-16 letters, numbers or _).";
		return null;
	}

	/** Returns an error message, or null when the auction started. */
	static String start(Data.Settings s) {
		if (isActive()) return "An auction is already running. Cancel it first.";
		String err = validate(s, Data.root.owner);
		if (err != null) return err;
		active = s.copy();
		owner = Data.root.owner;
		lots.clear();
		if (active.oneByOne()) {
			for (Data.Item i : active.items) {
				List<Data.Item> one = new ArrayList<>();
				one.add(i.copy());
				lots.add(new Lot(one));
			}
		} else {
			lots.add(new Lot(new ArrayList<>(active.items)));
		}
		lotIndex = 0;
		beginLot();
		return null;
	}

	private static void beginLot() {
		status = Status.LIVE;
		remainingMs = active.duration * 1000L;
		paused = false;
		winner = "";
		winnerPayment = 0;
		entry = null;
		payments.clear();
		lastNanos = System.nanoTime();
		Ui.ping();
	}

	static boolean nextLot() {
		if (!hasNextLot()) return false;
		lotIndex++;
		beginLot();
		return true;
	}

	static void tick() {
		long now = System.nanoTime();
		long d = (now - lastNanos) / 1_000_000L;
		lastNanos = now;
		if (status == Status.LIVE && !paused) {
			remainingMs -= d;
			if (remainingMs <= 0) {
				remainingMs = 0;
				status = Status.TIME_UP;
				Ui.ping();
				Ui.toast("Time's up", "Record the winner (" + KQdersAuctionClient.winnerKey.getBoundKeyLocalizedText().getString() + ").");
			}
		}
	}

	static void togglePause() {
		if (status == Status.LIVE) paused = !paused;
	}

	static void cancel() {
		if (isActive()) resolve(Status.CANCELLED, "", 0);
		else if (isResolved()) reset();
	}

	static void reset() {
		if (!isResolved()) return;
		status = Status.READY;
		lots.clear();
		lotIndex = 0;
		winner = "";
		winnerPayment = 0;
		entry = null;
		payments.clear();
	}

	static String noWinner() {
		if (!isActive()) return "There is no live lot to close.";
		resolve(Status.NO_WINNER, "", 0);
		return null;
	}

	/** Records the winner. Returns an error message, or null when accepted. */
	static String acceptWinner(String name, long pay) {
		if (status == Status.READY) return "No auction is running.";
		if (status == Status.NO_WINNER || status == Status.CANCELLED) return "This lot already ended without a winner.";
		if (name == null || !NAME.matcher(name).matches())
			return "Enter a valid Minecraft username (3-16 letters, numbers or _).";
		if (pay < 0) return "Enter the amount paid, like 5K.";
		if (pay < active.minPayment)
			return "Payment of " + Money.format(pay) + " is below the minimum of " + Money.format(active.minPayment) + ". Not accepted.";
		if (status == Status.SOLD) {
			entry.winner = name;
			entry.payment = pay;
			Data.save();
			winner = name;
			winnerPayment = pay;
			return null;
		}
		resolve(Status.SOLD, name, pay);
		return null;
	}

	private static void resolve(Status st, String who, long pay) {
		Lot lot = currentLot();
		Data.Entry e = new Data.Entry();
		e.time = System.currentTimeMillis();
		e.owner = owner;
		e.status = st.label;
		e.winner = who;
		e.payment = pay;
		e.mode = active.mode;
		e.duration = active.duration;
		e.minPayment = active.minPayment;
		e.listedTotal = lot.price;
		e.lot = lots.size() > 1 ? "Lot " + (lotIndex + 1) + "/" + lots.size() : "";
		for (Data.Item i : lot.items) e.items.add(i.copy());
		Data.root.history.add(0, e);
		while (Data.root.history.size() > 500) Data.root.history.remove(Data.root.history.size() - 1);
		Data.save();
		entry = e;
		status = st;
		winner = who;
		winnerPayment = pay;
		paused = false;
	}

	private static Pattern pattern() {
		String src = Data.root.paymentRegex;
		if (pattern == null || !src.equals(patternSrc)) {
			patternSrc = src;
			try {
				pattern = Pattern.compile(src);
			} catch (Exception e) {
				pattern = null;
			}
		}
		return pattern;
	}

	/**
	 * Looks for payment messages in chat. This only suggests candidates in the Record Winner screen.
	 * It never sets a winner and never counts as payment verification.
	 */
	static void onChat(String text) {
		if (!Data.root.chatDetect || !isActive() || text == null) return;
		Pattern p = pattern();
		if (p == null) return;
		Matcher m = p.matcher(text);
		if (!m.find()) return;
		String player, amt;
		try {
			player = m.group("player");
			amt = m.group("amount");
		} catch (IllegalArgumentException e) {
			return;
		}
		if (player == null || amt == null) return;
		long a = Money.parse(amt.trim());
		if (a < 0) return;
		Payment pay = new Payment();
		pay.player = player;
		pay.amount = a;
		pay.valid = a >= active.minPayment;
		payments.add(pay);
		Ui.toast("Payment spotted in chat",
			player + " - " + Money.format(a) + (pay.valid ? ". Check it, then press Set winner." : ". Below the minimum."));
	}

	/** Copies the recorded winner of the current lot. */
	static boolean copyCurrentWinner() {
		if (winner == null || winner.isEmpty()) {
			Ui.toast("Nothing to copy", "No winner is recorded for this lot yet.");
			return false;
		}
		Ui.copy(winner);
		Ui.toast("Winner copied", winner + " is on your clipboard.");
		return true;
	}

	/** Copies the current winner, or the latest winner from history if there is none. */
	static boolean copyLatestWinner() {
		String w = winner == null ? "" : winner;
		if (w.isEmpty()) {
			for (Data.Entry e : Data.root.history) {
				if (e.winner != null && !e.winner.isEmpty()) { w = e.winner; break; }
			}
		}
		if (w.isEmpty()) {
			Ui.toast("Nothing to copy", "No winner has been recorded yet.");
			return false;
		}
		Ui.copy(w);
		Ui.toast("Winner copied", w + " is on your clipboard.");
		return true;
	}
}
