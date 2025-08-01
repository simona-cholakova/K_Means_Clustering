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

        int mapWidth = 800;  // default width
        int mapHeight = 600; // default height

        if (rank == MASTER) {
            int option = JOptionPane.showOptionDialog(
                    null,
                    "Do you want the map to be the default size or set it manually?",
                    "Map Size Preference",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new Object[]{"Default Size", "Set Manually"},
                    "Default Size"
            );

            if (option == 1) { // Set Manually
                boolean validSize = false;
                while (!validSize) {
                    try {
                        String widthInput = JOptionPane.showInputDialog(null, "Enter map width (pixels):", "Input", JOptionPane.QUESTION_MESSAGE);
                        if (widthInput == null) throw new IllegalArgumentException("Cancelled");
                        mapWidth = Integer.parseInt(widthInput);
                        if (mapWidth <= 0) throw new IllegalArgumentException("Width must be positive.");

                        String heightInput = JOptionPane.showInputDialog(null, "Enter map height (pixels):", "Input", JOptionPane.QUESTION_MESSAGE);
                        if (heightInput == null) throw new IllegalArgumentException("Cancelled");
                        mapHeight = Integer.parseInt(heightInput);
                        if (mapHeight <= 0) throw new IllegalArgumentException("Height must be positive.");

                        validSize = true;
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null, "Please enter valid integer numbers.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    } catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(null, e.getMessage(), "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else if (option == JOptionPane.CLOSED_OPTION) {
                System.out.println("No selection made for map size. Using default.");
            }
        }

        //broadcast map width and height
        int[] sizeArr = new int[2];
        if (rank == MASTER) {
            sizeArr[0] = mapWidth;
            sizeArr[1] = mapHeight;
        }
        MPI.COMM_WORLD.Bcast(sizeArr, 0, 2, MPI.INT, MASTER);
        mapWidth = sizeArr[0];
        mapHeight = sizeArr[1];

        System.out.println("Rank " + rank + " received map size: width=" + mapWidth + ", height=" + mapHeight);

        int k = 0;
        int accumulationSites = 0;

        if (rank == MASTER) {
            boolean validInput = false;

            while (!validInput) {
                try {
                    String kInput = JOptionPane.showInputDialog(null, "Enter number of clusters (k):", "Input", JOptionPane.QUESTION_MESSAGE);
                    if (kInput == null) throw new IllegalArgumentException("Cancelled");

                    k = Integer.parseInt(kInput);
                    if (k <= 0) throw new IllegalArgumentException("k must be a positive integer.");

                    String sitesInput = JOptionPane.showInputDialog(null, "Enter number of accumulation sites:", "Input", JOptionPane.QUESTION_MESSAGE);
                    if (sitesInput == null) throw new IllegalArgumentException("Cancelled");

                    accumulationSites = Integer.parseInt(sitesInput);
                    if (accumulationSites <= 0) throw new IllegalArgumentException("Accumulation sites must be a positive integer.");

                    if (k > accumulationSites) {
                        throw new IllegalArgumentException("Number of clusters (k) cannot be greater than accumulation sites.");
                    }

                    validInput = true; // all validations passed

                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Please enter valid integers.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                } catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(null, e.getMessage(), "Invalid Input", JOptionPane.ERROR_MESSAGE);
                }
            }

            System.out.println("Using k = " + k + ", accumulationSites = " + accumulationSites);
        }

        List<Record> allRecords = null;

        if (rank == MASTER) {
            System.out.println("Using hardcoded k = " + k + ", accumulationSites = " + accumulationSites);

            allRecords = FileReading.loadRecords("src/main/java/germany/germany.json");
            //"src/main/java/germany/germany.json" - path from content root
            //"C:/Users/PC/Desktop/K_Means_Clustering/distributed/src/main/java/germany/germany.json" - absolute path

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

        //broadcast k
        int[] kArr = new int[1];
        if (rank == MASTER) {
            kArr[0] = k;
        }
        MPI.COMM_WORLD.Bcast(kArr, 0, 1, MPI.INT, MASTER);
        k = kArr[0];
        System.out.println("Rank " + rank + " received k = " + k);

        //serialize allRecords to byte array on master
        byte[] serializedRecords = null;
        int[] serializedLength = new int[1];
        if (rank == MASTER) {
            serializedRecords = serializeRecords(allRecords);
            serializedLength[0] = serializedRecords.length;
        }

        //broadcast length of serializedRecords
        MPI.COMM_WORLD.Bcast(serializedLength, 0, 1, MPI.INT, MASTER);
        if (rank != MASTER) {
            serializedRecords = new byte[serializedLength[0]];
        }

        //broadcast serializedRecords bytes
        MPI.COMM_WORLD.Bcast(serializedRecords, 0, serializedLength[0], MPI.BYTE, MASTER);

        //deserialize on non-master
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
            mapFrame.setSize(mapWidth, mapHeight);
            mapFrame.setVisible(true);
        }

        MPI.Finalize();
    }

    //serialization function
    private static byte[] serializeRecords(List<Record> records) throws IOException {
        List<Record> safeList = new ArrayList<>(records);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(safeList);
        oos.flush();
        return bos.toByteArray();
    }

    //deserialization function
    @SuppressWarnings("unchecked")
    private static List<Record> deserializeRecords(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (List<Record>) ois.readObject();
    }
}
