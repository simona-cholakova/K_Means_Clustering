package org.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Cluster {
    private double centerLat;
    private double centerLon;
    private List<Record> records;

    public Cluster(double centerLat, double centerLon) {
        this.centerLat = centerLat;
        this.centerLon = centerLon;
        this.records = new ArrayList<>();
    }

    public void setCenter(double centerLat, double centerLon) {
        this.centerLat = centerLat;
    }
    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }

    //update the cluster center by recalculating the average position
    public void updateCenter(Coordinate newCenter) {
        this.centerLat = newCenter.getLat();
        this.centerLon = newCenter.getLon();
    }


    public void clearRecords() {
        records.clear();
    }

    public void addRecord(Record record) {
        records.add(record);
    }
    public List<Record> getRecords() {
        return records;
    }
    public static List<Cluster> initializeRandomClusters(List<Record> records, int k) {
        Random random = new Random();
        List<Cluster> clusters = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            Record randomRecord = records.get(random.nextInt(records.size()));
            Coordinate center = new Coordinate(randomRecord.getLa(), randomRecord.getLo());
            clusters.add(new Cluster(center.getLat(), center.getLon()));
        }
        return clusters;
    }


    public void setRecords(List<Record> records) {
        this.records = records;
    }
}
