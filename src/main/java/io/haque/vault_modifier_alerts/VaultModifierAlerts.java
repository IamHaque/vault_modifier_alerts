package io.haque.vault_modifier_alerts;

import com.mojang.logging.LogUtils;
import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod(VmaReference.MOD_ID)
public final class VaultModifierAlerts {

	public static final String MOD_ID = VmaReference.MOD_ID;
	public static final Logger LOGGER = LogUtils.getLogger();

	public VaultModifierAlerts() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

		DeferredRegister<SoundEvent> sounds = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
		sounds.register(VmaReference.SOUND_EVENT_ID, () -> new SoundEvent(
				ResourceLocation.tryParse(MOD_ID + ":" + VmaReference.SOUND_EVENT_ID)));
		sounds.register(modBus);

		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VmaClientConfigs.SPEC,
				MOD_ID + "-client.toml");
	}
}