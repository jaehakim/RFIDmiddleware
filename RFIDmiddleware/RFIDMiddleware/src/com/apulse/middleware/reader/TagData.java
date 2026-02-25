package com.apulse.middleware.reader;

public class TagData {
    private final String epc;
    private String readerName;
    private int rssi;
    private int antenna;
    private int count;
    private String firstSeen;
    private String lastSeen;

    // 자산 정보 (자산에 해당하지 않으면 null)
    private String assetNumber;
    private String assetName;
    private String department;
    /** 상태: null=일반태그, "반출허용", "반출알림" */
    private String assetStatus;

    public TagData(String epc, String readerName, int rssi, int antenna, String time) {
        this.epc = epc;
        this.readerName = readerName;
        this.rssi = rssi;
        this.antenna = antenna;
        this.count = 1;
        this.firstSeen = time;
        this.lastSeen = time;
    }

    public String getEpc() { return epc; }
    public String getReaderName() { return readerName; }
    public int getRssi() { return rssi; }
    public int getAntenna() { return antenna; }
    public int getCount() { return count; }
    public String getFirstSeen() { return firstSeen; }
    public String getLastSeen() { return lastSeen; }
    public String getAssetNumber() { return assetNumber; }
    public String getAssetName() { return assetName; }
    public String getDepartment() { return department; }
    public String getAssetStatus() { return assetStatus; }

    public void setAssetInfo(String assetNumber, String assetName, String department, String assetStatus) {
        this.assetNumber = assetNumber;
        this.assetName = assetName;
        this.department = department;
        this.assetStatus = assetStatus;
    }

    public void update(int rssi, int antenna, String time) {
        this.rssi = rssi;
        this.antenna = antenna;
        this.count++;
        this.lastSeen = time;
    }

    public void update(int rssi, int antenna, String time, String readerName) {
        this.rssi = rssi;
        this.antenna = antenna;
        this.count++;
        this.lastSeen = time;
        this.readerName = readerName;
    }

    /** 리더기+EPC 조합으로 유니크 키 생성 */
    public String getKey() {
        //return readerName + ":" + epc;//리더기+EPC 조합으로 유니크 키 생성
		return epc;//EPC 로 유니크 키 생성
    }
}
