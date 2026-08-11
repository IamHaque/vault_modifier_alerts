package io.haque.vault_modifier_alerts.command;

import com.mojang.brigadier.Command;
import io.haque.vault_modifier_alerts.VmaReference;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import io.haque.vault_modifier_alerts.feature.order.ModifierOrdering;
import io.haque.vault_modifier_alerts.tracker.ModifierTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = VmaReference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VmaClientCommands {

	private VmaClientCommands() {
	}

	@SubscribeEvent
	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("vma")
				.then(Commands.literal("debug")
						.then(Commands.literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
						.then(Commands.literal("off").executes(ctx -> setDebug(ctx.getSource(), false))))
				.then(Commands.literal("sound")
						.then(Commands.literal("on").executes(ctx -> setSound(ctx.getSource(), true)))
						.then(Commands.literal("off").executes(ctx -> setSound(ctx.getSource(), false))))
				.then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
	}

	private static int setDebug(CommandSourceStack source, boolean value) {
		VmaClientConfigs.setDebugLogging(value);
		source.sendSuccess(new TextComponent("[VMA] Debug logging " + (value ? "enabled" : "disabled") + " (persists in config)"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int setSound(CommandSourceStack source, boolean value) {
		VmaClientConfigs.setAlertSoundEnabled(value);
		source.sendSuccess(new TextComponent("[VMA] Expiry sounds " + (value ? "enabled" : "disabled") + " (persists in config)"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int status(CommandSourceStack source) {
		ModifierTracker tracker = ModifierTracker.getInstance();
		for (String line : statusLines(tracker)) {
			source.sendSuccess(new TextComponent(line), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static List<String> statusLines(ModifierTracker tracker) {
		List<String> lines = new ArrayList<>();
		lines.add("[VMA] debug: " + (VmaClientConfigs.isDebugLogging() ? "on" : "off")
				+ ", sounds: " + (VmaClientConfigs.isAlertSoundEnabled() ? "on" : "off")
				+ ", hud ordering: " + (VmaClientConfigs.isHudOrderingEnabled() ? "on" : "off"));
		if (VmaClientConfigs.isHudOrderingEnabled() && ModifierOrdering.getLastOrdered() != null) {
			lines.add("[VMA] HUD order (first -> last): " + String.join(", ", ModifierOrdering.getLastOrdered()));
		}
		lines.add("[VMA] in vault: " + tracker.isInVault()
				+ ", frames: " + tracker.getGeneration());
		for (String watchedId : VmaClientConfigs.watchedModifiers()) {
			ResourceLocation id = ResourceLocation.tryParse(watchedId);
			if (id == null) {
				continue;
			}
			Integer timeLeft = tracker.getLastSnapshot().get(id);
			String sound = VmaClientConfigs.resolveSoundEventId(id);
			lines.add("[VMA] watched " + watchedId + ": " + (timeLeft == null ? "inactive" : timeLeft + " ticks left")
					+ ", fired: " + tracker.isFired(id)
					+ ", sound: " + (sound == null ? "NONE (no soundOverrides entry)" : sound));
		}
		return lines;
	}
}