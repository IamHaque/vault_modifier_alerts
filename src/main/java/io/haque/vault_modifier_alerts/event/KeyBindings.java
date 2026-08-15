package io.haque.vault_modifier_alerts.event;

import com.mojang.blaze3d.platform.InputConstants;
import io.haque.vault_modifier_alerts.VmaReference;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = VmaReference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KeyBindings {

	public static final KeyMapping TOGGLE_REROLL_PANEL = new KeyMapping(
			"key.vault_modifier_alerts.toggle_reroll_panel",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_P,
			"key.categories.vault_modifier_alerts");

	private KeyBindings() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		ClientRegistry.registerKeyBinding(TOGGLE_REROLL_PANEL);
	}
}