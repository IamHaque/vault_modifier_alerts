package io.haque.vault_modifier_alerts;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VaultModifierAlerts.MOD_ID)
public final class VaultModifierAlerts {

	public static final String MOD_ID = "vault_modifier_alerts";
	public static final Logger LOGGER = LogUtils.getLogger();

	private VaultModifierAlerts() {
	}
}