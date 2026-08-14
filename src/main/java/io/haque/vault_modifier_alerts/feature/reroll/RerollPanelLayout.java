package io.haque.vault_modifier_alerts.feature.reroll;

/**
 * Single source of truth for the auto-reroll panel geometry. Every row, button
 * and dropdown item is computed here exactly once per frame, and both the
 * renderer and the click/scroll routing ask this layout where things are, so
 * the draw path and the input path can never drift apart.
 */
public final class RerollPanelLayout {

	public static final int WIDTH = 200;
	public static final int PAD_X = 6;

	public static final int TITLE_H = 12;
	public static final int ROW_H = 11;
	public static final int BUTTON_H = 12;
	public static final int DROPDOWN_HEADER_H = 11;
	public static final int DROPDOWN_ITEM_H = 11;
	public static final int BOTTOM_PAD = 4;

	public enum HitType {
		NONE, FOCUS_ROW, MODIFIER_ROW, MIN_DEC, MIN_FIELD, MIN_INC, REROLL_TOGGLE, RESET_TOGGLE, START_BUTTON,
		DROPDOWN_UP, DROPDOWN_DOWN, DROPDOWN_ITEM
	}

	public record Hit(HitType type, int index) {
		public static final Hit MISS = new Hit(HitType.NONE, -1);
	}

	public final int x;
	public final int y;
	public final int baseHeight;
	public final int dropdownVisibleItems;
	public final int dropdownHeight;
	public final int totalHeight;

	public final int titleBottom;
	public final int focusY;
	public final int modifierY;
	public final int minY;
	public final int rangeY;
	public final int potentialY;
	public final int rerollToggleY;
	public final int resetToggleY;
	public final int buttonY;
	public final int statusY;
	public final int dropdownY;
	public final int dropdownEnd;

	public RerollPanelLayout(int x, int y, boolean dropdownOpen, int dropdownCount, int dropdownScroll,
			int maxDropdownRows) {
		this.x = x;
		this.y = y;

		titleBottom = y + TITLE_H;
		focusY = titleBottom;
		modifierY = focusY + ROW_H;
		minY = modifierY + ROW_H;
		rangeY = minY + ROW_H;
		potentialY = rangeY + ROW_H;
		rerollToggleY = potentialY + ROW_H;
		resetToggleY = rerollToggleY + ROW_H;
		buttonY = resetToggleY + ROW_H;
		statusY = buttonY + BUTTON_H;
		baseHeight = statusY + ROW_H + BOTTOM_PAD - y;

		int visible = 0;
		if (dropdownOpen && dropdownCount > 0) {
			visible = Math.min(Math.max(1, maxDropdownRows), dropdownCount);
		}
		dropdownVisibleItems = visible;
		dropdownHeight = visible == 0 ? 0 : DROPDOWN_HEADER_H + visible * DROPDOWN_ITEM_H;
		dropdownY = y + baseHeight;
		dropdownEnd = dropdownY + dropdownHeight;
		totalHeight = baseHeight + dropdownHeight;
	}

	public int minFieldLeft() {
		return x + 42;
	}

	public int minFieldRight() {
		return x + 176;
	}

	public record Rect(int x, int y, int width, int height) {
	}

	/** The visible dropdown item rect at the given visible-slot index, or null when no dropdown is open. */
	public Rect dropdownItem(int visibleIndex) {
		if (dropdownVisibleItems == 0 || visibleIndex < 0 || visibleIndex >= dropdownVisibleItems) {
			return null;
		}
		return new Rect(x, dropdownY + DROPDOWN_HEADER_H + visibleIndex * DROPDOWN_ITEM_H, WIDTH, DROPDOWN_ITEM_H);
	}

	/**
	 * Maps a screen-space point to the region under it. The dropdown index is
	 * resolved against the currently visible window, so callers must combine
	 * {@code hit.index()} with {@link #dropdownVisibleItems}.
	 */
	public Hit regionAt(int mouseX, int mouseY) {
		if (mouseY < y || mouseY >= dropdownEnd) {
			return Hit.MISS;
		}
		if (mouseY >= dropdownY) {
			if (mouseY < dropdownY + DROPDOWN_HEADER_H) {
				if (mouseX < x + 12) {
					return new Hit(HitType.DROPDOWN_UP, -1);
				}
				if (mouseX >= x + WIDTH - 12) {
					return new Hit(HitType.DROPDOWN_DOWN, -1);
				}
				return new Hit(HitType.NONE, -1);
			}
			int slot = (mouseY - dropdownY - DROPDOWN_HEADER_H) / DROPDOWN_ITEM_H;
			if (slot >= 0 && slot < dropdownVisibleItems) {
				return new Hit(HitType.DROPDOWN_ITEM, slot);
			}
			return Hit.MISS;
		}
		if (inside(mouseY, focusY, ROW_H)) {
			return new Hit(HitType.FOCUS_ROW, -1);
		}
		if (inside(mouseY, modifierY, ROW_H)) {
			return new Hit(HitType.MODIFIER_ROW, -1);
		}
		if (inside(mouseY, minY, ROW_H)) {
			if (mouseX < x + 14) {
				return new Hit(HitType.MIN_DEC, -1);
			}
			if (mouseX >= x + WIDTH - 14) {
				return new Hit(HitType.MIN_INC, -1);
			}
			if (mouseX >= minFieldLeft() && mouseX < minFieldRight()) {
				return new Hit(HitType.MIN_FIELD, -1);
			}
			return new Hit(HitType.NONE, -1);
		}
		if (inside(mouseY, rerollToggleY, ROW_H)) {
			return new Hit(HitType.REROLL_TOGGLE, -1);
		}
		if (inside(mouseY, resetToggleY, ROW_H)) {
			return new Hit(HitType.RESET_TOGGLE, -1);
		}
		if (inside(mouseY, buttonY, BUTTON_H)) {
			return new Hit(HitType.START_BUTTON, -1);
		}
		return Hit.MISS;
	}

	private static boolean inside(int value, int start, int height) {
		return value >= start && value < start + height;
	}
}