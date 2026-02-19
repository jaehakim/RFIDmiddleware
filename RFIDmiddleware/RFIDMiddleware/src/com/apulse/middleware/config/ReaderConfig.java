package com.apulse.middleware.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ReaderConfig {
    private String name;
    private String ip;
    private int port;

    public ReaderConfig(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    public String getName() { return name; }
    public String getIp() { return ip; }
    public int getPort() { return port; }

    public void setName(String name) { this.name = name; }
    public void setIp(String ip) { this.ip = ip; }
    public void setPort(int port) { this.port = port; }

    public String getConnectString() {
        return String.format("CommType=NET;RemoteIp=%s;RemotePort=%d", ip, port);
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d)", name, ip, port);
    }

    /** 설정 파일에서 리더기 목록 로드 */
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
                    configs.add(new ReaderConfig(name, ip, port));
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
            writer.println("# 리더기 설정 파일 (이름,IP,Port)");
            for (ReaderConfig cfg : configs) {
                writer.printf("%s,%s,%d%n", cfg.getName(), cfg.getIp(), cfg.getPort());
            }
        } catch (IOException e) {
            System.err.println("설정 파일 저장 실패: " + e.getMessage());
        }
    }
}
