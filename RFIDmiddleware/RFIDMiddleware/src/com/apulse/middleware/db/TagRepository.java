package com.apulse.middleware.db;

import com.apulse.middleware.reader.TagData;
import com.apulse.middleware.util.AppLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TagRepository {
    private static final TagRepository INSTANCE = new TagRepository();
    private static final int BATCH_SIZE = 50;
    private static final long BATCH_INTERVAL_MS = 500;
    private static final int RECENT_TAG_MAX = 200;

    private final BlockingQueue<TagRecord> queue = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedDeque<RecentTag> recentTags = new ConcurrentLinkedDeque<>();
    private final AtomicLong recentTagSeq = new AtomicLong(0);
    private Thread writerThread;
    private volatile boolean running = false;

    private TagRepository() {}

    public static TagRepository getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (running) return;
        running = true;
        writerThread = new Thread(this::writerLoop, "TagDB-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
        AppLogger.info("TagRepository", "Writer thread started");
    }

    public void insertTagRead(String epc, String readerName, int rssi, int antenna, String readTime) {
        if (!DatabaseManager.getInstance().isAvailable()) return;
        queue.offer(new TagRecord(epc, readerName, rssi, antenna, readTime));
    }

    private void writerLoop() {
        List<TagRecord> batch = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                TagRecord first = queue.poll(BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, BATCH_SIZE - 1);
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Flush remaining
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
        AppLogger.info("TagRepository", "Writer thread stopped");
    }

    private void flushBatch(List<TagRecord> batch) {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null || batch.isEmpty()) return;

        String sql = "INSERT INTO tag_reads (epc, reader_name, rssi, antenna, read_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (TagRecord rec : batch) {
                pstmt.setString(1, rec.epc);
                pstmt.setString(2, rec.readerName);
                pstmt.setInt(3, rec.rssi);
                pstmt.setInt(4, rec.antenna);
                try {
                    Date d = sdf.parse(rec.readTime);
                    pstmt.setTimestamp(5, new Timestamp(d.getTime()));
                } catch (Exception e) {
                    pstmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (Exception e) {
            AppLogger.error("TagRepository", "Batch insert failed: " + e.getMessage());
        }
    }

    public List<TagData> getTagReads(String fromTime, String toTime) {
        List<TagData> results = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return results;

        String sql = "SELECT epc, reader_name, rssi, antenna, read_time FROM tag_reads "
            + "WHERE read_time BETWEEN ? AND ? ORDER BY read_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            pstmt.setTimestamp(1, new Timestamp(sdf.parse(fromTime).getTime()));
            pstmt.setTimestamp(2, new Timestamp(sdf.parse(toTime).getTime()));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String epc = rs.getString("epc");
                    String readerName = rs.getString("reader_name");
                    int rssi = rs.getInt("rssi");
                    int antenna = rs.getInt("antenna");
                    String readTime = sdf.format(rs.getTimestamp("read_time"));
                    results.add(new TagData(epc, readerName, rssi, antenna, readTime));
                }
            }
        } catch (Exception e) {
            AppLogger.error("TagRepository", "Query failed: " + e.getMessage());
        }
        return results;
    }

    public int getTagReadCount() {
        Connection conn = DatabaseManager.getInstance().getConnection();
        if (conn == null) return 0;

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM tag_reads");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            AppLogger.error("TagRepository", "Count query failed: " + e.getMessage());
        }
        return 0;
    }

    public void shutdown() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        AppLogger.info("TagRepository", "Shutdown complete");
    }

    // --- Recent tag buffer (for dashboard real-time view) ---

    public void addRecentTag(String time, String readerName, String epc, int rssi, int antenna,
                             String assetNumber, String assetName, String department, String status) {
        long seq = recentTagSeq.incrementAndGet();
        recentTags.addFirst(new RecentTag(seq, time, readerName, epc, rssi, antenna,
                assetNumber, assetName, department, status));
        while (recentTags.size() > RECENT_TAG_MAX) {
            recentTags.pollLast();
        }
    }

    public List<RecentTag> getRecentTags() {
        return new ArrayList<>(recentTags);
    }

    public long getRecentTagSeq() {
        return recentTagSeq.get();
    }

    public void clearRecentTags() {
        recentTags.clear();
    }

    public static class RecentTag {
        public final long seq;
        public final String time;
        public final String readerName;
        public final String epc;
        public final int rssi;
        public final int antenna;
        public final String assetNumber;
        public final String assetName;
        public final String department;
        public final String status;

        RecentTag(long seq, String time, String readerName, String epc, int rssi, int antenna,
                  String assetNumber, String assetName, String department, String status) {
            this.seq = seq;
            this.time = time;
            this.readerName = readerName;
            this.epc = epc;
            this.rssi = rssi;
            this.antenna = antenna;
            this.assetNumber = assetNumber;
            this.assetName = assetName;
            this.department = department;
            this.status = status;
        }
    }

    private static class TagRecord {
        final String epc;
        final String readerName;
        final int rssi;
        final int antenna;
        final String readTime;

        TagRecord(String epc, String readerName, int rssi, int antenna, String readTime) {
            this.epc = epc;
            this.readerName = readerName;
            this.rssi = rssi;
            this.antenna = antenna;
            this.readTime = readTime;
        }
    }
}
