package com.apulse.middleware.db;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AssetRepository {
    private static final AssetRepository INSTANCE = new AssetRepository();

    private final ConcurrentHashMap<String, AssetInfo> assetMap = new ConcurrentHashMap<>();
    private final Set<String> permittedEpcs = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;

    /** 같은 EPC 30초 내 재알림 방지 */
    private final Cache<String, Boolean> alertDedup = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(10000)
        .build();

    private AssetRepository() {}

    public static AssetRepository getInstance() {
        return INSTANCE;
    }

    /**
     * EPC를 16비트 워드(4자리 HEX) 경계로 정규화.
     * 리더기는 워드 단위로 읽기 때문에 DB의 22자리 EPC가 24자리로 패딩되어 수신됨.
     * 예: "0420100420250910000006" (22자) → "042010042025091000000600" (24자)
     */
    private String normalizeEpc(String epc) {
        if (epc == null) return null;
        epc = epc.toUpperCase().trim();
        int remainder = epc.length() % 4;
        if (remainder == 0) return epc;
        StringBuilder sb = new StringBuilder(epc);
        for (int i = 0; i < 4 - remainder; i++) sb.append('0');
        return sb.toString();
    }

    public void start(int refreshIntervalSeconds) {
        refreshCache();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AssetRepo-Refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::refreshCache,
            refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
        System.out.println("[AssetRepository] Started (refresh every " + refreshIntervalSeconds + "s)");
    }

    public void refreshCache() {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return;

        try {
            // assets 전체 로드 (EPC 정규화하여 캐시 키로 사용)
            ConcurrentHashMap<String, AssetInfo> newAssetMap = new ConcurrentHashMap<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT epc, asset_number, asset_name, department FROM assets");
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String rawEpc = rs.getString("epc");
                    String normalizedEpc = normalizeEpc(rawEpc);
                    newAssetMap.put(normalizedEpc, new AssetInfo(
                        normalizedEpc,
                        rs.getString("asset_number"),
                        rs.getString("asset_name"),
                        rs.getString("department")
                    ));
                }
            }
            assetMap.clear();
            assetMap.putAll(newAssetMap);

            // export_permissions 유효기간 체크 (EPC 정규화)
            Set<String> newPermitted = ConcurrentHashMap.newKeySet();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT epc FROM export_permissions WHERE permit_start <= NOW() AND permit_end >= NOW()");
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    newPermitted.add(normalizeEpc(rs.getString("epc")));
                }
            }
            permittedEpcs.clear();
            permittedEpcs.addAll(newPermitted);

            System.out.println("[AssetRepository] Cache refreshed: assets=" + assetMap.size()
                + ", permitted=" + permittedEpcs.size());
        } catch (Exception e) {
            System.out.println("[AssetRepository] Cache refresh failed: " + e.getMessage());
        }
    }

    /** EPC로 자산정보 조회 (자산이 아니면 null) */
    public AssetInfo getAssetInfo(String epc) {
        return assetMap.get(epc);
    }

    /**
     * 미허가 반출 체크
     * @return 미허가 자산이면 AssetInfo 반환, 자산이 아니거나 허용된 경우 null
     */
    public AssetInfo checkUnauthorizedExport(String epc) {
        AssetInfo asset = assetMap.get(epc);
        if (asset == null) return null;  // 자산이 아님
        if (permittedEpcs.contains(epc)) return null;  // 반출 허용됨
        return asset;  // 미허가 반출
    }

    /**
     * 알림 중복 방지 체크 (같은 EPC 30초 내 재알림 방지)
     * @return true이면 알림 발생 필요 (중복 아님), false이면 중복
     */
    public boolean shouldAlert(String epc) {
        if (alertDedup.getIfPresent(epc) != null) {
            return false;
        }
        alertDedup.put(epc, Boolean.TRUE);
        return true;
    }

    /** 반출알림 이력 DB 기록 */
    public void insertAlert(String epc, String assetNumber, String readerName, int rssi, String alertTime) {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return;

        String sql = "INSERT INTO export_alerts (epc, asset_number, reader_name, rssi, alert_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, epc);
            pstmt.setString(2, assetNumber);
            pstmt.setString(3, readerName);
            pstmt.setInt(4, rssi);
            pstmt.setTimestamp(5, Timestamp.valueOf(alertTime));
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.out.println("[AssetRepository] Insert alert failed: " + e.getMessage());
        }
    }

    // --- DB 조회 메서드 (GUI 표시용) ---

    /** 자산 목록 전체 조회 */
    public List<String[]> queryAssets() {
        List<String[]> results = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return results;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT asset_number, epc, asset_name, department, created_at FROM assets ORDER BY asset_number");
             ResultSet rs = pstmt.executeQuery()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                results.add(new String[] {
                    rs.getString("asset_number"),
                    rs.getString("epc"),
                    rs.getString("asset_name") != null ? rs.getString("asset_name") : "",
                    rs.getString("department") != null ? rs.getString("department") : "",
                    ts != null ? sdf.format(ts) : ""
                });
            }
        } catch (Exception e) {
            System.out.println("[AssetRepository] Query assets failed: " + e.getMessage());
        }
        return results;
    }

    /** 반출허용 목록 전체 조회 */
    public List<String[]> queryExportPermissions() {
        List<String[]> results = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return results;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT ep.epc, a.asset_number, a.asset_name, ep.permit_start, ep.permit_end, ep.reason, "
                + "CASE WHEN ep.permit_start <= NOW() AND ep.permit_end >= NOW() THEN '유효' ELSE '만료' END AS status "
                + "FROM export_permissions ep LEFT JOIN assets a ON ep.epc = a.epc "
                + "ORDER BY ep.permit_end DESC");
             ResultSet rs = pstmt.executeQuery()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rs.next()) {
                Timestamp start = rs.getTimestamp("permit_start");
                Timestamp end = rs.getTimestamp("permit_end");
                results.add(new String[] {
                    rs.getString("epc"),
                    rs.getString("asset_number") != null ? rs.getString("asset_number") : "",
                    rs.getString("asset_name") != null ? rs.getString("asset_name") : "",
                    start != null ? sdf.format(start) : "",
                    end != null ? sdf.format(end) : "",
                    rs.getString("reason") != null ? rs.getString("reason") : "",
                    rs.getString("status")
                });
            }
        } catch (Exception e) {
            System.out.println("[AssetRepository] Query permissions failed: " + e.getMessage());
        }
        return results;
    }

    /** 반출알림 이력 조회 (기간 지정) */
    public List<String[]> queryExportAlerts(String fromTime, String toTime) {
        List<String[]> results = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return results;

        String sql = "SELECT ea.alert_time, ea.reader_name, ea.epc, ea.asset_number, "
            + "a.asset_name, ea.rssi "
            + "FROM export_alerts ea LEFT JOIN assets a ON ea.epc = a.epc "
            + "WHERE ea.alert_time BETWEEN ? AND ? ORDER BY ea.alert_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            pstmt.setTimestamp(1, new Timestamp(sdf.parse(fromTime).getTime()));
            pstmt.setTimestamp(2, new Timestamp(sdf.parse(toTime).getTime()));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp alertTime = rs.getTimestamp("alert_time");
                    results.add(new String[] {
                        alertTime != null ? sdf.format(alertTime) : "",
                        rs.getString("reader_name"),
                        rs.getString("epc"),
                        rs.getString("asset_number") != null ? rs.getString("asset_number") : "",
                        rs.getString("asset_name") != null ? rs.getString("asset_name") : "",
                        String.valueOf(rs.getInt("rssi"))
                    });
                }
            }
        } catch (Exception e) {
            System.out.println("[AssetRepository] Query alerts failed: " + e.getMessage());
        }
        return results;
    }

    public int getAssetCount() {
        return assetMap.size();
    }

    public int getPermittedCount() {
        return permittedEpcs.size();
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        System.out.println("[AssetRepository] Shutdown complete");
    }

    /** 자산 정보 모델 */
    public static class AssetInfo {
        private final String epc;
        private final String assetNumber;
        private final String assetName;
        private final String department;

        public AssetInfo(String epc, String assetNumber, String assetName, String department) {
            this.epc = epc;
            this.assetNumber = assetNumber;
            this.assetName = assetName;
            this.department = department;
        }

        public String getEpc() { return epc; }
        public String getAssetNumber() { return assetNumber; }
        public String getAssetName() { return assetName; }
        public String getDepartment() { return department; }
    }
}
