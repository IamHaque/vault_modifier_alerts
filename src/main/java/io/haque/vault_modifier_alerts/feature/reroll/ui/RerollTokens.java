package io.haque.vault_modifier_alerts.feature.reroll.ui;

import io.haque.vault_modifier_alerts.config.VmaClientConfigs;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;

/**
 * Panel-local color and spacing tokens for the auto-reroll panel.
 * Colors are themeable via config (Phase 7), with fallback defaults
 * matching the QOLHunters design token set (Design Guidelines §11).
 */
public final class RerollTokens {
	private RerollTokens() {
	}

	// Panel chrome (configurable)
	public static int PANEL_BG() { return VmaClientConfigs.panelBgColor(); }
	public static int PANEL_BORDER() { return VmaClientConfigs.panelBorderColor(); }
	public static int ACCENT_GOLD() { return VmaClientConfigs.panelAccentGoldColor(); }

	// Text (configurable)
	public static int TEXT_DEFAULT() { return VmaClientConfigs.panelTextColor(); }
	public static final int TEXT_MUTED = 0xFFA0A0A0;
	public static final int TEXT_DISABLED = 0xFF707070;

	// State (configurable)
	public static int STATE_SUCCESS() { return VmaClientConfigs.panelSuccessColor(); }
	public static int STATE_DANGER() { return VmaClientConfigs.panelDangerColor(); }
	public static final int ROW_HOVER = 0xFF3A3A3A;
	public static final int ROW_OPEN = 0xFF543C1F;

	// Input
	public static final int INPUT_BG = 0xFF2E2E2E;
	public static final int INPUT_FOCUS = 0xFF484848;

	// Button
	public static final int BUTTON_BG = 0xFF303030;
	public static final int BUTTON_DISABLED_BG = 0xFF222222;

	// Start/stop button atlas textures (host's generic 16px button)
	public static final ButtonElement.ButtonTextures START_BUTTON_TEXTURES = ScreenTextures.BUTTON_EMPTY_16_TEXTURES;

	// Dropdown
	public static final int DROPDOWN_BG = 0xF0181818;
	public static final int DROPDOWN_REMOVE_HOVER = 0xFF4A1F1F;

	// Tooltip
	public static final int TOOLTIP_BG = 0xF0101010;
}
