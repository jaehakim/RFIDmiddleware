package com.apulse.middleware.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ReaderConfig {
    private String name;
    private String ip;
    private int port;
    private boolean buzzerEnabled = false;
    private boolean warningLightEnabled = false;
    private int[] antennaPowers = {30, 30, 30, 30};
    private int dwellTime = 500;

    public ReaderConfig(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    public String getName() { return name; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public boolean isBuzzerEnabled() { return buzzerEnabled; }
    public boolean isWarningLightEnabled() { return warningLightEnabled; }
    public int[] getAntennaPowers() { return antennaPowers; }
    public int getDwellTime() { return dwellTime; }

    public void setName(String name) { this.name = name; }
    public void setIp(String ip) { this.ip = ip; }
    public void setPort(int port) { this.port = port; }
    public void setBuzzerEnabled(boolean buzzerEnabled) { this.buzzerEnabled = buzzerEnabled; }
    public void setWarningLightEnabled(boolean warningLightEnabled) { this.warningLightEnabled = warningLightEnabled; }
    public void setAntennaPowers(int[] antennaPowers) { this.antennaPowers = antennaPowers; }
    public void setDwellTime(int dwellTime) { this.dwellTime = dwellTime; }

    public String getConnectString() {
        return String.format("CommType=NET;RemoteIp=%s;RemotePort=%d", ip, port);
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d)", name, ip, port);
    }

    /**
     * 설정 파일에서 리더기 목록 로드.
     * CSV 포맷: 이름,IP,Port[,부저,경광등,출력1,출력2,출력3,출력4,드웰시간]
     * 뒤 7개 필드는 선택적 (하위 호환)
     */
    public static List<ReaderConfig> loadFromFile(String filePath) {
        List<ReaderConfig> configs = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            return configs;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String name = parts[0].trim();
                    String ip = parts[1].trim();
                    int port;
                    try {
                        port = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    ReaderConfig cfg = new ReaderConfig(name, ip, port);

                    // 확장 필드 (하위 호환)
                    if (parts.length >= 10) {
                        // 새 포맷: 이름,IP,Port,부저,경광등,출력1,출력2,출력3,출력4,드웰시간
                        try {
                            cfg.buzzerEnabled = "1".equals(parts[3].trim());
                            cfg.warningLightEnabled = "1".equals(parts[4].trim());
                            cfg.antennaPowers = new int[] {
                                Integer.parseInt(parts[5].trim()),
                                Integer.parseInt(parts[6].trim()),
                                Integer.parseInt(parts[7].trim()),
                                Integer.parseInt(parts[8].trim())
                            };
                            cfg.dwellTime = Integer.parseInt(parts[9].trim());
                        } catch (NumberFormatException e) {
                            // 파싱 실패시 기본값 유지
                        }
                    } else if (parts.length >= 9) {
                        // 구 포맷: 이름,IP,Port,부저,출력1,출력2,출력3,출력4,드웰시간
                        try {
                            cfg.buzzerEnabled = "1".equals(parts[3].trim());
                            cfg.antennaPowers = new int[] {
                                Integer.parseInt(parts[4].trim()),
                                Integer.parseInt(parts[5].trim()),
                                Integer.parseInt(parts[6].trim()),
                                Integer.parseInt(parts[7].trim())
                            };
                            cfg.dwellTime = Integer.parseInt(parts[8].trim());
                        } catch (NumberFormatException e) {
                            // 파싱 실패시 기본값 유지
                        }
                    }

                    configs.add(cfg);
                }
            }
        } catch (IOException e) {
            System.err.println("설정 파일 로드 실패: " + e.getMessage());
        }
        return configs;
    }

    /** 리더기 목록을 설정 파일에 저장 */
    public static void saveToFile(String filePath, List<ReaderConfig> configs) {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.println("# 리더기 설정 파일 (이름,IP,Port,부저,경광등,출력1,출력2,출력3,출력4,드웰시간)");
            for (ReaderConfig cfg : configs) {
                writer.printf("%s,%s,%d,%s,%s,%d,%d,%d,%d,%d%n",
                    cfg.getName(), cfg.getIp(), cfg.getPort(),
                    cfg.buzzerEnabled ? "1" : "0",
                    cfg.warningLightEnabled ? "1" : "0",
                    cfg.antennaPowers[0], cfg.antennaPowers[1],
                    cfg.antennaPowers[2], cfg.antennaPowers[3],
                    cfg.dwellTime);
            }
        } catch (IOException e) {
            System.err.println("설정 파일 저장 실패: " + e.getMessage());
        }
    }
}
