package com.apulse.middleware.reader;

import com.apulse.fixedreaderlib.FixedReader;
import com.apulse.fixedreaderlib.FixedReaderApiError;
import com.apulse.middleware.config.ReaderConfig;
import com.apulse.middleware.util.HexUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ReaderConnection {
    private final ReaderConfig config;
    private FixedReader reader;
    private ReaderStatus status = ReaderStatus.DISCONNECTED;
    private boolean lightOn = false;
    private boolean buzzerOn = false;
    private String firmwareVersion = "";
    private final List<ReaderConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private final List<TagDataListener> tagListeners = new CopyOnWriteArrayList<>();

    public interface ReaderConnectionListener {
        void onStatusChanged(ReaderConnection connection, ReaderStatus oldStatus, ReaderStatus newStatus);
        void onLightChanged(ReaderConnection connection, boolean lightOn);
        void onBuzzerChanged(ReaderConnection connection, boolean buzzerOn);
        void onLog(ReaderConnection connection, String message);
    }

    public interface TagDataListener {
        void onTagRead(ReaderConnection connection, String epc, int rssi);
    }

    public ReaderConnection(ReaderConfig config) {
        this.config = config;
    }

    public ReaderConfig getConfig() { return config; }
    public ReaderStatus getStatus() { return status; }
    public boolean isLightOn() { return lightOn; }
    public boolean isBuzzerOn() { return buzzerOn; }
    public String getFirmwareVersionString() { return firmwareVersion; }

    public void addListener(ReaderConnectionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ReaderConnectionListener listener) {
        listeners.remove(listener);
    }

    public void addTagListener(TagDataListener listener) {
        tagListeners.add(listener);
    }

    public void removeTagListener(TagDataListener listener) {
        tagListeners.remove(listener);
    }

    private void setStatus(ReaderStatus newStatus) {
        ReaderStatus old = this.status;
        this.status = newStatus;
        for (ReaderConnectionListener l : listeners) {
            l.onStatusChanged(this, old, newStatus);
        }
    }

    private void log(String message) {
        System.out.println("[" + config.getName() + "] " + message);
        for (ReaderConnectionListener l : listeners) {
            l.onLog(this, message);
        }
    }

    /** 리더기에 연결 (별도 스레드에서 호출 권장) */
    public synchronized boolean connect() {
        if (status == ReaderStatus.CONNECTED || status == ReaderStatus.READING) {
            return true;
        }

        setStatus(ReaderStatus.CONNECTING);
        log("Connecting: " + config.getConnectString());

        try {
            reader = new FixedReader();
            int result = reader.connect(config.getConnectString(),
                (readerId, cmdCode, reportData, reportDataLen) -> {
                    handleReport(readerId, cmdCode, reportData, reportDataLen);
                }
            );

            if (result != FixedReaderApiError.ErrNoError) {
                log("Connection failed (error: " + result + ")");
                setStatus(ReaderStatus.ERROR);
                reader = null;
                return false;
            }

            // 연결 성공 후 잠시 대기
            Thread.sleep(1000);

            // 펌웨어 버전 확인
            String[] version = new String[1];
            reader.getFirmwareVersion(version);
            firmwareVersion = version[0] != null ? version[0] : "unknown";
            log("Connected (FW: " + firmwareVersion + ")");

            // 부저 PUSH + 안테나 설정 FETCH (CONNECTED 이벤트 전에 완료)
            applySavedConfig();

            setStatus(ReaderStatus.CONNECTED);

            return true;

        } catch (Exception e) {
            log("Connection exception: " + e.getMessage());
            setStatus(ReaderStatus.ERROR);
            reader = null;
            return false;
        }
    }

    /** 연결 후 부저는 PUSH, 안테나 설정은 리더기에서 FETCH */
    private void applySavedConfig() {
        try {
            // 부저 설정 적용 (읽기 API 없으므로 기존대로 PUSH)
            boolean savedBuzzer = config.isBuzzerEnabled();
            int buzzerResult = reader.setBuzzerEnable(savedBuzzer ? (byte) 1 : (byte) 0);
            if (buzzerResult == FixedReaderApiError.ErrNoError) {
                buzzerOn = savedBuzzer;
                for (ReaderConnectionListener l : listeners) {
                    l.onBuzzerChanged(this, buzzerOn);
                }
                log("Buzzer config applied: " + (savedBuzzer ? "ON" : "OFF"));
            } else {
                log("Buzzer config failed (error: " + buzzerResult + ")");
            }

            // 안테나/드웰 설정은 리더기에서 가져옴
            fetchAntennaConfigFromReader();
        } catch (Exception e) {
            log("Apply saved config exception: " + e.getMessage());
        }
    }

    /** 리더기의 실제 안테나 설정을 읽어서 ReaderConfig에 반영 */
    private void fetchAntennaConfigFromReader() {
        try {
            byte[] curConfig = new byte[256];
            int[] curLen = new int[]{curConfig.length};
            int getResult = reader.getAntConfig(curConfig, curLen);

            if (getResult != FixedReaderApiError.ErrNoError) {
                log("Could not fetch antenna config (error: " + getResult + "), keeping saved values");
                return;
            }

            logAntennaConfig("Fetched", curConfig, curLen[0]);

            if (curLen[0] == ANT_EXTENDED_LEN) {
                // 48바이트 확장 포맷: 4채널 x [enable(4)][dwellTime(4)][power(4)]
                int[] powers = new int[ANT_CHANNEL_COUNT];
                int firstActiveDwell = -1;

                for (int ch = 0; ch < ANT_CHANNEL_COUNT; ch++) {
                    int base = ch * ANT_CHANNEL_SIZE;
                    int enable = readInt32LE(curConfig, base);
                    int dwell  = readInt32LE(curConfig, base + 4);
                    int power  = readInt32LE(curConfig, base + 8);

                    powers[ch] = (enable != 0) ? power : 0;
                    if (enable != 0 && firstActiveDwell < 0) {
                        firstActiveDwell = dwell;
                    }
                }

                config.setAntennaPowers(powers);
                if (firstActiveDwell > 0) {
                    config.setDwellTime(firstActiveDwell);
                }

                log(String.format("Fetched antenna config: power=[%d,%d,%d,%d], dwellTime=%d",
                    powers[0], powers[1], powers[2], powers[3],
                    firstActiveDwell > 0 ? firstActiveDwell : config.getDwellTime()));
            } else {
                // 레거시 5바이트 포맷: [mask][p1][p2][p3][p4]
                if (curLen[0] >= 5) {
                    int mask = curConfig[0] & 0xFF;
                    int[] powers = new int[4];
                    for (int i = 0; i < 4; i++) {
                        powers[i] = ((mask & (1 << i)) != 0) ? (curConfig[i + 1] & 0xFF) : 0;
                    }
                    config.setAntennaPowers(powers);
                    log(String.format("Fetched antenna config (legacy): mask=0x%02X, power=[%d,%d,%d,%d]",
                        mask, powers[0], powers[1], powers[2], powers[3]));
                } else {
                    log("Unexpected antenna config length (" + curLen[0] + "B), keeping saved values");
                }
            }
        } catch (Exception e) {
            log("Fetch antenna config exception: " + e.getMessage());
        }
    }

    /** 12바이트/채널 확장 포맷: [enable(int32 LE)][dwellTime(int32 LE)][power(int32 LE)] x 4채널 = 48바이트 */
    private static final int ANT_CHANNEL_SIZE = 12;
    private static final int ANT_CHANNEL_COUNT = 4;
    private static final int ANT_EXTENDED_LEN = ANT_CHANNEL_SIZE * ANT_CHANNEL_COUNT;

    /** Little-Endian int32 읽기 */
    private static int readInt32LE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
             | ((buf[offset + 1] & 0xFF) << 8)
             | ((buf[offset + 2] & 0xFF) << 16)
             | ((buf[offset + 3] & 0xFF) << 24);
    }

    /** Little-Endian int32 쓰기 */
    private static void writeInt32LE(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /** 안테나 설정을 읽기 쉽게 로그 */
    private void logAntennaConfig(String label, byte[] cfg, int len) {
        if (len == ANT_EXTENDED_LEN) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s antenna config (extended %dB):", label, len));
            for (int ch = 0; ch < ANT_CHANNEL_COUNT; ch++) {
                int base = ch * ANT_CHANNEL_SIZE;
                int enable = readInt32LE(cfg, base);
                int dwell  = readInt32LE(cfg, base + 4);
                int power  = readInt32LE(cfg, base + 8);
                sb.append(String.format(" CH%d[en=%d,dwell=%d,pow=%d]", ch + 1, enable, dwell, power));
            }
            log(sb.toString());
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s antenna config (%dB): raw=[", label, len));
            for (int i = 0; i < Math.min(len, 16); i++) {
                if (i > 0) sb.append(",");
                sb.append(String.format("0x%02X", cfg[i] & 0xFF));
            }
            if (len > 16) sb.append("...");
            sb.append("]");
            log(sb.toString());
        }
    }

    /** 리더기 연결 해제 */
    public synchronized void disconnect() {
        if (reader != null) {
            try {
                if (status == ReaderStatus.READING) {
                    reader.stopInventory();
                    Thread.sleep(500);
                }
                reader.close();
            } catch (Exception e) {
                log("Disconnect exception: " + e.getMessage());
            }
            reader = null;
        }
        firmwareVersion = "";
        lightOn = false;
        buzzerOn = false;
        for (ReaderConnectionListener l : listeners) {
            l.onLightChanged(this, false);
            l.onBuzzerChanged(this, false);
        }
        setStatus(ReaderStatus.DISCONNECTED);
        log("Disconnected");
    }

    /** 인벤토리 시작 */
    public synchronized void startInventory() {
        if (reader == null || status != ReaderStatus.CONNECTED) {
            log("Cannot start inventory (not connected)");
            return;
        }
        try {
            reader.inventory();
            setStatus(ReaderStatus.READING);
            log("Inventory started");
        } catch (Exception e) {
            log("Inventory start exception: " + e.getMessage());
            setStatus(ReaderStatus.ERROR);
        }
    }

    /** 인벤토리 중지 */
    public synchronized void stopInventory() {
        if (reader == null || status != ReaderStatus.READING) {
            return;
        }
        try {
            reader.stopInventory();
            setStatus(ReaderStatus.CONNECTED);
            log("Inventory stopped");
        } catch (Exception e) {
            log("Inventory stop exception: " + e.getMessage());
        }
    }

    /** 경광등(릴레이1) ON - 빨간등 */
    public synchronized void lightOn() {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot control light (not connected)");
            return;
        }
        try {
            int result = reader.setRelayStatus((byte) 2, (byte) 1);
            if (result == FixedReaderApiError.ErrNoError) {
                lightOn = true;
                for (ReaderConnectionListener l : listeners) {
                    l.onLightChanged(this, true);
                }
                log("Light ON");
            } else {
                log("Light ON failed (error: " + result + ")");
            }
        } catch (Exception e) {
            log("Light ON exception: " + e.getMessage());
        }
    }

    /** 경광등(릴레이1) OFF - 빨간등 */
    public synchronized void lightOff() {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot control light (not connected)");
            return;
        }
        try {
            int result = reader.setRelayStatus((byte) 2, (byte) 0);
            if (result == FixedReaderApiError.ErrNoError) {
                lightOn = false;
                for (ReaderConnectionListener l : listeners) {
                    l.onLightChanged(this, false);
                }
                log("Light OFF");
            } else {
                log("Light OFF failed (error: " + result + ")");
            }
        } catch (Exception e) {
            log("Light OFF exception: " + e.getMessage());
        }
    }

    /** 부저 ON */
    public synchronized void buzzerOn() {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot control buzzer (not connected)");
            return;
        }
        try {
            int result = reader.setBuzzerEnable((byte) 1);
            if (result == FixedReaderApiError.ErrNoError) {
                buzzerOn = true;
                config.setBuzzerEnabled(true);
                for (ReaderConnectionListener l : listeners) {
                    l.onBuzzerChanged(this, true);
                }
                log("Buzzer ON");
            } else {
                log("Buzzer ON failed (error: " + result + ")");
            }
        } catch (Exception e) {
            log("Buzzer ON exception: " + e.getMessage());
        }
    }

    /** 부저 OFF */
    public synchronized void buzzerOff() {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot control buzzer (not connected)");
            return;
        }
        try {
            int result = reader.setBuzzerEnable((byte) 0);
            if (result == FixedReaderApiError.ErrNoError) {
                buzzerOn = false;
                config.setBuzzerEnabled(false);
                for (ReaderConnectionListener l : listeners) {
                    l.onBuzzerChanged(this, false);
                }
                log("Buzzer OFF");
            } else {
                log("Buzzer OFF failed (error: " + result + ")");
            }
        } catch (Exception e) {
            log("Buzzer OFF exception: " + e.getMessage());
        }
    }

    /** 안테나 출력 설정 (powers: 안테나 1~4의 출력 값, 길이 4) */
    public synchronized void setAntennaConfig(int[] powers) {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot set antenna config (not connected)");
            return;
        }
        try {
            // 현재 설정 조회하여 포맷 감지
            byte[] curConfig = new byte[256];
            int[] curLen = new int[]{curConfig.length};
            int getResult = reader.getAntConfig(curConfig, curLen);

            if (getResult == FixedReaderApiError.ErrNoError && curLen[0] == ANT_EXTENDED_LEN) {
                // 48바이트 확장 포맷: [enable][dwellTime][power] x 4채널
                byte[] newConfig = new byte[ANT_EXTENDED_LEN];
                System.arraycopy(curConfig, 0, newConfig, 0, ANT_EXTENDED_LEN);

                for (int ch = 0; ch < ANT_CHANNEL_COUNT; ch++) {
                    int base = ch * ANT_CHANNEL_SIZE;
                    int enable = readInt32LE(newConfig, base);
                    if (enable != 0 && ch < powers.length) {
                        writeInt32LE(newConfig, base + 8, powers[ch]);
                    }
                }

                int result = reader.setAntConfig(newConfig);
                if (result == FixedReaderApiError.ErrNoError) {
                    this.config.setAntennaPowers(powers.clone());
                    logAntennaConfig("Applied", newConfig, ANT_EXTENDED_LEN);
                } else {
                    log("Antenna config failed (error: " + result + ")");
                }
            } else {
                // 레거시 5바이트 포맷
                byte enableMask = 0;
                for (int i = 0; i < powers.length && i < 4; i++) {
                    if (powers[i] > 0) enableMask |= (1 << i);
                }
                byte[] cfg = new byte[5];
                cfg[0] = enableMask;
                for (int i = 0; i < 4; i++) {
                    cfg[i + 1] = (i < powers.length) ? (byte) powers[i] : 0;
                }
                int result = reader.setAntConfig(cfg);
                if (result == FixedReaderApiError.ErrNoError) {
                    this.config.setAntennaPowers(powers.clone());
                    log(String.format("Antenna config set: mask=0x%02X, power=[%d,%d,%d,%d]",
                        enableMask, cfg[1], cfg[2], cfg[3], cfg[4]));
                } else {
                    log("Antenna config failed (error: " + result + ")");
                }
            }
        } catch (Exception e) {
            log("Antenna config exception: " + e.getMessage());
        }
    }

    /** 드웰시간 설정 (onTime/offTime: ms 단위) */
    public synchronized void setDwellTime(short onTime, short offTime) {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot set dwell time (not connected)");
            return;
        }
        try {
            // 확장 포맷(48B) 리더기는 setAntConfig으로 dwell 설정
            byte[] curConfig = new byte[256];
            int[] curLen = new int[]{curConfig.length};
            int getResult = reader.getAntConfig(curConfig, curLen);

            if (getResult == FixedReaderApiError.ErrNoError && curLen[0] == ANT_EXTENDED_LEN) {
                byte[] newConfig = new byte[ANT_EXTENDED_LEN];
                System.arraycopy(curConfig, 0, newConfig, 0, ANT_EXTENDED_LEN);

                for (int ch = 0; ch < ANT_CHANNEL_COUNT; ch++) {
                    int base = ch * ANT_CHANNEL_SIZE;
                    int enable = readInt32LE(newConfig, base);
                    if (enable != 0) {
                        writeInt32LE(newConfig, base + 4, onTime);
                    }
                }

                int setResult = reader.setAntConfig(newConfig);
                if (setResult == FixedReaderApiError.ErrNoError) {
                    config.setDwellTime(onTime);
                    log(String.format("Dwell time set via antConfig: %dms", onTime));
                } else {
                    log("Dwell time set failed (error: " + setResult + ")");
                }
            } else {
                // 레거시 리더기는 setTxOnOffTime 사용
                int result = reader.setTxOnOffTime((byte) 0, onTime, offTime);
                if (result == FixedReaderApiError.ErrNoError) {
                    config.setDwellTime(onTime);
                    log(String.format("Dwell time set: onTime=%dms, offTime=%dms", onTime, offTime));
                } else {
                    log("Dwell time set failed (error: " + result + ")");
                }
            }
        } catch (Exception e) {
            log("Dwell time exception: " + e.getMessage());
        }
    }

    /**
     * 리더기 보고 데이터 처리.
     * 인벤토리 보고 포맷:
     *   [PC(2)][EPC(N)][CRC(2)][Antenna(1)][RSSI(1)][Count(1)]
     * PC bits[15:11] = EPC 워드 수
     */
    private void handleReport(int readerId, int cmdCode, byte[] reportData, int reportDataLen) {
        System.out.println(String.format("[DEBUG-%s] cmdCode=0x%02X, len=%d, status=%s, raw=%s",
            config.getName(), cmdCode, reportDataLen, status,
            HexUtils.bytesToHex(reportData, 0, Math.min(reportDataLen, 50))));

        // 인벤토리 중이 아니면 태그 데이터로 처리하지 않음
        if (status != ReaderStatus.READING) {
            return;
        }

        if (reportData == null || reportDataLen < 7) {
            return;
        }

        try {
            int offset = 0;

            // PC (Protocol Control) 2 bytes
            int pc = ((reportData[offset] & 0xFF) << 8) | (reportData[offset + 1] & 0xFF);
            offset += 2;

            // EPC word count from PC upper 5 bits
            int epcWordCount = (pc >> 11) & 0x1F;
            int epcByteLen = epcWordCount * 2;

            if (epcByteLen <= 0 || offset + epcByteLen + 3 > reportDataLen) {
                return;
            }

            String epc = HexUtils.bytesToHex(reportData, offset, epcByteLen);
            offset += epcByteLen;

            // CRC (2 bytes) - skip
            offset += 2;

            // Antenna (1 byte)
            int antenna = (offset < reportDataLen) ? (reportData[offset] & 0xFF) : 0;
            offset += 1;

            // RSSI (1 byte, signed)
            int rssi = 0;
            if (offset < reportDataLen) {
                rssi = reportData[offset];  // signed byte
            }

            System.out.println(String.format("[DEBUG-%s] EPC=%s, RSSI=%d, Ant=%d, PC=0x%04X",
                config.getName(), epc, rssi, antenna, pc));

            for (TagDataListener l : tagListeners) {
                l.onTagRead(this, epc, rssi);
            }
        } catch (Exception e) {
            log("Report parse error: " + HexUtils.bytesToHex(reportData, 0, reportDataLen));
        }
    }
}
