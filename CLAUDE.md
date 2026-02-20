# RFID 미들웨어 - 프로젝트 문서

## 미들웨어 시작 시 데이터 흐름 및 작업순서

### 1단계: 애플리케이션 시작 (Main.java)
```
Main.main()
  → UIManager.setLookAndFeel()  -- 시스템 Look & Feel 설정
  → SwingUtilities.invokeLater()
      → new MainFrame()  -- 메인 윈도우 생성 (2단계로)
      → frame.setVisible(true)
```

### 2단계: MainFrame 생성자 초기화
```
MainFrame()
  ├── (1) DatabaseConfig 로드  -- database.cfg 파일 읽기
  ├── (2) DatabaseManager.initialize()  -- DB 연결 + 테이블 생성
  │       ├── MariaDB JDBC 드라이버 로드 (org.mariadb.jdbc.Driver)
  │       ├── DriverManager.getConnection()  -- TCP 연결
  │       ├── SET NAMES utf8mb4  -- 한글 인코딩 설정
  │       └── CREATE TABLE IF NOT EXISTS  -- 4개 테이블 자동 생성
  │           ├── tag_reads         (태그 읽기 이력)
  │           ├── assets            (자산 마스터)
  │           ├── export_permissions (반출허용 목록)
  │           └── export_alerts     (미허가 반출 알림 이력)
  │
  ├── (3) TagRepository.start()  -- 태그 DB 배치 저장 스레드 시작
  │       └── TagDB-Writer 데몬 스레드 시작 (BlockingQueue 폴링)
  │
  ├── (4) AssetRepository.start(30)  -- ★ 자산/반출허용 데이터 로드
  │       ├── refreshCache()  -- 최초 1회 즉시 실행
  │       │   ├── SELECT * FROM assets → ConcurrentHashMap<EPC, AssetInfo> 캐시
  │       │   │   └── ★ EPC 정규화: 16비트 워드(4자리 HEX) 경계로 패딩
  │       │   │       예) DB "0420100420250910000006" (22자)
  │       │   │        → 캐시 키 "042010042025091000000600" (24자)
  │       │   │       리더기는 항상 워드 단위로 읽으므로 뒤에 00 패딩됨
  │       │   └── SELECT epc FROM export_permissions
  │       │       WHERE permit_start <= NOW() AND permit_end >= NOW()
  │       │       → Set<EPC> permittedEpcs 캐시 (EPC 정규화 적용)
  │       └── ScheduledExecutorService  -- 30초 주기 갱신 스케줄 등록
  │
  ├── (5) GUI 컴포넌트 생성
  │       ├── ReaderManager
  │       ├── ReaderStatusPanel
  │       ├── TagDataPanel (Caffeine 캐시 포함)
  │       └── LogPanel
  │
  ├── (6) initLayout()  -- 화면 레이아웃 구성
  │       ├── 상단: 툴바 (전체연결, 해제, 인벤토리 시작/중지, 태그초기화, 자산DB, 도움말, 설정)
  │       └── 중앙: JSplitPane
  │           ├── 상: ReaderStatusPanel (리더기 상태 카드)
  │           └── 하: JSplitPane
  │               ├── 상: TagDataPanel (태그 데이터 테이블)
  │               └── 하: LogPanel (시스템 로그)
  │
  ├── (7) loadConfig()  -- readers.cfg 로드 → initializeReaders()
  │       ├── ReaderConfig.loadFromFile()
  │       └── initializeReaders()
  │           ├── statusListener 등록 (상태변경, 경광등, 부저, 로그 콜백)
  │           ├── tagListener 등록 (태그 감지 → 3단계로)
  │           └── readerManager.initialize()  -- ReaderConnection 생성 (연결은 안 함)
  │
  └── (8) ApiServer 시작  -- REST API 서버 (포트 18080)
          ├── HttpServer.create(18080)
          ├── 핸들러 등록 (/api/readers, /api/assets, /api/export-permissions, /api/export-alerts)
          └── server.start()
```

### 3단계: 태그 감지 시 처리 흐름 (tagListener)
```
리더기에서 태그 감지 (백그라운드 TCP 수신 스레드)
  │
  ├── (A-1) AssetRepository.getAssetInfo(epc)  -- 자산정보 조회 (메모리 O(1))
  │         ※ 리더기 EPC(24자)와 정규화된 캐시 키 일치 → 자산정보 반환
  │
  ├── (A-2) AssetRepository.checkUnauthorizedExport(epc)  -- 미허가 반출 체크
  │         ├── assetMap.get(epc)  -- 자산 테이블에 있는 EPC인가?
  │         │   └── 없으면 → null 반환 (자산이 아님, 일반 태그)
  │         └── permittedEpcs.contains(epc)  -- 반출허용 되어있는가?
  │             ├── 허용됨 → null 반환 (정상 반출)
  │             └── 미허용 → AssetInfo 반환 (미허가 반출!)
  │
  └── SwingUtilities.invokeLater() (EDT에서 실행)
      │
      ├── (B) tagDataPanel.addTag(자산번호, 자산명, 부서 포함)  -- 태그 데이터 테이블에 표시
      │       ※ 자산정보가 있으면 자산번호/자산명/부서 표시, 없으면 공백
      │       ├── Caffeine 캐시로 EPC 중복 체크
      │       │   ├── 캐시 HIT (TTL 내 동일 EPC) → update만, return false
      │       │   └── 캐시 MISS (신규 또는 TTL 만료) → 캐시에 추가, return true
      │       └── tagList에 항상 추가 (비중복제거 모드용)
      │
      ├── (C) [isNew == true일 때] TagRepository.insertTagRead()  -- tag_reads INSERT
      │       └── BlockingQueue에 offer → TagDB-Writer 스레드가 배치 처리
      │           └── 50건 모이거나 500ms 경과 시 executeBatch()
      │
      └── (D) [미허가 반출일 때 + 30초 내 중복 아닐 때]
              ├── [경광등 '적용'(1)인 리더기만]
              │   └── WarningLightController.triggerWarningLight(connection)
              │       ├── connection.lightOn()  -- 릴레이 2번(빨간등) ON
              │       └── 5초 후 lightOff() 자동 소등 (재감지 시 타이머 리셋)
              │
              ├── ★ AssetRepository.insertAlert()  -- export_alerts INSERT
              │   └── INSERT INTO export_alerts
              │       (epc, asset_number, reader_name, rssi, alert_time)
              │       VALUES (?, ?, ?, ?, ?)
              │   ※ 즉시 실행 (배치 아닌 단건 INSERT)
              │   ※ AssetRepository.shouldAlert(epc)가 true일 때만 (30초 내 중복 방지)
              │
              └── logPanel.appendLog()  -- "UNAUTHORIZED EXPORT: EPC=..., Asset=..."
```

### 4단계: 주기적 백그라운드 작업
```
[AssetRepository - 30초 주기]
  ├── assets 테이블 전체 SELECT → 메모리 캐시 갱신
  └── export_permissions 유효기간 SELECT → 허용 EPC Set 갱신
      ※ 외부 시스템에서 자산/반출허용 데이터를 변경하면
        최대 30초 후 미들웨어에 반영됨

[TagRepository - TagDB-Writer 스레드]
  └── BlockingQueue에서 50건/500ms 간격으로 배치 INSERT (tag_reads)
```

### 5단계: 종료 처리
```
윈도우 닫기 → 확인 다이얼로그
  ├── ApiServer.shutdown()  -- REST API 서버 종료
  ├── ReaderManager.shutdown()  -- 모든 리더기 연결 해제
  ├── WarningLightController.shutdown()  -- 경광등 타이머 취소
  ├── AssetRepository.shutdown()  -- 갱신 스케줄러 중지
  ├── TagRepository.shutdown()  -- 배치 Writer 스레드 중지 (잔여 flush)
  └── DatabaseManager.shutdown()  -- DB 연결 종료
```

---

## DB 테이블별 데이터 흐름 요약

| 테이블 | 데이터 관리 주체 | 미들웨어 동작 | 시점 |
|--------|-----------------|--------------|------|
| **assets** | 외부 시스템 (INSERT/UPDATE/DELETE) | SELECT 조회만 | 시작 시 + 30초마다 |
| **export_permissions** | 외부 시스템 (INSERT/UPDATE/DELETE) | SELECT 조회만 (유효기간 체크) | 시작 시 + 30초마다 |
| **export_alerts** | **미들웨어** (INSERT) | 미허가 반출 감지 시 기록 | 태그 감지 시 (30초 중복제거) |
| **tag_reads** | **미들웨어** (INSERT) | 태그 읽기 이력 기록 | 태그 감지 시 (캐시 MISS만) |

---

## 프로젝트 구조

### 주요 파일
```
RFIDmiddleware/RFIDMiddleware/
├── config/
│   ├── readers.cfg          -- 리더기 설정 (이름,IP,포트,부저,경광등,안테나출력,드웰타임)
│   └── database.cfg         -- DB 설정 (MariaDB 10.0.0.148:13306, tagflow)
├── libs/
│   ├── FixedReaderLib.jar   -- RFID 리더기 SDK
│   ├── ReaderFinderLib.jar  -- 리더기 검색 SDK
│   ├── mariadb-java-client-3.5.1.jar
│   └── caffeine-2.9.3.jar  -- 캐시 라이브러리
├── src/com/apulse/middleware/
│   ├── Main.java            -- 진입점
│   ├── api/
│   │   └── ApiServer.java       -- REST API 서버 (포트 18080, HttpServer)
│   ├── config/
│   │   ├── ReaderConfig.java    -- 리더기 설정 모델
│   │   └── DatabaseConfig.java  -- DB 설정 모델
│   ├── db/
│   │   ├── DatabaseManager.java -- DB 연결 관리 (싱글톤)
│   │   ├── TagRepository.java   -- 태그 읽기 배치 저장 (싱글톤)
│   │   └── AssetRepository.java -- 자산/반출허용 캐시, 미허가 반출 판단 (싱글톤)
│   ├── reader/
│   │   ├── ReaderConnection.java -- 리더기 TCP 연결, 경광등/부저 제어
│   │   ├── ReaderManager.java    -- 멀티 리더기 관리
│   │   ├── ReaderStatus.java     -- 연결 상태 enum
│   │   ├── TagData.java          -- 태그 데이터 모델
│   │   └── WarningLightController.java -- 경광등 자동 ON/OFF 제어 (싱글톤)
│   ├── gui/
│   │   ├── MainFrame.java        -- 메인 윈도우
│   │   ├── ReaderStatusPanel.java -- 리더기 상태 그리드
│   │   ├── ReaderIconComponent.java -- 개별 리더기 카드
│   │   ├── TagDataPanel.java     -- 태그 데이터 테이블 (미허가 반출 빨간 배경)
│   │   ├── LogPanel.java         -- 시스템 로그
│   │   └── ConfigDialog.java     -- 설정 다이얼로그
│   └── util/
│       └── HexUtils.java         -- HEX 변환, 타임스탬프
├── build.bat
└── run.bat
```

### 아키텍처 패턴
- **싱글톤**: DatabaseManager, TagRepository, AssetRepository, WarningLightController
- **리스너/옵저버**: ReaderConnectionListener, TagDataListener
- **배치 처리**: TagRepository (BlockingQueue, 50건/500ms)
- **메모리 캐시**: AssetRepository (ConcurrentHashMap, 30초 갱신)
- **중복 방지 캐시**: Caffeine (TagDataPanel TTL 캐시, AssetRepository 알림 30초 중복제거)
- **스레드풀**: ReaderManager (CachedThreadPool)
- **비동기 UI 갱신**: SwingUtilities.invokeLater()

---

## DB 테이블 (4개) - MariaDB 10.0.0.148:13306/tagflow

### tag_reads (태그 읽기 이력)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AI | 읽기 ID |
| epc | VARCHAR(128) NOT NULL | RFID EPC 코드 |
| reader_name | VARCHAR(64) NOT NULL | 리더기명 |
| rssi | INT NOT NULL | 수신 신호 강도 (dBm) |
| read_time | DATETIME NOT NULL | 태그 읽기 시각 |
| created_at | DATETIME DEFAULT NOW | 기록일시 |

### assets (자산 마스터 - 외부 관리)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AI | 자산 ID |
| asset_number | VARCHAR(64) NOT NULL | 자산번호 |
| epc | VARCHAR(128) NOT NULL UNIQUE | RFID EPC 코드 |
| asset_name | VARCHAR(128) | 자산명 |
| department | VARCHAR(64) | 관리 부서 |
| created_at | DATETIME DEFAULT NOW | 등록일시 |

### export_permissions (반출허용 목록 - 외부 관리)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AI | 허용 ID |
| epc | VARCHAR(128) NOT NULL | RFID EPC 코드 |
| permit_start | DATETIME | 반출허용 시작일시 |
| permit_end | DATETIME | 반출허용 종료일시 |
| reason | VARCHAR(256) | 반출 사유 |
| created_at | DATETIME DEFAULT NOW | 등록일시 |

### export_alerts (미허가 반출 알림 이력 - 미들웨어 기록)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AI | 알림 ID |
| epc | VARCHAR(128) NOT NULL | RFID EPC 코드 |
| asset_number | VARCHAR(64) | 자산번호 |
| reader_name | VARCHAR(64) NOT NULL | 감지 리더기명 |
| rssi | INT | 수신 신호 강도 (dBm) |
| alert_time | DATETIME NOT NULL | 알림 발생 시각 |
| created_at | DATETIME DEFAULT NOW | 기록일시 |

---

## EPC 정규화 (워드 경계 패딩)

RFID 리더기는 EPC 메모리를 **16비트 워드(2바이트 = 4 HEX 자리)** 단위로 읽는다.
DB에 저장된 EPC 길이가 워드 경계에 맞지 않으면 리더기 출력과 불일치가 발생한다.

```
DB 원본:  0420100420250910000006   (22자 = 11바이트, 5.5워드)
리더기:   042010042025091000000600  (24자 = 12바이트, 6워드 = EPC-96)
                                ^^  리더기가 워드 단위로 패딩한 00
```

**해결**: `AssetRepository.normalizeEpc()`에서 DB 로딩 시 EPC를 4자리 단위로 패딩
- 캐시 키가 리더기 출력 EPC와 일치하여 O(1) 해시맵 조회 성능 유지
- DB 원본 데이터는 변경하지 않음

---

## EPC Mask 필터

태그 수신 시 EPC 접두사(mask)로 필터링하여 특정 태그만 처리하는 기능.

### 동작
- **글로벌 설정**: 모든 리더기에 공통 적용 (리더기별이 아닌 전체 설정)
- **접두사 매칭**: mask 값이 있으면, EPC가 해당 값(대소문자 무시)으로 시작하는 태그만 처리
- **빈 값**: mask가 비어있거나 없으면 모든 태그 처리 (기존 동작)
- **적용 시점**: tagListener 진입 시 가장 먼저 체크 (mask 불일치 → 즉시 return)
- **재시작 불필요**: 설정 다이얼로그에서 저장 즉시 적용 (정적 필드를 매 태그마다 참조)

### readers.cfg 저장 형식
```
# 리더기 설정 파일
MASK=0420
Reader-01,192.168.0.196,20058,1,0,30,30,30,30,500
...
```
- 기존 CSV 리더기 파서는 `MASK=` 줄을 자동 스킵 (CSV 3필드 미만)
- 하위 호환 유지

### 관련 코드
| 파일 | 역할 |
|------|------|
| `config/ReaderConfig.java` | `epcMask` 정적 필드, `loadFromFile()`에서 `MASK=` 파싱, `saveToFile()`에서 출력 |
| `gui/ConfigDialog.java` | EPC Mask 입력 필드 UI (설정 다이얼로그 상단) |
| `gui/MainFrame.java` | tagListener 맨 앞에서 mask 필터링 수행 |

---

## GUI 기능

### 툴바 버튼
| 버튼 | 기능 |
|------|------|
| 전체 연결 | 모든 리더기 TCP 연결 |
| 전체 해제 | 모든 리더기 연결 해제 |
| 인벤토리 시작 | 모든 리더기 태그 읽기 시작 |
| 인벤토리 중지 | 모든 리더기 태그 읽기 중지 |
| 태그 초기화 | 태그 데이터 테이블 초기화 |
| **자산 DB** | 자산 DB 조회 다이얼로그 (3개 탭) |
| 도움말 | 사용법 + DB 데이터 흐름 표시 |
| 설정 | 리더기/DB 설정 다이얼로그 |

### 자산 DB 조회 다이얼로그 (자산 DB 버튼)
| 탭 | 내용 | 데이터 소스 |
|----|------|------------|
| **자산 목록** | 전체 자산 조회 (자산번호, EPC, 자산명, 부서, 등록일) | `SELECT FROM assets` |
| **반출허용 목록** | 반출허용 현황 (EPC, 자산번호, 자산명, 허용기간, 사유, 유효/만료 상태) | `SELECT FROM export_permissions LEFT JOIN assets` |
| **반출알림 이력** | 미허가 반출 알림 기간 조회 (알림시간, 리더기, EPC, 자산번호, 자산명, RSSI) | `SELECT FROM export_alerts LEFT JOIN assets` |

### 태그 데이터 테이블 컬럼
| 컬럼 | 설명 |
|------|------|
| 시간 | 마지막 읽기 시각 |
| 리더기 | 태그를 읽은 리더기명 |
| EPC | 태그 EPC 코드 |
| RSSI | 수신 신호 강도 |
| 횟수 | 읽기 횟수 (중복제거 모드) |
| **자산번호** | assets 테이블 매칭 시 표시, 비자산은 공백 |
| **자산명** | assets 테이블 매칭 시 표시, 비자산은 공백 |
| **부서** | assets 테이블 매칭 시 표시, 비자산은 공백 |

---

## 외부 REST API

### 개요
- `com.sun.net.httpserver.HttpServer` 사용 (JDK 내장, 추가 라이브러리 불필요)
- 포트: **18080**
- 응답 형식: `Content-Type: application/json; charset=UTF-8`
- CORS 허용 (`Access-Control-Allow-Origin: *`)

### API 엔드포인트

#### 조회 API (GET)
| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/readers` | 리더기 설정정보 전체 조회 (이름, IP, 포트, 부저, 경광등, 안테나출력, 드웰시간, 상태) |
| GET | `/api/assets` | 자산 테이블 조회 (자산번호, EPC, 자산명, 부서, 등록일) |
| GET | `/api/export-permissions` | 반출허용 목록 조회 (EPC, 자산번호, 자산명, 허용기간, 사유, 상태) |
| GET | `/api/export-alerts?from=yyyy-MM-dd HH:mm:ss&to=yyyy-MM-dd HH:mm:ss` | 반출알림 이력 조회 (기간 필수) |

#### 제어 API (POST)
| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/control/connect-all` | 전체 리더기 연결 |
| POST | `/api/control/disconnect-all` | 전체 리더기 연결 해제 |
| POST | `/api/control/start-inventory` | 전체 인벤토리 시작 |
| POST | `/api/control/stop-inventory` | 전체 인벤토리 중지 |

#### 수정 API (PUT/POST/DELETE)
| Method | URL | 설명 |
|--------|-----|------|
| PUT | `/api/readers/{name}` | 리더기 설정 수정 (ip, port, buzzer, warningLight, antennaPower1~4, dwellTime) |
| POST | `/api/export-permissions` | 반출허용 추가 (epc, permitStart, permitEnd, reason) |
| DELETE | `/api/export-permissions/{id}` | 반출허용 삭제 (id로 삭제) |

### 응답 형식

```json
// 성공
{"status": "ok", "data": [...]}

// 실패
{"status": "error", "message": "에러 내용"}
```

### 요청 예시

```bash
# 리더기 목록 조회
curl http://localhost:18080/api/readers

# 자산 목록 조회
curl http://localhost:18080/api/assets

# 반출허용 목록 조회
curl http://localhost:18080/api/export-permissions

# 반출알림 이력 조회 (기간 지정)
curl "http://localhost:18080/api/export-alerts?from=2026-01-01%2000:00:00&to=2026-12-31%2023:59:59"

# 리더기 설정 수정
curl -X PUT http://localhost:18080/api/readers/Reader1 \
  -H "Content-Type: application/json" \
  -d '{"ip":"192.168.0.100","port":14150,"buzzer":true,"warningLight":true,"dwellTime":500}'

# 반출허용 추가
curl -X POST http://localhost:18080/api/export-permissions \
  -H "Content-Type: application/json" \
  -d '{"epc":"0420100420250910000006","permitStart":"2026-01-01 00:00:00","permitEnd":"2026-12-31 23:59:59","reason":"업무용 반출"}'

# 반출허용 삭제
curl -X DELETE http://localhost:18080/api/export-permissions/1

# 전체 리더기 연결
curl -X POST http://localhost:18080/api/control/connect-all

# 전체 리더기 연결 해제
curl -X POST http://localhost:18080/api/control/disconnect-all

# 전체 인벤토리 시작
curl -X POST http://localhost:18080/api/control/start-inventory

# 전체 인벤토리 중지
curl -X POST http://localhost:18080/api/control/stop-inventory
```

### 데이터 흐름

```
외부 시스템
  │
  ├── GET /api/readers ──────────→ ReaderManager.getConnections() → JSON 응답
  ├── GET /api/assets ───────────→ AssetRepository.queryAssets() → JSON 응답
  ├── GET /api/export-permissions → AssetRepository.queryExportPermissions() → JSON 응답
  ├── GET /api/export-alerts ────→ AssetRepository.queryExportAlerts(from, to) → JSON 응답
  │
  ├── POST /api/control/connect-all ──→ ReaderManager.connectAll() → JSON 응답
  ├── POST /api/control/disconnect-all → ReaderManager.disconnectAll() → JSON 응답
  ├── POST /api/control/start-inventory → ReaderManager.startInventoryAll() → JSON 응답
  ├── POST /api/control/stop-inventory → ReaderManager.stopInventoryAll() → JSON 응답
  │
  ├── PUT /api/readers/{name} ──→ ReaderConfig 수정 → saveToFile() → JSON 응답
  ├── POST /api/export-permissions → DB INSERT → refreshCache() → JSON 응답
  └── DELETE /api/export-permissions/{id} → DB DELETE → refreshCache() → JSON 응답
```

### 구현 파일
| 파일 | 역할 |
|------|------|
| `api/ApiServer.java` | REST API 서버 (HttpServer, 핸들러, JSON 처리) |
| `db/AssetRepository.java` | `insertPermission()`, `deletePermission()` 메서드 |
| `gui/MainFrame.java` | ApiServer 생성/시작/종료 관리 |
