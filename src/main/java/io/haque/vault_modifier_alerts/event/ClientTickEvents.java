package io.haque.vault_modifier_alerts.event;

import io.haque.vault_modifier_alerts.VmaReference;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.expiry.ExpiryAlertEngine;
import io.haque.vault_modifier_alerts.tracker.ModifierTracker;
import iskallia.vault.core.vault.ClientVaults;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VmaReference.MOD_ID, value = Dist.CLIENT)
public final class ClientTickEvents {

	private ClientTickEvents() {
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		if (!VmaClientConfigs.isExpiryAlertsEnabled()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		ModifierTracker tracker = ModifierTracker.getInstance();
		boolean inVaultNow = ClientVaults.getActive().isPresent();
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