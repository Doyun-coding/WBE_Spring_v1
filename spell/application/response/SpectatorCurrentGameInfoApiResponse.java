package lolpago.spell.application.response;

import java.util.List;

public record SpectatorCurrentGameInfoApiResponse(
	Long gameId,
	String gameType,
	Long gameStartTime,
	Long mapId,
	Long gameLength,
	String platformId,
	String gameMode,
	List<BannedChampion> bannedChampions,
	Long gameQueueConfigId,
	Observer observers,
	List<CurrentGameParticipant> participants
) {
	public record BannedChampion(
		int pickTurn,
		Long championId,
		Long teamId
	) {}

	public record Observer(
		String encryptionKey
	) {}

	public record CurrentGameParticipant(
		Long championId,
		Perks perks,
		Long profileIconId,
		Boolean bot,
		Long teamId,
		String puuid,
		Long spell1Id,
		Long spell2Id,
		List<GameCustomizationObject> gameCustomizationObjects
	) {}

	public record Perks(
		List<Long> perkIds,
		Long perkStyle,
		Long perkSubStyle
	) {}

	public record GameCustomizationObject(
		String category,
		String content
	) {}
}
