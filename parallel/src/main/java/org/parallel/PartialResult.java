package org.parallel;

import java.util.ArrayList;
import java.util.List;

public class PartialResult {
    public double[] sumLat;
    public double[] sumLon;
    public int[] counts;
    public List<List<Record>> clusterRecords;

    public PartialResult(int k) {
        sumLat = new double[k];
        sumLon = new double[k];
        counts = new int[k];

        clusterRecords = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            clusterRecords.add(new ArrayList<>());
        }
    }

    public synchronized void add(int clusterIndex, double lat, double lon, Record record) {
        sumLat[clusterIndex] += lat;
        sumLon[clusterIndex] += lon;
        counts[clusterIndex]++;
        clusterRecords.get(clusterIndex).add(record);
    }
}
