package lolpago.spell.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.ValidationException;
import lolpago.spell.application.command.SpellCoolDownCommand;
import lolpago.spell.application.result.SpellCheckResult;
import lolpago.spell.application.result.SpellCoolDownResult;
import lolpago.spell.application.service.SpellCheckService;
import lolpago.spell.presentation.request.SpellCheckRequest;
import lolpago.spell.presentation.response.SpellCheckResponse;
import lolpago.spell.presentation.response.SpellCoolDownResponse;
import lombok.RequiredArgsConstructor;

/**
 * 스펠 체크 관련 요청을 처리하는 REST 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/spell")
public class SpellCheckController {

	private final SpellCheckService spellCheckService;

	/**
	 * WBE-python 으로부터 "<챔피언이름> <스펠이름>" 텍스트 요청을 받아 스펠 체크 수행
	 */
	@PostMapping
	public ResponseEntity<SpellCheckResponse> checkSpell(@Validated @RequestBody SpellCheckRequest request,
		BindingResult bindingResult) {
		// 유효성 검사
		if(bindingResult.hasErrors()) {
			throw new ValidationException();
		}

		SpellCheckResult spellCheckResult = spellCheckService.championSpellCheck(request.toCommand());

		return ResponseEntity.status(HttpStatus.CREATED).body(SpellCheckResponse.from(spellCheckResult));
	}

	/**
	 * Redis 에서 해당 스펠 쿨타임이 끝났는지 확인하는 대기 요청
	 * 클라이언트는 일정 시간 동안 쿨타임 키가 사라지기를 대기
	 */
	@GetMapping("/await")
	public ResponseEntity<SpellCoolDownResponse> awaitSpellCoolDown(
		@RequestParam Long summonerId,
		@RequestParam String championName,
		@RequestParam String spellName) {

		SpellCoolDownResult spellCoolDownResult = spellCheckService.championSpellCoolDown(
			new SpellCoolDownCommand(summonerId, championName, spellName)
		);

		return ResponseEntity.status(HttpStatus.OK).body(SpellCoolDownResponse.from(spellCoolDownResult));
	}

}
