package lolpago.spell.application.request;

public record SpellAlertRequest(
	Long summonerId,
	String alertMessage
) {
}
