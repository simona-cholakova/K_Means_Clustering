package org.parallel;

import java.util.List;

public class AssignmentTask implements Runnable {
    private final List<Record> records;
    private final List<Cluster> clusters;
    private final PartialResult result;
    private final EuclideanDistance distance;

    public AssignmentTask(List<Record> records, List<Cluster> clusters, PartialResult result, EuclideanDistance distance) {
        this.records = records;
        this.clusters = clusters;
        this.result = result;
        this.distance = distance;
    }

    private int findClosestCluster(Record record) {
        int closest = 0;
        double minDist = distance.calculate(record, clusters.get(0));

        for (int i = 1; i < clusters.size(); i++) {
            double dist = distance.calculate(record, clusters.get(i));
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest;
    }

    @Override
    public void run() {
        for (Record record : records) {
            int closest = findClosestCluster(record);
            result.add(closest, record.getLa(), record.getLo(), record);
        }
    }
}
