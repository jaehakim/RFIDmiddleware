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
    private String firmwareVersion = "";
    private final List<ReaderConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private final List<TagDataListener> tagListeners = new CopyOnWriteArrayList<>();

    public interface ReaderConnectionListener {
        void onStatusChanged(ReaderConnection connection, ReaderStatus oldStatus, ReaderStatus newStatus);
        void onLightChanged(ReaderConnection connection, boolean lightOn);
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
            return true;

        } catch (Exception e) {
            log("Connection exception: " + e.getMessage());
            setStatus(ReaderStatus.ERROR);
            reader = null;
            return false;
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
        for (ReaderConnectionListener l : listeners) {
            l.onLightChanged(this, false);
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

    /**
     * 리더기 보고 데이터 처리.
     * 인벤토리 보고 포맷:
     * reportData에서 EPC와 RSSI를 추출한다.
     * 일반적인 인벤토리 보고 cmdCode 범위: 0x01xx 계열
     */
    private void handleReport(int readerId, int cmdCode, byte[] reportData, int reportDataLen) {
        // DEBUG: always print raw callback data
        System.out.println(String.format("[DEBUG-%s] cmdCode=0x%02X, len=%d, raw=%s",
            config.getName(), cmdCode, reportDataLen,
            HexUtils.bytesToHex(reportData, 0, Math.min(reportDataLen, 50))));

        if (reportData == null || reportDataLen < 6) {
            return;
        }

        try {
            int offset = 0;
            int rssi = 0;

            // Check if reportData includes full frame (starts with 434D)
            if (reportDataLen > 7
                && (reportData[0] & 0xFF) == 0x43
                && (reportData[1] & 0xFF) == 0x4D) {
                // Full frame: [434D][cmdCode(1)][len(2)][data...][crc(2)]
                int dataLen = ((reportData[3] & 0xFF) << 8) | (reportData[4] & 0xFF);
                offset = 5; // skip header(2) + cmdCode(1) + len(2)
                reportDataLen = Math.min(offset + dataLen, reportDataLen);
            }

            if (reportDataLen - offset < 5) {
                return;
            }

            // First byte of data: RSSI or antenna info
            rssi = reportData[offset] & 0xFF;
            if (rssi > 127) rssi = rssi - 256;
            offset += 1;

            // PC (Protocol Control) 2 bytes
            int pc = ((reportData[offset] & 0xFF) << 8) | (reportData[offset + 1] & 0xFF);
            offset += 2;

            // EPC word count from PC upper 5 bits
            int epcWordCount = (pc >> 11) & 0x1F;
            int epcByteLen = epcWordCount * 2;

            if (epcByteLen > 0 && offset + epcByteLen <= reportDataLen) {
                String epc = HexUtils.bytesToHex(reportData, offset, epcByteLen);
                System.out.println(String.format("[DEBUG-%s] EPC=%s, RSSI=%d, PC=0x%04X, words=%d",
                    config.getName(), epc, rssi, pc, epcWordCount));
                for (TagDataListener l : tagListeners) {
                    l.onTagRead(this, epc, rssi);
                }
            } else if (reportDataLen - offset >= 4) {
                String epc = HexUtils.bytesToHex(reportData, offset, reportDataLen - offset);
                System.out.println(String.format("[DEBUG-%s] EPC(fallback)=%s, RSSI=%d",
                    config.getName(), epc, rssi));
                for (TagDataListener l : tagListeners) {
                    l.onTagRead(this, epc, rssi);
                }
            }
        } catch (Exception e) {
            log("Report parse error: " + HexUtils.bytesToHex(reportData, 0, reportDataLen));
        }
    }
}
