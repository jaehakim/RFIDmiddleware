import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *  Fixed Reader Simulator
 *
 * Simulates an (EX10-based) UHF RFID fixed reader for SDK testing.
 * Protocol: CM-frame over TCP (port 20058) + UDP discovery (port 17777)
 *
 * Frame format:
 *   [0x43][0x4D][CmdCode][Address][DataLen_Lo][DataLen_Hi][Data...][BCC]
 *   BCC = XOR of all data bytes
 *
 * Usage:
 *   java ReaderSimulator                        # TCP server on 20058, UDP on 17777
 *   java ReaderSimulator --port 20058           # custom TCP port
 *   java ReaderSimulator --connect 127.0.0.1:9000  # connect TO SDK listen port
 */
public class ReaderSimulator {

    // ── Frame constants ──────────────────────────────────────────────────
    static final byte HDR0 = 0x43; // 'C'
    static final byte HDR1 = 0x4D; // 'M'

    // ── Command codes ────────────────────────────────────────────────────
    static final int CMD_SET_MAC_ADDRESS          = 5;
    static final int CMD_GET_SN                   = 6;
    static final int CMD_HEARTBEAT                = 16;
    static final int CMD_START_INVENTORY           = 42;
    static final int CMD_STOP_INVENTORY            = 43;
    static final int CMD_GET_VERSION               = 49;
    static final int CMD_GET_ANT_CONFIG            = 50;
    static final int CMD_SET_ANT_CONFIG            = 51;
    static final int CMD_SET_Q_VALUE               = 65;
    static final int CMD_GET_Q_VALUE               = 66;
    static final int CMD_SET_QUERY_CONFIG          = 67;
    static final int CMD_GET_QUERY_CONFIG          = 68;
    static final int CMD_SET_INVENTORY_CONFIG      = 71;
    static final int CMD_GET_INVENTORY_CONFIG      = 72;
    static final int CMD_SET_TX_ONOFF              = 73;
    static final int CMD_GET_TX_ONOFF              = 74;
    static final int CMD_SELECT_TAG                = 96;
    static final int CMD_CANCEL_SELECT_TAG         = 97;
    static final int CMD_GET_DEVICE_NAME           = 101;
    static final int CMD_SET_DEVICE_NAME           = 102;
    static final int CMD_DEVICE_INFO_REPORT        = 103;
    static final int CMD_GET_SELECT_TAG            = 106;
    static final int CMD_SET_WORK_MODE             = 112;
    static final int CMD_SET_COMM_MODE             = 113;
    static final int CMD_SET_RS485_ADDR            = 114;
    static final int CMD_SET_TRIGGER_DELAY         = 115;
    static final int CMD_SET_AUTO_READ_PARAMS      = 116;
    static final int CMD_SET_RSSI_FILTER           = 117;
    static final int CMD_SET_TAG_FILTER            = 118;
    static final int CMD_SET_BUZZER                = 121;
    static final int CMD_SET_BAUD                  = 122;
    static final int CMD_SET_WG_PARAMS             = 123;
    static final int CMD_SET_RTC_TIME              = 124;
    static final int CMD_GET_WORK_MODE             = 128;
    static final int CMD_GET_COMM_MODE             = 129;
    static final int CMD_GET_RS485_ADDR            = 130;
    static final int CMD_GET_TRIGGER_DELAY         = 131;
    static final int CMD_GET_AUTO_READ_PARAMS      = 132;
    static final int CMD_GET_RSSI_FILTER           = 133;
    static final int CMD_GET_TAG_FILTER            = 134;
    static final int CMD_GET_BUZZER                = 137;
    static final int CMD_GET_BAUD                  = 138;
    static final int CMD_GET_WG_PARAMS             = 139;
    static final int CMD_GET_RTC_TIME              = 140;
    static final int CMD_GET_ALL_SYSTEM_PARAMS     = 143;
    static final int CMD_GET_READER_STATE          = 240;
    static final int CMD_READ_OEM_REGISTER         = 244;
    static final int CMD_WRITE_OEM_REGISTER        = 245;
    static final int CMD_SET_LINK_PROFILE          = 246;
    static final int CMD_GET_LINK_PROFILE          = 247;
    static final int CMD_RESET_READER              = 251;

    // ── Simulated device info ────────────────────────────────────────────
    static final String DEVICE_NAME       = "-EX10";
    static final String FIRMWARE_VERSION  = "V2.05.01";
    static final String SERIAL_NUMBER     = "AP241200001";
    static final byte[] MAC_BYTES         = {(byte)0xAA,(byte)0xBB,(byte)0xCC,(byte)0xDD,(byte)0xEE,(byte)0xFF};

    // GlobalBand: 0=Unknown,1=Korea,2=ETSI,3=FCC,4=China ...
    static final int GLOBAL_BAND_VALUE    = 1; // Korea

    // ── TAG_DATA.txt 에서 로딩할 EPC 리스트 ─────────────────────────────
    static final String TAG_DATA_FILE = "TAG_DATA.txt";
    volatile List<String> epcList = new ArrayList<>();
    long tagFileLastModified = 0;

    // ── State ────────────────────────────────────────────────────────────
    final AtomicBoolean running         = new AtomicBoolean(true);
    final AtomicBoolean inventoryActive = new AtomicBoolean(false);
    ScheduledExecutorService scheduler;
    ScheduledFuture<?> inventoryTask;
    OutputStream currentOut; // guarded by synchronized(outLock)
    final Object outLock = new Object();
    byte currentAddress = 0x00;
    int tagReportCount = 0;
    final Random rng = new Random();

    // ── Configuration ────────────────────────────────────────────────────
    int tcpPort   = 20058;
    int udpPort   = 17777;
    String connectTarget = null; // "host:port" for active mode

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) throws Exception {
        ReaderSimulator sim = new ReaderSimulator();
        sim.parseArgs(args);
        sim.run();
    }

    void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    tcpPort = Integer.parseInt(args[++i]);
                    break;
                case "--udp-port":
                    udpPort = Integer.parseInt(args[++i]);
                    break;
                case "--connect":
                    connectTarget = args[++i];
                    break;
                case "--help":
                    System.out.println("Usage: java ReaderSimulator [options]");
                    System.out.println("  --port <port>          TCP listen port (default: 20058)");
                    System.out.println("  --udp-port <port>      UDP discovery port (default: 17777)");
                    System.out.println("  --connect <host:port>  Active mode: connect TO SDK listen port");
                    System.out.println("  --help                 Show this help");
                    System.exit(0);
                    break;
            }
        }
    }

    void loadTagData() {
        Path path = Paths.get(TAG_DATA_FILE);
        try {
            long mod = Files.getLastModifiedTime(path).toMillis();
            if (mod == tagFileLastModified) return; // 변경 없음
            tagFileLastModified = mod;

            List<String> newList = new ArrayList<>();
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && trimmed.matches("[0-9a-fA-F]+") && trimmed.length() % 2 == 0) {
                    newList.add(trimmed.toUpperCase());
                }
            }
            if (!newList.isEmpty()) {
                epcList = newList; // volatile 교체
                log("FILE", "Loaded " + epcList.size() + " EPC tags from " + TAG_DATA_FILE);
            }
        } catch (IOException e) {
            if (epcList.isEmpty()) {
                log("ERR", "No valid EPC data! Add hex EPC strings to " + TAG_DATA_FILE);
                System.exit(1);
            }
        }
    }

    void run() throws Exception {
        scheduler = Executors.newScheduledThreadPool(2);

        loadTagData();
        printBanner();

        // Start UDP discovery responder in background
        Thread udpThread = new Thread(this::udpDiscoveryResponder, "UDP-Discovery");
        udpThread.setDaemon(true);
        udpThread.start();

        if (connectTarget != null) {
            // Active mode: connect to SDK's listen port
            runActiveMode();
        } else {
            // Passive mode: listen for SDK connections
            runPassiveMode();
        }
    }

    void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        Fixed Reader Simulator              ║");
        System.out.println("║        Device : " + pad(DEVICE_NAME, 39) + "║");
        System.out.println("║        F/W    : " + pad(FIRMWARE_VERSION, 39) + "║");
        System.out.println("║        S/N    : " + pad(SERIAL_NUMBER, 39) + "║");
        System.out.println("║        Tags   : " + pad(epcList.size() + " tags from " + TAG_DATA_FILE, 39) + "║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        if (connectTarget != null) {
            System.out.println("║  Mode: ACTIVE  (connecting to SDK at " + pad(connectTarget + ")", 19) + "║");
        } else {
            System.out.println("║  Mode: PASSIVE (TCP:" + pad(tcpPort + ", UDP:" + udpPort + ")", 36) + "║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    static String pad(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    // =====================================================================
    //  PASSIVE MODE: TCP Server
    // =====================================================================
    void runPassiveMode() throws Exception {
        try (ServerSocket ss = new ServerSocket(tcpPort)) {
            log("TCP", "Listening on port " + tcpPort + " (waiting for SDK connection...)");
            log("UDP", "Discovery responder on port " + udpPort);

            while (running.get()) {
                Socket client = ss.accept();
                client.setTcpNoDelay(true);
                log("TCP", "SDK connected from " + client.getRemoteSocketAddress());
                Thread t = new Thread(() -> handleConnection(client), "Client-Handler");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    // =====================================================================
    //  ACTIVE MODE: Connect to SDK listen port
    // =====================================================================
    void runActiveMode() throws Exception {
        String[] parts = connectTarget.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        log("TCP", "Connecting to SDK at " + host + ":" + port + " ...");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setTcpNoDelay(true);
            log("TCP", "Connected to SDK at " + socket.getRemoteSocketAddress());
            handleConnection(socket);
        } catch (Exception e) {
            log("ERR", "Connection failed: " + e.getMessage());
        }
    }

    // =====================================================================
    //  CONNECTION HANDLER (shared between passive/active)
    // =====================================================================
    void handleConnection(Socket socket) {
        OutputStream out = null;
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(500);
            InputStream in = socket.getInputStream();
            out = socket.getOutputStream();
            synchronized (outLock) {
                currentOut = out;
            }

            byte[] buf = new byte[4096];
            int bufLen = 0;

            while (running.get() && !socket.isClosed()) {
                // 블로킹 read (타임아웃 500ms)
                int n;
                try {
                    n = in.read(buf, bufLen, buf.length - bufLen);
                } catch (SocketTimeoutException e) {
                    continue; // 타임아웃 - 계속 대기
                }
                if (n == -1) {
                    log("TCP", "SDK disconnected");
                    break;
                }
                bufLen += n;

                // 프레임 파싱
                int pos = 0;
                while (pos + 7 <= bufLen) {
                    // CM 헤더 찾기
                    if (buf[pos] != HDR0 || buf[pos + 1] != HDR1) {
                        pos++;
                        continue;
                    }

                    int dataLen = (buf[pos + 4] & 0xFF) | ((buf[pos + 5] & 0xFF) << 8);
                    int totalLen = 7 + dataLen;
                    if (pos + totalLen > bufLen) break; // 불완전 프레임 - 더 읽기

                    int cmdCode = buf[pos + 2] & 0xFF;
                    byte address = buf[pos + 3];
                    currentAddress = address;

                    byte[] data = new byte[dataLen];
                    System.arraycopy(buf, pos + 6, data, 0, dataLen);

                    pos += totalLen; // 프레임 소비

                    handleCommand(cmdCode, address, data, out);
                }

                // 남은 데이터를 버퍼 앞으로 이동
                if (pos > 0) {
                    bufLen -= pos;
                    if (bufLen > 0) System.arraycopy(buf, pos, buf, 0, bufLen);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log("TCP", "Connection error: " + e.getMessage());
            }
        } finally {
            stopInventory();
            synchronized (outLock) {
                currentOut = null;
            }
            try { socket.close(); } catch (IOException ignored) {}
            log("TCP", "Connection closed");
        }
    }

    // =====================================================================
    //  COMMAND HANDLER
    // =====================================================================
    void handleCommand(int cmdCode, byte address, byte[] data, OutputStream out) throws IOException {
        String cmdName = cmdName(cmdCode);
        log("<<RX", String.format("cmd=%d(%s) addr=%d dataLen=%d data=%s",
                cmdCode, cmdName, address & 0xFF, data.length, bytesToHex(data)));

        byte[] respData;

        switch (cmdCode) {
            case CMD_GET_DEVICE_NAME:
                respData = DEVICE_NAME.getBytes(StandardCharsets.US_ASCII);
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_VERSION:
                respData = FIRMWARE_VERSION.getBytes(StandardCharsets.US_ASCII);
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_SN:
                respData = SERIAL_NUMBER.getBytes(StandardCharsets.US_ASCII);
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_START_INVENTORY:
                log("RFID", "*** Inventory START ***");
                startInventory(out);
                // No immediate response for async command
                break;

            case CMD_STOP_INVENTORY:
                log("RFID", "*** Inventory STOP *** (reported " + tagReportCount + " tags)");
                stopInventory();
                // Send stop inventory response with statistics
                respData = buildInventoryStats();
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_ALL_SYSTEM_PARAMS:
                respData = buildAllSystemParams();
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_READ_OEM_REGISTER:
                respData = handleReadOemRegister(data);
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_WRITE_OEM_REGISTER:
                sendResponse(cmdCode, address, new byte[]{0x00}, out);
                break;

            case CMD_GET_ANT_CONFIG:
                respData = buildAntConfig();
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_SET_ANT_CONFIG:
                sendResponse(cmdCode, address, new byte[]{0x00}, out);
                break;

            case CMD_GET_READER_STATE:
                respData = new byte[]{(byte)(inventoryActive.get() ? 0x01 : 0x00)};
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_Q_VALUE:
                respData = new byte[]{0x04}; // Q=4
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_QUERY_CONFIG:
                respData = new byte[]{0x00, 0x04, 0x00, 0x00}; // default query config
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_INVENTORY_CONFIG:
                respData = new byte[]{0x00, 0x01, 0x00, 0x00}; // default inventory config
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_TX_ONOFF:
                respData = new byte[]{0x00, 0x00, 0x00, 0x00}; // TX on/off time defaults
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_LINK_PROFILE:
                respData = new byte[]{0x01}; // link profile index
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_WORK_MODE:
                respData = new byte[]{0x00}; // normal mode
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_COMM_MODE:
                respData = new byte[]{0x00}; // TCP mode
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_BAUD:
                // 115200 in LE: 0x00 0x01 0xC2 0x00
                respData = new byte[]{0x00, (byte)0xC2, 0x01, 0x00};
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_RSSI_FILTER:
                respData = new byte[]{0x00, 0x00}; // no filter
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_TAG_FILTER:
                respData = new byte[]{0x00}; // no filter
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_BUZZER:
                respData = new byte[]{0x01}; // buzzer enabled
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_RTC_TIME:
                respData = buildRtcTime();
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_RS485_ADDR:
                respData = new byte[]{0x01};
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_TRIGGER_DELAY:
                respData = new byte[]{0x00, 0x00};
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_AUTO_READ_PARAMS:
                respData = new byte[]{0x00, 0x00, 0x00, 0x00};
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_WG_PARAMS:
                respData = new byte[]{0x00, 0x1A, 0x00, 0x00}; // Wiegand 26
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_GET_SELECT_TAG:
                respData = new byte[]{0x00}; // no select mask
                sendResponse(cmdCode, address, respData, out);
                break;

            case CMD_HEARTBEAT:
                // Heartbeat acknowledge
                sendResponse(cmdCode, address, new byte[0], out);
                break;

            case CMD_RESET_READER:
                log("SYS", "Reader reset requested");
                sendResponse(cmdCode, address, new byte[]{0x00}, out);
                break;

            default:
                // Generic ACK for set/unknown commands
                log("WARN", "Unknown/unhandled command " + cmdCode + ", sending generic ACK");
                sendResponse(cmdCode, address, new byte[]{0x00}, out);
                break;
        }
    }

    // =====================================================================
    //  INVENTORY SIMULATION
    // =====================================================================
    void startInventory(OutputStream out) {
        if (inventoryActive.getAndSet(true)) {
            log("RFID", "Inventory already running");
            return;
        }
        tagReportCount = 0;

        inventoryTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!inventoryActive.get()) return;
                sendTagReport(out);
            } catch (Exception e) {
                log("ERR", "Inventory report error: " + e.getMessage());
                stopInventory();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS); // 1초 간격
    }

    void stopInventory() {
        inventoryActive.set(false);
        if (inventoryTask != null) {
            inventoryTask.cancel(false);
            inventoryTask = null;
        }
    }

    void sendTagReport(OutputStream out) throws IOException {
        loadTagData(); // 파일 변경 시 자동 리로딩
        int count = 1 + rng.nextInt(5);

        for (int t = 0; t < count; t++) {
            // TAG_DATA.txt에서 랜덤 행 선택
            String epcHex = epcList.get(rng.nextInt(epcList.size()));
            byte[] epc = hexToBytes(epcHex);

            int antenna = 1 + rng.nextInt(4);
            int rssi = -(30 + rng.nextInt(36));

            // PC 계산: EPC 바이트 수 → 워드 수 → bits[15:11]
            int epcWords = (epc.length + 1) / 2;
            byte[] pc = new byte[]{(byte)(epcWords << 3), 0x00};

            // CRC-16: ~CRC-CCITT over PC+EPC
            byte[] pcEpc = new byte[pc.length + epc.length];
            System.arraycopy(pc, 0, pcEpc, 0, pc.length);
            System.arraycopy(epc, 0, pcEpc, pc.length, epc.length);
            short crc = (short) ~calcEPCCRC(pcEpc, 0, pcEpc.length);

            // SDK 포맷: [PC(2)][EPC(N)][CRC(2)][Ant(1)][RSSI(1)][Count(1)]
            int dataLen = 2 + epc.length + 2 + 3; // PC+EPC+CRC+Ant+RSSI+Count
            byte[] reportData = new byte[dataLen];
            int off = 0;
            reportData[off++] = pc[0];
            reportData[off++] = pc[1];
            System.arraycopy(epc, 0, reportData, off, epc.length);
            off += epc.length;
            reportData[off++] = (byte)((crc >> 8) & 0xFF);
            reportData[off++] = (byte)(crc & 0xFF);
            reportData[off++] = (byte) antenna;
            reportData[off++] = (byte) rssi;
            reportData[off]   = 0x01;

            sendResponse(CMD_START_INVENTORY, currentAddress, reportData, out);
            tagReportCount++;

            log("TAG", String.format("Ant:%d  RSSI:%ddBm  EPC:%s",
                    antenna, rssi, epcHex));
        }
    }

    byte[] buildInventoryStats() {
        // [totalReads(4 LE)] [uniqueTags(2 LE)] [duration_ms(4 LE)]
        byte[] stats = new byte[10];
        u32ToLE(tagReportCount, stats, 0);
        u16ToLE((short) epcList.size(), stats, 4);
        u32ToLE(tagReportCount * 1000, stats, 6); // approximate duration
        return stats;
    }

    // =====================================================================
    //  OEM REGISTER HANDLING
    // =====================================================================
    byte[] handleReadOemRegister(byte[] reqData) {
        if (reqData.length >= 2) {
            int regAddr = (reqData[0] & 0xFF) | ((reqData[1] & 0xFF) << 8);
            log("REG", String.format("Read OEM register 0x%04X", regAddr));

            // Register 0x0006 is commonly used for GlobalBand
            if (regAddr == 0x0006 || regAddr == 0x0000) {
                byte[] val = new byte[2];
                u16ToLE((short) GLOBAL_BAND_VALUE, val, 0);
                return val;
            }
        }
        // Default: return zeros
        return new byte[]{0x00, 0x00};
    }

    // =====================================================================
    //  SYSTEM PARAMS
    // =====================================================================
    byte[] buildAllSystemParams() {
        // Build a mock system params block
        // Format varies by reader, but typically includes:
        // [WorkMode(1)] [CommMode(1)] [Band(1)] [TxPower per ant(4x1)]
        // [AntEnable(1)] [QValue(1)] [BuzzerEn(1)] [Baud(4)]
        // ... and more
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(0x00); // work mode: normal
            bos.write(0x00); // comm mode: TCP
            bos.write(GLOBAL_BAND_VALUE); // band
            bos.write(new byte[]{0x1E, 0x1E, 0x1E, 0x1E}); // TX power 30dBm x 4 antennas
            bos.write(0x0F); // antenna enable mask (4 antennas)
            bos.write(0x04); // Q value
            bos.write(0x01); // buzzer enabled
            bos.write(new byte[]{0x00, (byte)0xC2, 0x01, 0x00}); // baud 115200 LE
            bos.write(0x00); // RSSI filter off
            bos.write(0x00); // tag filter off
            bos.write(new byte[]{0x00, 0x00}); // trigger delay
            bos.write(0x01); // link profile
            // Serial number
            byte[] sn = SERIAL_NUMBER.getBytes(StandardCharsets.US_ASCII);
            bos.write(sn.length);
            bos.write(sn);
            // Device name
            byte[] dn = DEVICE_NAME.getBytes(StandardCharsets.US_ASCII);
            bos.write(dn.length);
            bos.write(dn);
        } catch (IOException ignored) {}
        return bos.toByteArray();
    }

    byte[] buildAntConfig() {
        // [AntEnable(1)] [Power1(1)] [Power2(1)] [Power3(1)] [Power4(1)]
        return new byte[]{0x0F, 0x1E, 0x1E, 0x1E, 0x1E}; // 4 antennas, 30dBm each
    }

    byte[] buildRtcTime() {
        Calendar cal = Calendar.getInstance();
        return new byte[]{
            (byte)(cal.get(Calendar.YEAR) - 2000),
            (byte)(cal.get(Calendar.MONTH) + 1),
            (byte) cal.get(Calendar.DAY_OF_MONTH),
            (byte) cal.get(Calendar.HOUR_OF_DAY),
            (byte) cal.get(Calendar.MINUTE),
            (byte) cal.get(Calendar.SECOND)
        };
    }

    // =====================================================================
    //  FRAME BUILD & SEND
    // =====================================================================
    byte[] buildFrame(int cmdCode, byte address, byte[] data) {
        int dataLen = (data != null) ? data.length : 0;
        byte[] frame = new byte[7 + dataLen];
        frame[0] = HDR0;
        frame[1] = HDR1;
        frame[2] = (byte) cmdCode;
        frame[3] = address;
        frame[4] = (byte) (dataLen & 0xFF);        // LE low
        frame[5] = (byte) ((dataLen >> 8) & 0xFF);  // LE high
        if (dataLen > 0) {
            System.arraycopy(data, 0, frame, 6, dataLen);
        }
        frame[6 + dataLen] = bcc(frame, 6, dataLen);
        return frame;
    }

    void sendResponse(int cmdCode, byte address, byte[] data, OutputStream out) throws IOException {
        byte[] frame = buildFrame(cmdCode, address, data);
        synchronized (outLock) {
            if (out != null) {
                out.write(frame);
                out.flush();
            }
        }
        log(">>TX", String.format("cmd=%d(%s) dataLen=%d data=%s",
                cmdCode, cmdName(cmdCode), data.length, bytesToHex(data)));
    }

    // =====================================================================
    //  UDP DISCOVERY RESPONDER
    // =====================================================================
    void udpDiscoveryResponder() {
        if (udpPort <= 0) { log("UDP", "Discovery disabled"); return; }
        try (DatagramSocket udpSocket = new DatagramSocket(udpPort)) {
            udpSocket.setBroadcast(true);
            udpSocket.setSoTimeout(1000);
            byte[] recvBuf = new byte[512];

            log("UDP", "Discovery responder listening on port " + udpPort);

            while (running.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                    udpSocket.receive(packet);

                    log("UDP", "Discovery request from " + packet.getSocketAddress()
                            + " (" + packet.getLength() + " bytes)");

                    // Build discovery response string
                    String localIp = getLocalIp();
                    String macHex = bytesToHex(MAC_BYTES);
                    String response = String.format(
                        "ip=%s;mac_address=%s;version=%s;name=%s;net_mode=0",
                        localIp, macHex, FIRMWARE_VERSION, DEVICE_NAME
                    );
                    byte[] respBytes = response.getBytes(StandardCharsets.US_ASCII);

                    // Build discovery response packet (simplified)
                    // The actual protocol uses a specific binary format, but we'll send
                    // a response that the SDK's discoveryReportUtilizer can parse
                    byte[] fullResp = buildDiscoveryResponse(localIp, macHex);

                    DatagramPacket respPacket = new DatagramPacket(
                        fullResp, fullResp.length,
                        packet.getAddress(), packet.getPort()
                    );
                    udpSocket.send(respPacket);
                    log("UDP", "Discovery response sent: " + response);

                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue listening
                }
            }
        } catch (BindException e) {
            log("UDP", "Port " + udpPort + " already in use, discovery disabled");
        } catch (Exception e) {
            log("UDP", "Discovery error: " + e.getMessage());
        }
    }

    byte[] buildDiscoveryResponse(String ip, String macHex) {
        // Build a binary discovery response matching the SDK's expected format
        // Based on protocol analysis:
        // [4 bytes: some header/flags]
        // [IP as 4 bytes big-endian] at offset 0 relative to data
        // [padding]
        // [MAC 6 bytes] at offset 14
        // [more padding]
        // [version marker byte=3] at offset 31
        // [version len] at offset 32
        // [version string]
        // [name marker byte=2]
        // [name len]
        // [name string]
        // [net_mode byte]

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            // Preamble (4 bytes - response flags matching expected pattern)
            bos.write(new byte[]{0x00, 0x01, 0x00, 0x00});

            // IP address as big-endian 4 bytes
            String[] ipParts = ip.split("\\.");
            for (String part : ipParts) {
                bos.write(Integer.parseInt(part));
            }

            // ResponseFirst match bytes at offset 4: {0, 2, 0, 0, 0, 0, 0, 0, 0, 1}
            bos.write(new byte[]{0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});

            // MAC address at offset 14 (6 bytes)
            bos.write(MAC_BYTES);

            // ResponseSecond match bytes at offset 20: {1, 0, 0xC0, 1, 0, 0, 1, 0, 1}
            bos.write(new byte[]{0x01, 0x00, (byte)0xC0, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01});

            // Padding to offset 31
            bos.write(new byte[]{0x00, 0x00});

            // Version info: marker=3, len, string
            byte[] versionBytes = FIRMWARE_VERSION.getBytes(StandardCharsets.US_ASCII);
            bos.write(0x03); // version marker
            bos.write(versionBytes.length);
            bos.write(versionBytes);

            // Device name info: marker=2, len, string
            byte[] nameBytes = DEVICE_NAME.getBytes(StandardCharsets.US_ASCII);
            bos.write(0x02); // name marker
            bos.write(nameBytes.length);
            bos.write(nameBytes);

            // Network mode
            bos.write(0x00); // net_mode=0 (TCP server)

            return bos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    // =====================================================================
    //  UTILITY
    // =====================================================================
    static short calcEPCCRC(byte[] bytes, int offset, int len) {
        final int polynomial = 0x1021;
        int crc = 0xFFFF;
        for (int index = 0; index < len; index++) {
            byte b = bytes[offset + index];
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
            }
        }
        return (short) (crc & 0xFFFF);
    }

    static byte bcc(byte[] data, int offset, int length) {
        byte result = 0;
        for (int i = 0; i < length; i++) {
            result ^= data[offset + i];
        }
        return result;
    }

    static void u16ToLE(short value, byte[] buf, int offset) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    static void u32ToLE(int value, byte[] buf, int offset) {
        buf[offset]     = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static String bytesToHex(byte[] data) {
        if (data == null || data.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(HEX[(b >> 4) & 0xF]);
            sb.append(HEX[b & 0xF]);
        }
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static String cmdName(int code) {
        return switch (code) {
            case CMD_GET_SN               -> "GetSN";
            case CMD_HEARTBEAT            -> "Heartbeat";
            case CMD_START_INVENTORY      -> "StartInventory";
            case CMD_STOP_INVENTORY       -> "StopInventory";
            case CMD_GET_VERSION          -> "GetVersion";
            case CMD_GET_ANT_CONFIG       -> "GetAntConfig";
            case CMD_SET_ANT_CONFIG       -> "SetAntConfig";
            case CMD_GET_Q_VALUE          -> "GetQValue";
            case CMD_GET_QUERY_CONFIG     -> "GetQueryConfig";
            case CMD_GET_INVENTORY_CONFIG -> "GetInventoryConfig";
            case CMD_GET_TX_ONOFF         -> "GetTxOnOff";
            case CMD_SELECT_TAG           -> "SelectTag";
            case CMD_CANCEL_SELECT_TAG    -> "CancelSelectTag";
            case CMD_GET_DEVICE_NAME      -> "GetDeviceName";
            case CMD_SET_DEVICE_NAME      -> "SetDeviceName";
            case CMD_DEVICE_INFO_REPORT   -> "DeviceInfoReport";
            case CMD_GET_SELECT_TAG       -> "GetSelectTag";
            case CMD_GET_WORK_MODE        -> "GetWorkMode";
            case CMD_GET_COMM_MODE        -> "GetCommMode";
            case CMD_GET_ALL_SYSTEM_PARAMS -> "GetAllSystemParams";
            case CMD_GET_READER_STATE     -> "GetReaderState";
            case CMD_READ_OEM_REGISTER    -> "ReadOemRegister";
            case CMD_WRITE_OEM_REGISTER   -> "WriteOemRegister";
            case CMD_GET_LINK_PROFILE     -> "GetLinkProfile";
            case CMD_SET_LINK_PROFILE     -> "SetLinkProfile";
            case CMD_RESET_READER         -> "ResetReader";
            case CMD_GET_BAUD             -> "GetBaud";
            case CMD_GET_BUZZER           -> "GetBuzzer";
            case CMD_GET_RSSI_FILTER      -> "GetRssiFilter";
            case CMD_GET_TAG_FILTER       -> "GetTagFilter";
            case CMD_GET_RTC_TIME         -> "GetRtcTime";
            case CMD_GET_RS485_ADDR       -> "GetRs485Addr";
            case CMD_GET_TRIGGER_DELAY    -> "GetTriggerDelay";
            case CMD_GET_AUTO_READ_PARAMS -> "GetAutoReadParams";
            case CMD_GET_WG_PARAMS        -> "GetWgParams";
            default -> "Cmd" + code;
        };
    }

    static void log(String tag, String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        System.out.printf("[%s] [%-4s] %s%n", ts, tag, msg);
    }

}
