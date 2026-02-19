package com.apulse.middleware.reader;

public class TagData {
    private final String epc;
    private String readerName;
    private int rssi;
    private int count;
    private String firstSeen;
    private String lastSeen;

    public TagData(String epc, String readerName, int rssi, String time) {
        this.epc = epc;
        this.readerName = readerName;
        this.rssi = rssi;
        this.count = 1;
        this.firstSeen = time;
        this.lastSeen = time;
    }

    public String getEpc() { return epc; }
    public String getReaderName() { return readerName; }
    public int getRssi() { return rssi; }
    public int getCount() { return count; }
    public String getFirstSeen() { return firstSeen; }
    public String getLastSeen() { return lastSeen; }

    public void update(int rssi, String time) {
        this.rssi = rssi;
        this.count++;
        this.lastSeen = time;
    }

    public void update(int rssi, String time, String readerName) {
        this.rssi = rssi;
        this.count++;
        this.lastSeen = time;
        this.readerName = readerName;
    }

    /** 리더기+EPC 조합으로 유니크 키 생성 */
    public String getKey() {
        return readerName + ":" + epc;
    }
}
