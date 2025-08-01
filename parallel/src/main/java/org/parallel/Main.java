package org.parallel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            //panel for all inputs
            JPanel panel = new JPanel(new GridLayout(0, 2));

            int defaultWidth = 800;
            int defaultHeight = 600;

            JTextField widthField = new JTextField(String.valueOf(defaultWidth));
            JTextField heightField = new JTextField(String.valueOf(defaultHeight));
            JTextField kField = new JTextField();
            JTextField accumulationField = new JTextField();

            panel.add(new JLabel("Map Width (default 800):"));
            panel.add(widthField);
            panel.add(new JLabel("Map Height (default 600):"));
            panel.add(heightField);
            panel.add(new JLabel("Number of Clusters (k):"));
            panel.add(kField);
            panel.add(new JLabel("Number of Accumulation Sites:"));
            panel.add(accumulationField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Enter Parameters",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                System.out.println("User cancelled the input. Exiting.");
                return;
            }

            int width, height, k, accumulationSites;
            try {
                width = Integer.parseInt(widthField.getText().trim());
                height = Integer.parseInt(heightField.getText().trim());
                k = Integer.parseInt(kField.getText().trim());
                accumulationSites = Integer.parseInt(accumulationField.getText().trim());

                if (width <= 0 || height <= 0 || k <= 0 || accumulationSites <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid input. All values must be positive integers.");
                return;
            }

            //summary in terminal
            System.out.println("Map Size: " + width + "x" + height);
            System.out.println("Number of Clusters (k): " + k);
            System.out.println("Number of Accumulation Sites: " + accumulationSites);

            List<Record> records = FileReading.loadRecords("src/main/java/germany/germany.json");

            if (records.isEmpty()) {
                System.out.println("No records found. Exiting.");
                return;
            }

            if (accumulationSites > records.size()) {
                System.out.println("Generating additional random locations...");

                int additionalSites = accumulationSites - records.size();
                Random random = new Random();
                List<Region> landRegions = RegionData.getLandRegions();

                for (int i = 0; i < additionalSites; i++) {
                    Region region = landRegions.get(random.nextInt(landRegions.size()));
                    double lat = region.getLatStart() + (region.getLatEnd() - region.getLatStart()) * random.nextDouble();
                    double lon = region.getLonStart() + (region.getLonEnd() - region.getLonStart()) * random.nextDouble();
                    records.add(new Record(lat, lon));
                }
            }

            List<Record> filteredRecords = records.subList(0, accumulationSites);
            KMeans kMeans = new KMeans(filteredRecords, k);
            kMeans.initializeClusters();
            EuclideanDistance distance = new EuclideanDistance();

            try {
                kMeans.performClustering(distance);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            List<Cluster> clusters = kMeans.getClusters();
            System.out.println("\nCluster Details:");
            for (int i = 0; i < clusters.size(); i++) {
                Cluster cluster = clusters.get(i);
                System.out.printf("Cluster %d Center: (%.6f, %.6f) with %d records.%n",
                        i + 1, cluster.getCenterLat(), cluster.getCenterLon(), cluster.getRecords().size());
            }

            JFrame mapFrame = Map.createMapFrame(clusters);
            mapFrame.setSize(width, height);
            mapFrame.setVisible(true);

            long end = System.currentTimeMillis() - start;
            System.out.println("\nThe program has been running for: " + end + "ms");
        });
    }
}
