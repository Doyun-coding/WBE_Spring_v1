package lolpago.spell.application.command;

public record SpellCoolDownCommand(
	Long summonerId,
	String championName,
	String spellName
) {
}
