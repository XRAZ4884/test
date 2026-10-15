package dev.kqders.auction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KQdersAuctionClient implements ClientModInitializer {
	public static final String MOD_ID = "kqders_auction";

	static KeyBinding openKey, toggleKey, cancelKey, copyKey, winnerKey;

	@Override
	public void onInitializeClient() {
		Data.load();
		String cat = "key.categories." + MOD_ID;
		openKey = reg("open", GLFW.GLFW_KEY_K, cat);
		toggleKey = reg("toggle", GLFW.GLFW_KEY_LEFT_BRACKET, cat);
		cancelKey = reg("cancel", GLFW.GLFW_KEY_RIGHT_BRACKET, cat);
		copyKey = reg("copy", GLFW.GLFW_KEY_BACKSLASH, cat);
		winnerKey = reg("winner", GLFW.GLFW_KEY_SEMICOLON, cat);

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		HudRenderCallback.EVENT.register(HudRenderer::render);

		// System messages (where most servers print "X paid you ...").
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) AuctionManager.onChat(message.getString());
		});
		// Player-typed chat can be faked by anyone, so it is ignored unless you turn it on in the config.
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> {
			if (Data.root.listenPlayerChat) AuctionManager.onChat(message.getString());
		});
	}

	private static KeyBinding reg(String name, int key, String cat) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + MOD_ID + "." + name, InputUtil.Type.KEYSYM, key, cat));
	}

	private void onTick(MinecraftClient mc) {
		AuctionManager.tick();

		while (openKey.wasPressed()) mc.setScreen(new BuilderScreen());

		while (toggleKey.wasPressed()) {
			if (AuctionManager.status == AuctionManager.Status.LIVE) {
				AuctionManager.togglePause();
			} else if (AuctionManager.status == AuctionManager.Status.READY) {
				String err = AuctionManager.start(Data.root.draft);
				if (err != null) Ui.toast("Can't start auction", err);
				else Ui.toast("Auction started", "Timer is running.");
			} else if (AuctionManager.status == AuctionManager.Status.TIME_UP) {
				Ui.toast("Time's up", "Record the winner with the winner key.");
			}
		}

		while (cancelKey.wasPressed()) {
			boolean wasActive = AuctionManager.isActive();
			AuctionManager.cancel();
			if (wasActive) Ui.toast("Auction cancelled", "The lot was saved to history as Cancelled.");
		}

		while (copyKey.wasPressed()) AuctionManager.copyLatestWinner();

		while (winnerKey.wasPressed()) mc.setScreen(new WinnerScreen());
	}
}
