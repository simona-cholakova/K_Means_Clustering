package org.parallel;

import java.util.ArrayList;
import java.util.List;

public class KMeans {
    private List<Cluster> clusters;
    private List<Record> records;
    private int k;

    public KMeans(List<Record> records, int k) {
        this.records = records;
        this.k = k;
        this.clusters = new ArrayList<>();
    }

    public void initializeClusters() {
        for (int i = 0; i < k; i++) {
            Record record = records.get(i);
            clusters.add(new Cluster(record.getLa(), record.getLo()));
        }
    }

    public void performClustering(EuclideanDistance distance) throws InterruptedException {
        boolean isChanged = true;
        int numThreads = Runtime.getRuntime().availableProcessors();

        while (isChanged) {
            isChanged = false;

            for (Cluster cluster : clusters) {
                cluster.clearRecords();
            }

            List<List<Record>> chunks = splitIntoChunks(records, numThreads);
            List<Thread> threads = new ArrayList<>();
            List<PartialResult> partials = new ArrayList<>();

            for (List<Record> chunk : chunks) {
                PartialResult pr = new PartialResult(k);
                partials.add(pr);
                Thread t = new Thread(new AssignmentTask(chunk, clusters, pr, distance));
                threads.add(t);
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            for (int i = 0; i < k; i++) {
                double totalLat = 0;
                double totalLon = 0;
                int totalCount = 0;
                List<Record> allRecords = new ArrayList<>();

                for (PartialResult pr : partials) {
                    totalLat += pr.sumLat[i];
                    totalLon += pr.sumLon[i];
                    totalCount += pr.counts[i];
                    allRecords.addAll(pr.clusterRecords.get(i));
                }

                if (totalCount > 0) {
                    clusters.get(i).setRecords(allRecords);
                    Coordinate newCenter = new Coordinate(totalLat / totalCount, totalLon / totalCount);

                    double oldLat = clusters.get(i).getCenterLat();
                    double oldLon = clusters.get(i).getCenterLon();
                    if (oldLat != newCenter.getLat() || oldLon != newCenter.getLon()) {
                        clusters.get(i).updateCenter(newCenter);
                        isChanged = true;
                    }
                }
            }
        }
    }

    private List<List<Record>> splitIntoChunks(List<Record> records, int numChunks) {
        List<List<Record>> chunks = new ArrayList<>();
        int chunkSize = (int) Math.ceil((double) records.size() / numChunks);
        for (int i = 0; i < records.size(); i += chunkSize) {
            chunks.add(records.subList(i, Math.min(i + chunkSize, records.size())));
        }
        return chunks;
    }

    public List<Cluster> getClusters() {
        return clusters;
    }
}
