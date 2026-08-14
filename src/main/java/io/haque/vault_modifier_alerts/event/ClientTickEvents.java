package io.haque.vault_modifier_alerts.event;

import io.haque.vault_modifier_alerts.VaultModifierAlerts;
import io.haque.vault_modifier_alerts.VmaReference;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.downed.DownedAlertEngine;
import io.haque.vault_modifier_alerts.feature.expiry.ExpiryAlertEngine;
import io.haque.vault_modifier_alerts.feature.reroll.AutoRerollEngine;
import io.haque.vault_modifier_alerts.feature.reroll.RerollPanel;
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
	 */
	@SubscribeEvent
	public static void onScreenKeyPressed(ScreenEvent.KeyboardKeyPressedEvent.Pre event) {
		if (!(event.getScreen() instanceof VaultArtisanStationScreen)) {
			return;
		}
		if (!KeyBindings.TOGGLE_REROLL_PANEL.matches(event.getKeyCode(), event.getScanCode())) {
			return;
		}
		RerollPanel panel = RerollPanel.getInstance();
		panel.toggleVisible();
		if (VmaClientConfigs.isDebugLogging()) {
			VaultModifierAlerts.LOGGER.debug("[VMA] Auto-reroll panel {}", panel.isVisible() ? "shown" : "hidden");
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
				panel.toggleVisible();
				if (VmaClientConfigs.isDebugLogging()) {
					VaultModifierAlerts.LOGGER.debug("[VMA] Auto-reroll panel {}",
							panel.isVisible() ? "shown" : "hidden");
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