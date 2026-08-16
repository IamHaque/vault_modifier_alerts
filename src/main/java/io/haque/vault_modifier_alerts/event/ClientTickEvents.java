package io.haque.vault_modifier_alerts.event;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.VmaReference;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.downed.DownedAlertEngine;
import io.haque.vault_modifier_alerts.feature.expiry.ExpiryAlertEngine;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelElement;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanelLayout;
import io.haque.vault_modifier_alerts.tracker.ModifierTracker;
import iskallia.vault.client.gui.screen.block.VaultArtisanStationScreen;
import iskallia.vault.core.vault.ClientVaults;
import iskallia.vault.core.vault.Vault;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = VmaReference.MOD_ID, value = Dist.CLIENT)
public final class ClientTickEvents {

	private ClientTickEvents() {
	}

	/**
	 * Vanilla only routes KeyMapping clicks while no screen is open, so the panel
	 * toggle key would never fire inside the station GUI. ScreenEvent fires for
	 * every key press a screen receives, so the toggle is matched here instead.
	 * An open panel dropdown or focused min-value field gets the key first
	 * (Escape closes the dropdown, arrows scroll it, editing keys commit the
	 * field), and while the field is focused no key - including the panel
	 * toggle - may be consumed by the panel toggle path (typing P into the
	 * min-value field must not hide the panel).
	 */
	@SubscribeEvent
	public static void onScreenKeyPressed(ScreenEvent.KeyboardKeyPressedEvent.Pre event) {
		if (!(event.getScreen() instanceof VaultArtisanStationScreen)) {
			return;
		}
		RerollPanel panel = RerollPanel.getInstance();
		if (panel.onKeyPressed(event.getKeyCode())) {
			event.setCanceled(true);
			return;
		}
		if (panel.isMinInputFocused()) {
			return;
		}
		if (!KeyBindings.TOGGLE_REROLL_PANEL.matches(event.getKeyCode(), event.getScanCode())) {
			return;
		}
		panel.toggleVisible();
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Auto-reroll panel {}", panel.isVisible() ? "shown" : "hidden");
		}
	}

	/**
	 * Typed-character support for the min-value field of the auto-reroll panel.
	 * The framework does not route chars to elements we own, so this screen-level
	 * event feeds the panel's own input state (with its strict range guards).
	 */
	@SubscribeEvent
	public static void onScreenCharTyped(ScreenEvent.KeyboardCharTypedEvent.Pre event) {
		if (!(event.getScreen() instanceof VaultArtisanStationScreen)) {
			return;
		}
		RerollPanel panel = RerollPanel.getInstance();
		if (panel.isMinInputFocused() && panel.acceptChar((char) event.getCodePoint())) {
			event.setCanceled(true);
		}
	}

	/**
	 * Clicking anywhere outside the panel while a dropdown is open closes the
	 * dropdown without consuming the click (the station GUI stays interactive).
	 */
	@SubscribeEvent
	public static void onScreenMouseClicked(ScreenEvent.MouseClickedEvent.Pre event) {
		if (!(event.getScreen() instanceof VaultArtisanStationScreen)) {
			return;
		}
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isDropdownOpen()) {
			return;
		}
		RerollPanelLayout.Rect bounds = panel.bounds();
		if (bounds == null || event.getMouseX() < bounds.x() || event.getMouseX() >= bounds.x() + bounds.width()
				|| event.getMouseY() < bounds.y() || event.getMouseY() >= bounds.y() + bounds.height()) {
			panel.closeDropdown();
		}
	}

	/**
	 * Mouse-wheel scrolling for an open dropdown. Handled at screen level (the
	 * same proven path as keys/chars/mouse-clicks) so it fires exactly once even
	 * if the framework also dispatches wheel events to its own elements; the
	 * event is routed into the element tree so the host scrollbar consumes it
	 * when the list overflows.
	 */
	@SubscribeEvent
	public static void onScreenMouseScrolled(ScreenEvent.MouseScrollEvent.Pre event) {
		if (!(event.getScreen() instanceof VaultArtisanStationScreen)) {
			return;
		}
		RerollPanel panel = RerollPanel.getInstance();
		if (!panel.isDropdownOpen()) {
			return;
		}
		RerollPanelLayout.Rect bounds = panel.bounds();
		if (bounds == null || event.getMouseX() < bounds.x() || event.getMouseX() >= bounds.x() + bounds.width()
				|| event.getMouseY() < bounds.y() || event.getMouseY() >= bounds.y() + bounds.height()) {
			return;
		}
		RerollPanelElement element = RerollPanelElement.getInstance();
		if (element != null && element.onMouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		DownedAlertEngine.getInstance().evaluate();
		AutoRerollEngine.getInstance().evaluate();
		while (KeyBindings.TOGGLE_REROLL_PANEL.consumeClick()) {
			if (minecraft.screen instanceof VaultArtisanStationScreen) {
				RerollPanel panel = RerollPanel.getInstance();
				if (!panel.isMinInputFocused()) {
					panel.toggleVisible();
					if (VmaClientConfigs.isDebugLogging()) {
						VaultModifierAlerts.LOGGER.debug("[VMA] Auto-reroll panel {}",
								panel.isVisible() ? "shown" : "hidden");
					}
				}
			} else {
				minecraft.player.displayClientMessage(new TextComponent(
						"[VMA] Open the Artisan Station to toggle the auto-reroll panel"), false);
			}
		}
		if (!VmaClientConfigs.isExpiryAlertsEnabled()) {
			return;
		}
		ModifierTracker tracker = ModifierTracker.getInstance();
		Optional<Vault> active = ClientVaults.getActive();
		boolean inVaultNow = active != null && active.isPresent();
		if (inVaultNow != tracker.isInVault()) {
			tracker.resetSession(inVaultNow, VmaClientConfigs.GRACE_PERIOD_TICKS.get(), minecraft.player.tickCount);
			return;
		}
		if (!inVaultNow) {
			return;
		}
		if (minecraft.player.tickCount < tracker.getSuppressUntilTick()) {
			return;
		}
		if (!tracker.hasUnprocessedFrame()) {
			return;
		}
		ExpiryAlertEngine.getInstance().evaluate();
	}
}