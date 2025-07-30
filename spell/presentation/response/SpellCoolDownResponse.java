package lolpago.spell.presentation.response;

import lolpago.spell.application.result.SpellCoolDownResult;

public record SpellCoolDownResponse(
	Long summonerId,
	String spellCoolDownMessage
) {
	public static SpellCoolDownResponse from(
		SpellCoolDownResult spellCoolDownResult
	) {
		return new SpellCoolDownResponse(spellCoolDownResult.summonerId(), spellCoolDownResult.spellCoolDownMessage());
	}

}
