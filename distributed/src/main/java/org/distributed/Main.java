package org.distributed;

import mpi.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        final int MASTER = 0;

        int mapWidth = 800;
        int mapHeight = 600;
        int k = 0;
        int accumulationSites = 0;

        if (rank == MASTER) {
            JPanel panel = new JPanel(new GridLayout(0, 2));
            JTextField widthField = new JTextField("800");
            JTextField heightField = new JTextField("600");
            JTextField kField = new JTextField();
            JTextField accField = new JTextField();

            panel.add(new JLabel("Map Width (default 800):"));
            panel.add(widthField);
            panel.add(new JLabel("Map Height (default 600):"));
            panel.add(heightField);
            panel.add(new JLabel("Number of Clusters (k):"));
            panel.add(kField);
            panel.add(new JLabel("Number of Accumulation Sites:"));
            panel.add(accField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Distributed Version",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                System.out.println("User cancelled the input. Exiting.");
                MPI.Finalize();
                return;
            }

            try {
                mapWidth = Integer.parseInt(widthField.getText().trim());
                mapHeight = Integer.parseInt(heightField.getText().trim());
                k = Integer.parseInt(kField.getText().trim());
                accumulationSites = Integer.parseInt(accField.getText().trim());

                if (mapWidth <= 0 || mapHeight <= 0 || k <= 0 || accumulationSites <= 0) {
                    throw new NumberFormatException();
                }

                if (k > accumulationSites) {
                    JOptionPane.showMessageDialog(null,
                            "Please enter a number of clusters (k) that is less than or equal to the number of accumulation sites.",
                            "Invalid Input",
                            JOptionPane.WARNING_MESSAGE);
                    MPI.Finalize();
                    return;
                }

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null,
                        "Please enter valid positive integers.",
                        "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                MPI.Finalize();
                return;
            }

            System.out.println("Map Size: " + mapWidth + "x" + mapHeight);
            System.out.println("Using k = " + k + ", accumulationSites = " + accumulationSites);
        }

        // Broadcast map size
        int[] sizeArr = new int[2];
        if (rank == MASTER) {
            sizeArr[0] = mapWidth;
            sizeArr[1] = mapHeight;
        }
        MPI.COMM_WORLD.Bcast(sizeArr, 0, 2, MPI.INT, MASTER);
        mapWidth = sizeArr[0];
        mapHeight = sizeArr[1];

        // Broadcast k
        int[] kArr = new int[1];
        if (rank == MASTER) {
            kArr[0] = k;
        }
        MPI.COMM_WORLD.Bcast(kArr, 0, 1, MPI.INT, MASTER);
        k = kArr[0];

        System.out.println("Rank " + rank + " received map size: width=" + mapWidth + ", height=" + mapHeight);
        System.out.println("Rank " + rank + " received k = " + k);

        List<Record> allRecords = null;

        if (rank == MASTER) {
            allRecords = FileReading.loadRecords("distributed/src/main/java/germany/germany.json");

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

        // Serialize allRecords to byte array on master
        byte[] serializedRecords = null;
        int[] serializedLength = new int[1];
        if (rank == MASTER) {
            serializedRecords = serializeRecords(allRecords);
            serializedLength[0] = serializedRecords.length;
        }

        MPI.COMM_WORLD.Bcast(serializedLength, 0, 1, MPI.INT, MASTER);
        if (rank != MASTER) {
            serializedRecords = new byte[serializedLength[0]];
        }

        MPI.COMM_WORLD.Bcast(serializedRecords, 0, serializedLength[0], MPI.BYTE, MASTER);

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

    private static byte[] serializeRecords(List<Record> records) throws IOException {
        List<Record> safeList = new ArrayList<>(records);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(safeList);
        oos.flush();
        return bos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<Record> deserializeRecords(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (List<Record>) ois.readObject();
    }
}
