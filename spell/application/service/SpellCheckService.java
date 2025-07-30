package lolpago.spell.application.service;

import static lolpago.common.exception.ExceptionMessage.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lolpago.client.riot.RiotClient;
import lolpago.common.exception.type.InternalServerErrorException;
import lolpago.common.exception.type.NotFoundException;
import lolpago.spell.application.command.SpellCheckCommand;
import lolpago.spell.application.command.SpellCoolDownCommand;
import lolpago.spell.application.response.SpectatorCurrentGameInfoApiResponse;
import lolpago.spell.application.result.SpellCheckResult;
import lolpago.spell.application.result.SpellCoolDownResult;
import lolpago.staticdata.domain.Champion;
import lolpago.staticdata.domain.repository.ChampionRepository;
import lolpago.summoner.domain.Summoner;
import lolpago.summoner.domain.SummonerRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * 스펠 체크 및 쿨타임 확인 로직을 처리하는 서비스 클래스
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SpellCheckService {
	// 스펠 이름과 각 스펠의 쿨타임을 매칭
	private static final Map<String, Long> SPELL_COOL_TIME = Map.of(
			"점멸", 300_000L, // 300_000L : 5분
			"순간이동", 360_000L,
			"점화", 180_000L,
			"회복", 240_000L,
			"탈진", 210_000L,
			"정화", 210_000L,
			"방어막", 180_000L,
			"유체화", 210_000L,
			"강타", 90_000L
	);

	// 스펠 목록
	private static final List<String> spells = List.of(
			"점멸", "순간이동", "점화", "회복", "탈진", "정화", "방어막", "유체화", "강타"
	);

	private final RiotClient riotClient;
	private final SummonerRepository summonerRepository;
	private final ChampionRepository championRepository;
	private final StringRedisTemplate spellRedisTemplate;

	public SpellCheckService(RiotClient riotClient,
		SummonerRepository summonerRepository,
		ChampionRepository championRepository,
		@Qualifier("spellRedisTemplate") StringRedisTemplate spellRedisTemplate
	) {
		this.riotClient = riotClient;
		this.summonerRepository = summonerRepository;
		this.championRepository = championRepository;
		this.spellRedisTemplate = spellRedisTemplate;
	}

	/**
	 * 주어진 텍스트로부터 적 챔피언 이름과 스펠명을 추출
	 * 해당 스펠의 쿨타임을 Redis 에 등록
	 */
	public SpellCheckResult championSpellCheck(SpellCheckCommand command) {
		// 소환사 정보 조회
		Summoner summoner = summonerRepository.getById(command.summonerId());

		// 현재 게임 정보 Riot API 호출
		ResponseEntity<SpectatorCurrentGameInfoApiResponse> spectatorCurrentGameInfoApiResponse =
		riotClient.getSpectatorCurrentGameInfo(summoner.getPuuid(), command.region());
		// API 실패 또는 응답 없음
		if(!spectatorCurrentGameInfoApiResponse.getStatusCode().is2xxSuccessful() ||
			Objects.isNull(spectatorCurrentGameInfoApiResponse.getBody())) {
			throw new NotFoundException(SPECTATOR_CURRENT_GAME_INFO_NOT_FOUND_MESSAGE);
		}

		SpectatorCurrentGameInfoApiResponse gameInfo = spectatorCurrentGameInfoApiResponse.getBody();

		// 자신의 팀 ID 찾기 (ID를 통해 상대 팀의 ID 알 수 있다)
		Long myTeamId = gameInfo.participants().stream()
			.filter(p -> summoner.getPuuid().equals(p.puuid()))
			.map(SpectatorCurrentGameInfoApiResponse.CurrentGameParticipant::teamId)
				.filter(Objects::nonNull)
			.findFirst()
			.orElseThrow(() -> new NotFoundException(MY_TEAM_ID_NOT_FOUND_MESSAGE));

		// 상대 챔피언들의 한글 이름 목록
		List<String> enemyChampions = gameInfo.participants().stream()
			.filter(p -> !p.teamId().equals(myTeamId))
			.map(SpectatorCurrentGameInfoApiResponse.CurrentGameParticipant::championId)
			.map(championId -> championRepository.getById(championId.intValue()))
			.map(Champion::getKrName)
			.toList();

		// finalText 에 적 챔피언 이름이 없으면 예외
		if(!isChampion(command.finalText(), enemyChampions)) {
			throw new NotFoundException(CHAMPION_NAME_NOT_FOUND_MESSAGE);
		}

		// finalText 에 유효한 스펠 이름이 없으면 예외
		if(!isSpell(command.finalText())) {
			throw new NotFoundException(SPELL_NAME_NOT_FOUND_MESSAGE);
		}

		// 텍스트에서 챔피언 이름과 스펠명 추출
		Optional<String> championName = extractChampionName(command.finalText(), enemyChampions);
		Optional<String> spellName = extractSpell(command.finalText());

		// Redis 에 쿨타임 등록
		String championSpellRedisKey = command.summonerId().toString() + ":" + championName.get() + ":" + spellName.get();
		String championSpellRedisValue = championName.get() + ":" + spellName.get();
		spellRedisTemplate.opsForValue().set(
			championSpellRedisKey, championSpellRedisValue, getSpellCoolTime(spellName.get()), TimeUnit.MILLISECONDS
		);

		return new SpellCheckResult(
			summoner.getId(), championName.get(), spellName.get(), spellRegisterMessage(championName.get(), spellName.get())
		);
	}

	/**
	 * Redis 에 저장된 쿨타임 키가 사라질 때까지 최대 6분 동안 대기
	 * 사라지면 알림 메시지와 함께 반환, 끝까지 안 사라지면 예외
	 */
	public SpellCoolDownResult championSpellCoolDown(SpellCoolDownCommand spellCoolDownCommand) {
		String championSpellRedisKey = spellCoolDownCommand.summonerId() + ":" + spellCoolDownCommand.championName() + ":" + spellCoolDownCommand.spellName();

		long timeoutMillis = TimeUnit.MINUTES.toMillis(6);
		long checkIntervalMillis = 1000; // 1초마다 검사
		long waited = 0;

		while(waited < timeoutMillis) {
			Boolean exists = spellRedisTemplate.hasKey(championSpellRedisKey);
			if(Boolean.FALSE.equals(exists)) {
				// 쿨타임이 끝나 키가 사라졌다면 알림 메시지 반환
				return new SpellCoolDownResult(spellCoolDownCommand.summonerId(), alertMessage(
					spellCoolDownCommand.championName(), spellCoolDownCommand.spellName()
				));
			}

			try {
				Thread.sleep(checkIntervalMillis); // 1초 대기 후 반복
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new InternalServerErrorException(SPELL_COOL_DOWN_MESSAGE);
			}

			waited += checkIntervalMillis;
		}

		// 제한 시간 초과해도 키가 남아 있으면 실패 처리
		throw new InternalServerErrorException(SPELL_COOL_DOWN_MESSAGE);
	}

	// 텍스트에 포함된 챔피언 이름이 적 챔피언 중에 있는지 확인
	private boolean isChampion(String finalText, List<String> enemyChampions) {
		return enemyChampions.stream()
			.anyMatch(finalText::contains);
	}

	// 텍스트에 포함된 스펠 이름이 유효한 스펠 목록에 있는지 확인
	private boolean isSpell(String finalText) {
		return spells.stream()
			.anyMatch(finalText::contains);
	}

	// 스펠 이름에 해당하는 쿨타임 반환
	private long getSpellCoolTime(String spellName) {
		return SPELL_COOL_TIME.get(spellName);
	}

	// 텍스트에서 적 챔피언 이름 추출
	private Optional<String> extractChampionName(String finalText, List<String> enemyChampions) {
		return enemyChampions.stream()
			.filter(finalText::contains)
			.findFirst();
	}

	// 텍스트에서 스펠 이름 추출
	private Optional<String> extractSpell(String finalText) {
		return spells.stream()
			.filter(finalText::contains)
			.findFirst();
	}

	// 스펠 쿨타임이 끝났다는 메시지 생성
	private String alertMessage(String championName, String spell) {
		return String.format("%s %s 돌았습니다!", championName, spell);
	}

	// 스펠 쿨타임이 등록되었다는 메시지 생성
	private String spellRegisterMessage(String championName, String spell) {
		return String.format("%s %s 쿨타임 등록했습니다!", championName, spell);
	}

}
