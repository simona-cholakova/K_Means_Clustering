package org.distributed;

import mpi.*;

import java.io.*;
import java.util.*;

public class KMeans {

    private final int rank;
    private final int size;
    private final int k;
    private final List<Record> allRecords; //only set on rank 0
    private List<Cluster> finalClusters;

    public KMeans(int k, List<Record> allRecords) {
        this.k = k;
        this.allRecords = allRecords;
        this.rank = MPI.COMM_WORLD.Rank();
        this.size = MPI.COMM_WORLD.Size();
    }

    public void runClustering() throws Exception {
        int MASTER = 0;

        //broadcast k again
        int[] kArr = new int[]{k};
        MPI.COMM_WORLD.Bcast(kArr, 0, 1, MPI.INT, MASTER);

        //broadcast total number of records
        int totalRecords = allRecords != null ? allRecords.size() : 0;
        int[] recordCount = new int[]{totalRecords};
        MPI.COMM_WORLD.Bcast(recordCount, 0, 1, MPI.INT, MASTER);
        totalRecords = recordCount[0];

        int chunkSize = (int) Math.ceil((double) totalRecords / size);
        List<Record> localRecords;

        if (rank == MASTER) {
            System.out.println("Master is distributing the data chunks for each process...");
            for (int i = 1; i < size; i++) {
                int start = i * chunkSize;
                int end = Math.min((i + 1) * chunkSize, totalRecords);
                List<Record> sub = new ArrayList<>(allRecords.subList(start, end));
                byte[] bytes = serializeRecords(sub);
                //send length first
                int[] lenArr = new int[]{bytes.length};
                MPI.COMM_WORLD.Send(lenArr, 0, 1, MPI.INT, i, 0);
                //send serialized bytes
                MPI.COMM_WORLD.Send(bytes, 0, bytes.length, MPI.BYTE, i, 1);
            }
            //master local chunk
            localRecords = new ArrayList<>(allRecords.subList(0, Math.min(chunkSize, totalRecords)));
        } else {
            //receive length
            int[] lenArr = new int[1];
            MPI.COMM_WORLD.Recv(lenArr, 0, 1, MPI.INT, MASTER, 0);
            int length = lenArr[0];
            byte[] bytes = new byte[length];
            MPI.COMM_WORLD.Recv(bytes, 0, length, MPI.BYTE, MASTER, 1);
            localRecords = deserializeRecords(bytes);
        }
        System.out.println("Rank " + rank + " received localRecords size = " + localRecords.size());

        //initialize clusters on master
        List<Cluster> clusters = new ArrayList<>();
        ClusterCenter[] centers = new ClusterCenter[k];
        if (rank == MASTER) {
            for (int i = 0; i < k; i++) {
                Record r = allRecords.get(i);
                clusters.add(new Cluster(r.getLa(), r.getLo()));
                centers[i] = new ClusterCenter(r.getLa(), r.getLo());
            }
        }

        //broadcast initial centers
        centers = broadcastCenters(centers, k, MASTER);

        boolean changed;
        do {
            //assign records to nearest cluster
            PartialResult localResult = new PartialResult(k);
            EuclideanDistance distance = new EuclideanDistance();

            for (Record rec : localRecords) {
                int nearest = 0;
                double minDist = distance.calculate(rec.getLa(), rec.getLo(), centers[0].lat, centers[0].lon);
                for (int i = 1; i < k; i++) {
                    double dist = distance.calculate(rec.getLa(), rec.getLo(), centers[i].lat, centers[i].lon);
                    if (dist < minDist) {
                        nearest = i;
                        minDist = dist;
                    }
                }
                localResult.add(nearest, rec.getLa(), rec.getLo(), rec);
            }

            //gather PartialResult objects to master
            PartialResult[] allResults = null;
            if (rank == MASTER) {
                allResults = new PartialResult[size];
            }

            //serialize localResult
            byte[] serializedLocalResult = serializePartialResult(localResult);

            //first send length of serialized data to master
            int[] lenArr = new int[]{serializedLocalResult.length};
//            MPI.COMM_WORLD.Gather(lenArr, 0, 1, MPI.INT, null, 0, 0, MPI.INT, MASTER);

            //gather serialized bytes — tricky because size varies per rank
            //gather lengths first, then master receives each buffer individually

            if (rank == MASTER) {
                allResults = new PartialResult[size];
                //receive all serialized PartialResults from other ranks
                for (int i = 0; i < size; i++) {
                    int length;
                    if (i == MASTER) {
                        length = serializedLocalResult.length;
                    } else {
                        int[] recvLen = new int[1];
                        MPI.COMM_WORLD.Recv(recvLen, 0, 1, MPI.INT, i, 100 + i);
                        length = recvLen[0];
                    }
                    byte[] buffer = new byte[length];
                    if (i == MASTER) {
                        buffer = serializedLocalResult;
                    } else {
                        MPI.COMM_WORLD.Recv(buffer, 0, length, MPI.BYTE, i, 200 + i);
                    }
                    allResults[i] = deserializePartialResult(buffer);
                }
            } else {
                //send length to master
                MPI.COMM_WORLD.Send(lenArr, 0, 1, MPI.INT, MASTER, 100 + rank);
                //send serialized bytes to master
                MPI.COMM_WORLD.Send(serializedLocalResult, 0, serializedLocalResult.length, MPI.BYTE, MASTER, 200 + rank);
            }

            changed = false;
            if (rank == MASTER) {
                double[] sumLat = new double[k];
                double[] sumLon = new double[k];
                int[] counts = new int[k];
                List<List<Record>> newClusters = new ArrayList<>(k);
                for (int i = 0; i < k; i++) {
                    newClusters.add(new ArrayList<>());
                }

                for (PartialResult pr : allResults) {
                    for (int i = 0; i < k; i++) {
                        sumLat[i] += pr.sumLat[i];
                        sumLon[i] += pr.sumLon[i];
                        counts[i] += pr.counts[i];
                        newClusters.set(i, merge(newClusters.get(i), pr.clusterRecords.get(i)));
                    }
                }

                for (int i = 0; i < k; i++) {
                    if (counts[i] == 0) continue;
                    double newLat = sumLat[i] / counts[i];
                    double newLon = sumLon[i] / counts[i];
                    if (clusters.get(i).getCenterLat() != newLat || clusters.get(i).getCenterLon() != newLon) {
                        changed = true;
                        clusters.get(i).updateCenter(new Coordinate(newLat, newLon));
                    }
                    clusters.get(i).setRecords(newClusters.get(i));
                    centers[i] = new ClusterCenter(clusters.get(i).getCenterLat(), clusters.get(i).getCenterLon());
                }
                System.out.println("Iteration completed. Centers updated. Changed = " + changed);
            }

            //broadcast the changed flag
            boolean[] changedArr = new boolean[]{changed};
            MPI.COMM_WORLD.Bcast(changedArr, 0, 1, MPI.BOOLEAN, MASTER);
            changed = changedArr[0];

            //broadcast the new centers
            centers = broadcastCenters(centers, k, MASTER);

        } while (changed);

        if (rank == MASTER) {
            finalClusters = clusters;
        }
    }

    public List<Cluster> getFinalClusters() {
        return finalClusters;
    }

    private static List<Record> merge(List<Record> a, List<Record> b) {
        List<Record> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }

    private ClusterCenter[] broadcastCenters(ClusterCenter[] centers, int k, int MASTER) throws IOException, ClassNotFoundException {
        byte[] serializedCenters = null;
        int[] lenArr = new int[1];

        if (rank == MASTER) {
            serializedCenters = serializeCenters(centers);
            lenArr[0] = serializedCenters.length;
        }
        MPI.COMM_WORLD.Bcast(lenArr, 0, 1, MPI.INT, MASTER);
        if (rank != MASTER) {
            serializedCenters = new byte[lenArr[0]];
        }
        MPI.COMM_WORLD.Bcast(serializedCenters, 0, lenArr[0], MPI.BYTE, MASTER);

        if (rank != MASTER) {
            centers = deserializeCenters(serializedCenters);
        }
        return centers;
    }

    private static byte[] serializeRecords(List<Record> records) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(records);
        oos.flush();
        return bos.toByteArray();
    }

    private static List<Record> deserializeRecords(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (List<Record>) ois.readObject();
    }

    private static byte[] serializePartialResult(PartialResult pr) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(pr);
        oos.flush();
        return bos.toByteArray();
    }

    private static PartialResult deserializePartialResult(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (PartialResult) ois.readObject();
    }

    private static byte[] serializeCenters(ClusterCenter[] centers) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(centers);
        oos.flush();
        return bos.toByteArray();
    }

    private static ClusterCenter[] deserializeCenters(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (ClusterCenter[]) ois.readObject();
    }
}
