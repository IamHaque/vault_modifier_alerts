package io.haque.vault_modifier_alerts.feature.reroll.ui;

import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.client.gui.framework.element.ButtonElement;

/**
 * Panel-local color and spacing tokens for the auto-reroll panel.
 * Maps onto the shared QOLHunters design token set (Design Guidelines §11).
 * These are the reroll-specific semantic names; the underlying values match
 * the host framework's conventions for consistent visual weight.
 *
 * <p>TODO: replace drawPanelFrame()'s GuiComponent.fill() calls with a 9-slice
 * atlas panel texture once a PNG asset is available at
 * {@code vault_modifier_alerts:gui/panel/reroll_bg}.</p>
 */
public final class RerollTokens {
	private RerollTokens() {
	}

	// Panel chrome
	public static final int PANEL_BG = 0xEE111111;
	public static final int PANEL_BORDER = 0xFF6B6B6B;
	public static final int ACCENT_GOLD = 0xFFE3C38C;

	// Text
	public static final int TEXT_DEFAULT = 0xFFFFFFFF;
	public static final int TEXT_MUTED = 0xFFA0A0A0;
	public static final int TEXT_DISABLED = 0xFF707070;

	// State
	public static final int STATE_SUCCESS = 0xFF55FF55;
	public static final int STATE_DANGER = 0xFFFF5555;
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

	// Spacing (Design Guidelines §11)
	public static final int SPACING_TIGHT = 3;
	public static final int SPACING_DEFAULT = 5;
	public static final int SPACING_LOOSE = 7;

	// Alpha convention (state intensity — Design Guidelines §11)
	public static final int ALPHA_CONFIRMED = 0x64;
	public static final int ALPHA_TRANSIENT = 0x40;
}
