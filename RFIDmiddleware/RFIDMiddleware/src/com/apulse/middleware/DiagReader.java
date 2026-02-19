package com.apulse.middleware;

import com.apulse.fixedreaderlib.FixedReader;
import com.apulse.fixedreaderlib.FixedReaderApiError;

/**
 * Reader-01 안테나 설정 진단 도구
 */
public class DiagReader {
    public static void main(String[] args) throws Exception {
        String connectConfig = "CommType=NET;RemoteIp=192.168.0.196;RemotePort=20058";
        FixedReader reader = new FixedReader();

        System.out.println("=== Reader-01 안테나 설정 진단 ===");
        System.out.println("Connecting: " + connectConfig);

        int result = reader.connect(connectConfig,
            (readerId, cmdCode, reportData, reportDataLen) -> { });

        if (result != FixedReaderApiError.ErrNoError) {
            System.out.println("Connection failed: error=" + result);
            return;
        }

        Thread.sleep(2000);

        // 펌웨어 버전
        String[] version = new String[1];
        reader.getFirmwareVersion(version);
        System.out.println("\n[1] Firmware: " + version[0]);

        // 안테나 설정 조회
        System.out.println("\n[2] getAntConfig:");
        byte[] antConfig = new byte[256];
        int[] antLen = new int[]{antConfig.length};
        int antResult = reader.getAntConfig(antConfig, antLen);
        System.out.println("  result=" + antResult + " (0=OK), len=" + antLen[0]);
        if (antResult == FixedReaderApiError.ErrNoError && antLen[0] > 0) {
            System.out.print("  raw hex: ");
            for (int i = 0; i < antLen[0]; i++) {
                System.out.printf("%02X ", antConfig[i] & 0xFF);
                if ((i + 1) % 12 == 0) System.out.print("\n           ");
            }
            System.out.println();
            // 48바이트 = 4채널 x 12바이트 (int32 LE x 3)
            if (antLen[0] == 48) {
                System.out.println("  === 48-byte 확장 포맷 (4ch x 12B = int32 LE x 3) ===");
                for (int ch = 0; ch < 4; ch++) {
                    int base = ch * 12;
                    int f1 = readLE32(antConfig, base);
                    int f2 = readLE32(antConfig, base + 4);
                    int f3 = readLE32(antConfig, base + 8);
                    System.out.printf("  CH%d: field1=%d, field2=%d, field3=%d\n", ch + 1, f1, f2, f3);
                }
            }
        }

        // 전체 시스템 파라미터 조회
        System.out.println("\n[3] getAllSystemParams:");
        byte[] sysParams = new byte[512];
        int[] sysLen = new int[]{sysParams.length};
        int sysResult = reader.getAllSystemParams(sysParams, sysLen);
        System.out.println("  result=" + sysResult + " (0=OK), len=" + sysLen[0]);
        if (sysResult == FixedReaderApiError.ErrNoError && sysLen[0] > 0) {
            System.out.print("  raw hex: ");
            for (int i = 0; i < sysLen[0]; i++) {
                System.out.printf("%02X ", sysParams[i] & 0xFF);
                if ((i + 1) % 16 == 0) System.out.print("\n           ");
            }
            System.out.println();
        }

        // setAntConfig 테스트 — field3(power?)를 변경
        if (antResult == FixedReaderApiError.ErrNoError && antLen[0] == 48) {
            System.out.println("\n[4] setAntConfig 테스트 — CH1 field3만 변경:");

            // 원본 복사 후 CH1 field3를 30으로 변경
            byte[] cfgTest = new byte[48];
            System.arraycopy(antConfig, 0, cfgTest, 0, 48);
            writeLE32(cfgTest, 8, 30);  // CH1 field3 = 30

            System.out.print("  sending: ");
            for (int i = 0; i < 12; i++) System.out.printf("%02X ", cfgTest[i] & 0xFF);
            System.out.println("...");

            int rSet = reader.setAntConfig(cfgTest);
            System.out.println("  setAntConfig result: error=" + rSet + " (0=OK)");

            // 다시 조회해서 변경 확인
            if (rSet == FixedReaderApiError.ErrNoError) {
                Thread.sleep(500);
                byte[] verify = new byte[256];
                int[] verLen = new int[]{verify.length};
                reader.getAntConfig(verify, verLen);
                System.out.println("  === 변경 후 재조회 ===");
                for (int ch = 0; ch < 4; ch++) {
                    int base = ch * 12;
                    int f1 = readLE32(verify, base);
                    int f2 = readLE32(verify, base + 4);
                    int f3 = readLE32(verify, base + 8);
                    System.out.printf("  CH%d: field1=%d, field2=%d, field3=%d\n", ch + 1, f1, f2, f3);
                }
            }

            // 원본으로 복원
            reader.setAntConfig(antConfig);
            System.out.println("  원본으로 복원 완료");
        }

        Thread.sleep(1000);
        reader.close();
        System.out.println("\n=== 진단 완료 ===");
    }

    static int readLE32(byte[] buf, int off) {
        return (buf[off] & 0xFF) | ((buf[off+1] & 0xFF) << 8)
            | ((buf[off+2] & 0xFF) << 16) | ((buf[off+3] & 0xFF) << 24);
    }

    static void writeLE32(byte[] buf, int off, int val) {
        buf[off] = (byte)(val & 0xFF);
        buf[off+1] = (byte)((val >> 8) & 0xFF);
        buf[off+2] = (byte)((val >> 16) & 0xFF);
        buf[off+3] = (byte)((val >> 24) & 0xFF);
    }
}
