package org.parallel;

import java.util.ArrayList;
import java.util.List;

public class Cluster {
    private double centerLat;
    private double centerLon;
    private List<Record> records;

    public Cluster(double centerLat, double centerLon) {
        this.centerLat = centerLat;
        this.centerLon = centerLon;
        this.records = new ArrayList<>();
    }

    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }

    public void updateCenter(Coordinate newCenter) {
        this.centerLat = newCenter.getLat();
        this.centerLon = newCenter.getLon();
    }


    public void clearRecords() {
        records.clear();
    }

    public List<Record> getRecords() {
        return records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }
}
