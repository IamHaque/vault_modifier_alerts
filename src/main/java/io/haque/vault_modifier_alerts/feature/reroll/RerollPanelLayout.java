package io.haque.vault_modifier_alerts.feature.reroll;

/**
 * Single source of truth for the auto-reroll panel geometry. Every row, button
 * and dropdown item is computed here exactly once per frame, and both the
 * renderer and the click/scroll routing ask this layout where things are, so
 * the draw path and the input path can never drift apart.
 */
public final class RerollPanelLayout {

	/** Full-width panel; shrinks toward {@link #MIN_WIDTH} when no side room beside the station window. */
	public static final int WIDTH = 216;
	public static final int MIN_WIDTH = 160;
	public static final int MARGIN = 22;
	public static final int PAD_X = 8;

	public static final int TITLE_H = 18;
	public static final int ROW_H = 14;
	public static final int BUTTON_H = 14;
	public static final int DROPDOWN_HEADER_H = 14;
	public static final int DROPDOWN_ITEM_H = 14;
	public static final int BOTTOM_PAD = 6;

	public static final int DEFAULT_DROPDOWN_ROWS = 8;
	public static final int MIN_DROPDOWN_ROWS = 3;
	public static final int MAX_DROPDOWN_ROWS = 8;

	public 	enum HitType {
		NONE, MIN_DEC, MIN_FIELD, MIN_INC
	}

	public record Hit(HitType type, int index) {
		public static final Hit MISS = new Hit(HitType.NONE, -1);
	}

	public final int x;
	public final int y;
	public final int width;
	public final int baseHeight;
	public final int dropdownVisibleItems;
	public final int dropdownHeight;
	public final int totalHeight;

	public final int titleBottom;
	public final int focusY;
	public final int minY;
	public final int rangeY;
	public final int potentialY;
	public final int rerollToggleY;
	public final int resetToggleY;
	public final int buttonY;
	public final int statusY;
	public final int counterY;
	public final int dropdownY;
	public final int dropdownEnd;

	public RerollPanelLayout(int x, int y, int width, boolean dropdownOpen, int dropdownCount, int dropdownScroll,
			int maxDropdownRows) {
		this.x = x;
		this.y = y;
		this.width = Math.max(MIN_WIDTH, width);

		titleBottom = y + TITLE_H;
		focusY = titleBottom;
		minY = focusY + 3 * ROW_H;
		rangeY = minY + ROW_H;
		potentialY = rangeY + ROW_H;
		rerollToggleY = potentialY + ROW_H;
		resetToggleY = rerollToggleY + ROW_H;
		buttonY = resetToggleY + ROW_H;
		statusY = buttonY + BUTTON_H;
		counterY = statusY + ROW_H;
		baseHeight = counterY + ROW_H + BOTTOM_PAD - y;

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
		return x + 44;
	}

	public int minFieldRight() {
		return Math.min(x + 184, x + width - 18);
	}

	public record Rect(int x, int y, int width, int height) {
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
			return Hit.MISS;
		}
		if (inside(mouseY, minY, ROW_H)) {
			if (mouseX < x + 16) {
				return new Hit(HitType.MIN_DEC, -1);
			}
			if (mouseX >= x + width - 16) {
				return new Hit(HitType.MIN_INC, -1);
			}
			if (mouseX >= minFieldLeft() && mouseX < minFieldRight()) {
				return new Hit(HitType.MIN_FIELD, -1);
			}
			return new Hit(HitType.NONE, -1);
		}
		return Hit.MISS;
	}

	private static boolean inside(int value, int start, int height) {
		return value >= start && value < start + height;
	}
}