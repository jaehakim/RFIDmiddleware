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

            setStatus(ReaderStatus.CONNECTED);

            // 저장된 설정 적용
            applySavedConfig();

            return true;

        } catch (Exception e) {
            log("Connection exception: " + e.getMessage());
            setStatus(ReaderStatus.ERROR);
            reader = null;
            return false;
        }
    }

    /** 연결 후 설정파일에 저장된 값을 리더기에 적용 */
    private void applySavedConfig() {
        try {
            // 부저 설정 적용
            boolean savedBuzzer = config.isBuzzerEnabled();
            int buzzerResult = reader.setBuzzerEnable(savedBuzzer ? (byte) 1 : (byte) 0);
            if (buzzerResult == FixedReaderApiError.ErrNoError) {
                buzzerOn = savedBuzzer;
                for (ReaderConnectionListener l : listeners) {
                    l.onBuzzerChanged(this, buzzerOn);
                }
                log("Buzzer config applied: " + (savedBuzzer ? "ON" : "OFF"));
            }

            // 안테나 출력 적용
            int[] powers = config.getAntennaPowers();
            byte enableMask = 0;
            for (int i = 0; i < 4; i++) {
                if (powers[i] > 0) enableMask |= (1 << i);
            }
            byte[] antConfig = new byte[5];
            antConfig[0] = enableMask;
            for (int i = 0; i < 4; i++) antConfig[i + 1] = (byte) powers[i];
            int antResult = reader.setAntConfig(antConfig);
            if (antResult == FixedReaderApiError.ErrNoError) {
                log(String.format("Antenna config applied: power=[%d,%d,%d,%d]",
                    powers[0], powers[1], powers[2], powers[3]));
            }

            // 드웰시간 적용
            short dwellTime = (short) config.getDwellTime();
            int dwellResult = reader.setTxOnOffTime((byte) 0, dwellTime, (short) 0);
            if (dwellResult == FixedReaderApiError.ErrNoError) {
                log("Dwell time applied: " + dwellTime + "ms");
            }
        } catch (Exception e) {
            log("Apply saved config exception: " + e.getMessage());
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

    /** 경광등(릴레이) ON */
    public synchronized void lightOn() {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot control light (not connected)");
            return;
        }
        try {
            int result = reader.setRelayStatus((byte) 0, (byte) 1);
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

    /** 경광등(릴레이) OFF */
    public synchronized void lightOff() {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot control light (not connected)");
            return;
        }
        try {
            int result = reader.setRelayStatus((byte) 0, (byte) 0);
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

    /** 안테나 출력 설정 (powers: 안테나 1~4의 dBm 값, 길이 4) */
    public synchronized void setAntennaConfig(int[] powers) {
        if (reader == null || (status != ReaderStatus.CONNECTED && status != ReaderStatus.READING)) {
            log("Cannot set antenna config (not connected)");
            return;
        }
        try {
            // [Enable_mask(1)][Power1(1)][Power2(1)][Power3(1)][Power4(1)]
            byte enableMask = 0;
            for (int i = 0; i < powers.length && i < 4; i++) {
                if (powers[i] > 0) {
                    enableMask |= (1 << i);
                }
            }
            byte[] config = new byte[5];
            config[0] = enableMask;
            for (int i = 0; i < 4; i++) {
                config[i + 1] = (i < powers.length) ? (byte) powers[i] : 0;
            }
            int result = reader.setAntConfig(config);
            if (result == FixedReaderApiError.ErrNoError) {
                this.config.setAntennaPowers(powers.clone());
                log(String.format("Antenna config set: mask=0x%02X, power=[%d,%d,%d,%d]",
                    enableMask, config[1], config[2], config[3], config[4]));
            } else {
                log("Antenna config failed (error: " + result + ")");
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
            int result = reader.setTxOnOffTime((byte) 0, onTime, offTime);
            if (result == FixedReaderApiError.ErrNoError) {
                config.setDwellTime(onTime);
                log(String.format("Dwell time set: onTime=%dms, offTime=%dms", onTime, offTime));
            } else {
                log("Dwell time set failed (error: " + result + ")");
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
        System.out.println(String.format("[DEBUG-%s] cmdCode=0x%02X, len=%d, raw=%s",
            config.getName(), cmdCode, reportDataLen,
            HexUtils.bytesToHex(reportData, 0, Math.min(reportDataLen, 50))));

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
