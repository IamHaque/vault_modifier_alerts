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

	private VmaReference() {
	}
}