package lolpago.spell.presentation.request;

import jakarta.validation.constraints.NotNull;
import lolpago.region.Region;
import lolpago.spell.application.command.SpellCheckCommand;

public record SpellCheckRequest(
		@NotNull
		Long summonerId,
		@NotNull
		String finalText,
		@NotNull
		Region region
) {

	public SpellCheckCommand toCommand() {
		return new SpellCheckCommand(summonerId, finalText, region);
	}

}
