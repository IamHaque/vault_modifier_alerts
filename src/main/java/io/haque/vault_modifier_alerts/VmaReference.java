package io.haque.vault_modifier_alerts;

public final class VmaReference {

	public static final String MOD_ID = "vault_modifier_alerts";
	public static final String SOUND_EVENT_ID = "champ_domain_expired";
	public static final String SUBTITLE_KEY = MOD_ID + ".subtitle." + SOUND_EVENT_ID;
	public static final String CHAMPION_DOMAIN_ID = "the_vault:champion_domain";
	public static final String SOUND_EVENT_NAMESPACED = MOD_ID + ":" + SOUND_EVENT_ID;
	public static final String DOWNED_SOUND_EVENT_ID = "downed_alert";
	public static final String DOWNED_SUBTITLE_KEY = MOD_ID + ".subtitle." + DOWNED_SOUND_EVENT_ID;
	public static final String DOWNED_SOUND_EVENT_NAMESPACED = MOD_ID + ":" + DOWNED_SOUND_EVENT_ID;
	public static final String REROLL_SUCCESS_SOUND_EVENT_ID = "reroll_success";
	public static final String REROLL_SUCCESS_SUBTITLE_KEY = MOD_ID + ".subtitle." + REROLL_SUCCESS_SOUND_EVENT_ID;
	public static final String REROLL_SUCCESS_SOUND_EVENT_NAMESPACED = MOD_ID + ":" + REROLL_SUCCESS_SOUND_EVENT_ID;
	public static final String REROLL_STOP_SOUND_EVENT_ID = "reroll_stop";
	public static final String REROLL_STOP_SUBTITLE_KEY = MOD_ID + ".subtitle." + REROLL_STOP_SOUND_EVENT_ID;
	public static final String REROLL_STOP_SOUND_EVENT_NAMESPACED = MOD_ID + ":" + REROLL_STOP_SOUND_EVENT_ID;
	public static final String DEFAULT_REROLL_SOUND_EVENT = "minecraft:block.note_block.pling";

	public static final String OPERATION_REFORGE_ALL = "the_vault:reforge_all";
	public static final String OPERATION_REFORGE_PREFIX = "the_vault:reforge_affix_prefix";
	public static final String OPERATION_REFORGE_SUFFIX = "the_vault:reforge_affix_suffix";
	public static final String OPERATION_REFORGE_IMPLICITS = "the_vault:reforge_implicits";
	public static final String OPERATION_RESET_POTENTIAL = "the_vault:reset_potential";

	private VmaReference() {
	}
}