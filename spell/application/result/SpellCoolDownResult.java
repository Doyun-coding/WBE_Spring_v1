package lolpago.spell.application.result;

public record SpellCoolDownResult(
	Long summonerId,
	String spellCoolDownMessage
) {
}
