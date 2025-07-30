package org.distributed;

import mpi.*;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        final int MASTER = 0;

        // Hardcoded values
        int k = 10;
        int accumulationSites = 100;

        List<Record> allRecords = null;

        if (rank == MASTER) {
            System.out.println("Using hardcoded k = " + k + ", accumulationSites = " + accumulationSites);

            allRecords = FileReading.loadRecords("C:/Users/PC/Desktop/K Means Clustering/parallel - Copy (2)/src/main/java/germany/germany.json");
            if (accumulationSites > allRecords.size()) {
                int extra = accumulationSites - allRecords.size();
                List<Region> regions = RegionData.getLandRegions();
                Random random = new Random();
                for (int i = 0; i < extra; i++) {
                    Region region = regions.get(random.nextInt(regions.size()));
                    double lat = region.getLatStart() + (region.getLatEnd() - region.getLatStart()) * random.nextDouble();
                    double lon = region.getLonStart() + (region.getLonEnd() - region.getLonStart()) * random.nextDouble();
                    allRecords.add(new Record(lat, lon));
                }
            }
            allRecords = new ArrayList<>(allRecords.subList(0, accumulationSites));
        }

        // Broadcast k
        int[] kArr = new int[1];
        if (rank == MASTER) {
            kArr[0] = k;
        }
        MPI.COMM_WORLD.Bcast(kArr, 0, 1, MPI.INT, MASTER);
        k = kArr[0];
        System.out.println("Rank " + rank + " received k = " + k);

        // Serialize allRecords to byte array on master
        byte[] serializedRecords = null;
        int[] serializedLength = new int[1];
        if (rank == MASTER) {
            serializedRecords = serializeRecords(allRecords);
            serializedLength[0] = serializedRecords.length;
        }

        // Broadcast length of serializedRecords
        MPI.COMM_WORLD.Bcast(serializedLength, 0, 1, MPI.INT, MASTER);
        if (rank != MASTER) {
            serializedRecords = new byte[serializedLength[0]];
        }

        // Broadcast serializedRecords bytes
        MPI.COMM_WORLD.Bcast(serializedRecords, 0, serializedLength[0], MPI.BYTE, MASTER);

        // Deserialize on non-master
        if (rank != MASTER) {
            allRecords = deserializeRecords(serializedRecords);
        }
        System.out.println("Rank " + rank + " received allRecords size = " + allRecords.size());

        KMeans dkm = new KMeans(k, allRecords);
        dkm.runClustering();

        if (rank == MASTER) {
            List<Cluster> clusters = dkm.getFinalClusters();
            clusters.forEach(c -> System.out.printf("Cluster center: (%.5f, %.5f), size: %d\n",
                    c.getCenterLat(), c.getCenterLon(), c.getRecords().size()));

            JFrame mapFrame = Map.createMapFrame(clusters);
            mapFrame.setVisible(true);
        }

        MPI.Finalize();
    }

    // Serialization helper
    private static byte[] serializeRecords(List<Record> records) throws IOException {
        List<Record> safeList = new ArrayList<>(records);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(safeList);
        oos.flush();
        return bos.toByteArray();
    }

    // Deserialization helper
    @SuppressWarnings("unchecked")
    private static List<Record> deserializeRecords(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (List<Record>) ois.readObject();
    }
}
