package com.apulse.middleware.db;

import com.apulse.middleware.util.AppLogger;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        scheduler.scheduleAtFixedRate(() -> {
            try {
                refreshCache();
            } catch (Throwable t) {
                // scheduleAtFixedRate는 예외 발생 시 이후 실행을 중단하므로 반드시 catch
                AppLogger.error("AssetRepository", "Cache refresh FATAL: " + t.getMessage(), t);
            }
        }, refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
        AppLogger.info("AssetRepository", "Started (refresh every " + refreshIntervalSeconds + "s)");
    }

    public void refreshCache() {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return;

        try {
            // assets 전체 로드 (EPC 정규화하여 캐시 키로 사용)
            ConcurrentHashMap<String, AssetInfo> newAssetMap = new ConcurrentHashMap<>();
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT epc, asset_number, asset_name, department, possession FROM assets");
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String rawEpc = rs.getString("epc");
                    String normalizedEpc = normalizeEpc(rawEpc);
                    newAssetMap.put(normalizedEpc, new AssetInfo(
                        normalizedEpc,
                        rs.getString("asset_number"),
                        rs.getString("asset_name"),
                        rs.getString("department"),
                        rs.getInt("possession") == 1
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

            AppLogger.debug("AssetRepository", "Cache refreshed: assets=" + assetMap.size()
                + ", permitted=" + permittedEpcs.size() + " " + permittedEpcs);
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Cache refresh failed: " + e.getMessage());
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
            AppLogger.error("AssetRepository", "Insert alert failed: " + e.getMessage());
        }
    }

    /** 반출허용 추가 */
    public boolean insertPermission(String epc, String permitStart, String permitEnd, String reason) {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return false;

        String sql = "INSERT INTO export_permissions (epc, permit_start, permit_end, reason) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, epc);
            pstmt.setTimestamp(2, Timestamp.valueOf(permitStart));
            pstmt.setTimestamp(3, Timestamp.valueOf(permitEnd));
            pstmt.setString(4, reason);
            pstmt.executeUpdate();
            return true;
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Insert permission failed: " + e.getMessage());
            return false;
        }
    }

    /** 반출허용 삭제 */
    public boolean deletePermission(long id) {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return false;

        String sql = "DELETE FROM export_permissions WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Delete permission failed: " + e.getMessage());
            return false;
        }
    }

    // --- DB 조회 메서드 (GUI 표시용) ---

    /** 자산 목록 전체 조회 */
    public List<String[]> queryAssets() {
        List<String[]> results = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return results;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id, asset_number, epc, asset_name, department, possession, created_at FROM assets ORDER BY asset_number");
             ResultSet rs = pstmt.executeQuery()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                results.add(new String[] {
                    rs.getString("asset_number"),
                    rs.getString("epc"),
                    rs.getString("asset_name") != null ? rs.getString("asset_name") : "",
                    rs.getString("department") != null ? rs.getString("department") : "",
                    ts != null ? sdf.format(ts) : "",
                    rs.getInt("possession") == 1 ? "보유" : "미보유",
                    String.valueOf(rs.getLong("id"))
                });
            }
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Query assets failed: " + e.getMessage());
        }
        return results;
    }

    /** 자산 추가 */
    public boolean insertAsset(String assetNumber, String epc, String assetName, String department, int possession) {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return false;

        String sql = "INSERT INTO assets (asset_number, epc, asset_name, department, possession) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assetNumber);
            pstmt.setString(2, epc);
            pstmt.setString(3, assetName);
            pstmt.setString(4, department);
            pstmt.setInt(5, possession);
            pstmt.executeUpdate();
            return true;
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Insert asset failed: " + e.getMessage());
            return false;
        }
    }

    /** 자산 수정 */
    public boolean updateAsset(long id, String assetNumber, String epc, String assetName, String department, Integer possession) {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return false;

        // 동적 UPDATE 쿼리 생성 (전달된 필드만 수정)
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (assetNumber != null) { setClauses.add("asset_number = ?"); params.add(assetNumber); }
        if (epc != null) { setClauses.add("epc = ?"); params.add(epc); }
        if (assetName != null) { setClauses.add("asset_name = ?"); params.add(assetName); }
        if (department != null) { setClauses.add("department = ?"); params.add(department); }
        if (possession != null) { setClauses.add("possession = ?"); params.add(possession); }

        if (setClauses.isEmpty()) return false;

        String sql = "UPDATE assets SET " + String.join(", ", setClauses) + " WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String) pstmt.setString(i + 1, (String) p);
                else if (p instanceof Integer) pstmt.setInt(i + 1, (Integer) p);
            }
            pstmt.setLong(params.size() + 1, id);
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Update asset failed: " + e.getMessage());
            return false;
        }
    }

    /** 반출허용 목록 전체 조회 */
    public List<String[]> queryExportPermissions() {
        List<String[]> results = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return results;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT ep.id, ep.epc, a.asset_number, a.asset_name, ep.permit_start, ep.permit_end, ep.reason, "
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
                    rs.getString("status"),
                    String.valueOf(rs.getLong("id"))
                });
            }
        } catch (Exception e) {
            AppLogger.error("AssetRepository", "Query permissions failed: " + e.getMessage());
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
            AppLogger.error("AssetRepository", "Query alerts failed: " + e.getMessage());
        }
        return results;
    }

    public int getAssetCount() {
        return assetMap.size();
    }

    public int getPermittedCount() {
        return permittedEpcs.size();
    }

    /** 자산 캐시 복사본 (조회용) */
    public Map<String, AssetInfo> getAssetMapCopy() {
        return new HashMap<>(assetMap);
    }

    /** 반출허용 EPC 캐시 복사본 (조회용) */
    public Set<String> getPermittedEpcsCopy() {
        return new HashSet<>(permittedEpcs);
    }

    /** 알림 중복제거 캐시 키 목록 (조회용) */
    public Set<String> getAlertDedupKeys() {
        return new HashSet<>(alertDedup.asMap().keySet());
    }

    public long getAlertDedupSize() {
        return alertDedup.estimatedSize();
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        AppLogger.info("AssetRepository", "Shutdown complete");
    }

    /** 자산 정보 모델 */
    public static class AssetInfo {
        private final String epc;
        private final String assetNumber;
        private final String assetName;
        private final String department;
        private final boolean possession;

        public AssetInfo(String epc, String assetNumber, String assetName, String department, boolean possession) {
            this.epc = epc;
            this.assetNumber = assetNumber;
            this.assetName = assetName;
            this.department = department;
            this.possession = possession;
        }

        public String getEpc() { return epc; }
        public String getAssetNumber() { return assetNumber; }
        public String getAssetName() { return assetName; }
        public String getDepartment() { return department; }
        public boolean isPossession() { return possession; }
    }
}
