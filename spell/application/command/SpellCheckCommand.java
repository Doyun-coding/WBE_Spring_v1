package lolpago.spell.application.command;

import lolpago.region.Region;

public record SpellCheckCommand(
	Long summonerId,
	String finalText,
	Region region
) {
}
