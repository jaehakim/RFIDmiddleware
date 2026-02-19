#     Fixed Reader Simulator - 프로젝트 진행상황

## 프로젝트 개요
    Java SDK를 분석하여 고정형 UHF RFID 리더기 시뮬레이터를 Java로 개발

## SDK 분석 내역
- **소스**: ` - _Java_SDK_v1.00.00b01_20260207`
- **프로토콜**: CM-frame over TCP (헤더 `0x43 0x4D`)
- **프레임 포맷**: `[C][M][CmdCode][Address][DataLen_LE(2)][Data(N)][BCC]`
- **BCC**: Data 영역 XOR 체크섬
- **JAR 디컴파일**: FixedReaderLib.jar 내부 ProcessCenter, Processor, Client 클래스 바이트코드 분석

## 핵심 발견사항

### 인벤토리 태그 리포트 데이터 포맷 (cmdCode=42)
SDK 내부 `inventoryOnceReportValidator`가 검증하는 포맷:
```
[PC(2)][EPC(N)][CRC-16(2)][Antenna(1)][RSSI(1)][Count(1)]
```
- **PC**: Protocol Control, bits[15:11] = EPC 워드 수 (0x3000 = 96-bit EPC)
- **CRC-16**: `~CRC-CCITT(PC+EPC)` big-endian
- **최소 길이**: `epcLenBytes + 7` (96-bit EPC 기준 19바이트)
- 포맷 불일치 시 SDK가 리포트를 무시함 (콜백 호출 안됨)

### 주요 커맨드 코드
| Code | 명칭 | 동작 |
|------|------|------|
| 6 | GetSN | 시리얼번호 조회 |
| 42 | StartInventory | 인벤토리 시작 (비동기) |
| 43 | StopInventory | 인벤토리 중지 |
| 49 | GetVersion | 펌웨어 버전 조회 |
| 101 | GetDeviceName | 장치명 조회 (플랫폼 감지용) |
| 143 | GetAllSystemParams | 전체 시스템 파라미터 |
| 244 | ReadOemRegister | OEM 레지스터 읽기 (GlobalBand 등) |

## 개발 완료 항목

### 1. ReaderSimulator.java
- **TCP 서버 모드**: 지정 포트에서 SDK 연결 대기 (기본 20058)
- **Active 모드**: `--connect host:port`로 SDK listen 포트에 연결
- **UDP Discovery 응답**: 포트 17777에서 장치 검색 응답
- **30+ 커맨드 처리**: GetDeviceName, GetVersion, GetSN, StartInventory, StopInventory, GetAllSystemParams, ReadOemRegister 등
- **인벤토리 시뮬레이션**: 1초 간격, 랜덤 1~5개 태그, CRC-16 포함
- **TAG_DATA.txt 연동**: 외부 파일에서 EPC 로딩, 실시간 파일 변경 감지 (재시작 불필요)
- **PC 자동 계산**: EPC 바이트 길이에 맞춰 PC 워드 자동 생성

### 2. TAG_DATA.txt
- 127개 EPC 태그 데이터
- HEX 문자열 형식, 한 줄에 하나
- 시뮬레이터 실행 중 편집 시 자동 반영

### 3. run_simulator.bat
- 단일 시뮬레이터 컴파일 + 실행
- 사용법: `run_simulator.bat [--port N]`

### 4. run_multi_simulator.bat
- N개 시뮬레이터 동시 실행 (각각 별도 CMD 창)
- 사용법: `run_multi_simulator.bat [개수] [시작포트]`
- 예: `run_multi_simulator.bat 5 20058` → 20058~20062 포트에 5개 실행

## 해결한 이슈
1. **태그 데이터 미수신**: SDK `inventoryOnceReportValidator`가 `[PC][EPC][CRC]` 순서와 CRC-CCITT 검증을 요구 → 포맷 수정으로 해결
2. **소켓 읽기 불안정**: `available()` 기반 읽기가 Windows에서 불안정 → 블로킹 read + 타임아웃 방식으로 변경

## 파일 구조
```
  ReaderSimulator.java    -- 시뮬레이터 소스
  ReaderSimulator.class   -- 컴파일 결과
  TAG_DATA.txt           -- EPC 태그 데이터 (편집 가능)
  run_simulator.bat      -- 단일 실행
  run_multi_simulator.bat-- 멀티 실행
  CLAUDE.md              -- 본 문서
```
