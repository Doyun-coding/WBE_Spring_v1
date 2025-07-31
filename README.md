# WBE_Spring_v1

# 구조도
<img width="691" height="561" alt="LOLPAGO STT + TTS 스트리밍 drawio" src="https://github.com/user-attachments/assets/03802ab3-c6ee-4490-b62b-988bd0ced53e" />

# 🎧 Voice WebSocket Server (STT + TTS)

실시간 음성을 받아 STT(음성 인식)로 텍스트로 변환하고, 분석 후 TTS(음성 합성) 응답을 반환하는 WebSocket 기반 Python 서버입니다.

---

## 📌 프로젝트 개요

- **입력**: 클라이언트가 WebSocket으로 전송한 음성 스트림  
- **처리**: 음성 감지 → Whisper 모델을 통한 STT → OpenAI TTS 변환  
- **출력**: 변환된 MP3 음성을 다시 WebSocket을 통해 클라이언트에 전송

---

## 📐 시스템 구조

```plaintext
[Client]
   ⬇
[WebSocket Server (ws_audio_server.py)]
   ⬇
[VoiceActivityDetector]
   ⬇
[Whisper STT (whisper_pipeline)]
   ⬇
[OpenAI TTS (generate_tts_mp3)]
   ⬇
[Return MP3 to Client]

```

## 📂 디렉토리 구조

```
.
├── ws/                              
│   └── ws_audio_server.py           # WebSocket 서버 구현
├── requirements.txt                 # 의존성 목록
├── .env                             # 환경 변수 파일 (API 키 등)
├── worker/
│   ├── stt/
│   │   ├── stt_worker_process.py    # Whisper 기반 음성 인식 처리
│   │   └── util/
│   │       └── stt_worker_util.py  # VoiceActivityDetector (음성 감지기)
│   └── tts/
│       └── tts_worker_process.py    # OpenAI 기반 TTS 생성기
├── main.py                          # 메인 실행 파일
```
