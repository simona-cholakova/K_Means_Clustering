package controller;

import javax.swing.*;
import java.awt.*;

public class MainController {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Select Mode");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 1, 10, 10));

        JLabel label = new JLabel("Choose a mode to run:", SwingConstants.CENTER);
        frame.add(label, BorderLayout.NORTH);

        JButton distributedButton = new JButton("Distributed");
        JButton parallelButton = new JButton("Parallel");
        JButton sequentialButton = new JButton("Sequential");

        panel.add(distributedButton);
        panel.add(parallelButton);
        panel.add(sequentialButton);

        frame.add(panel, BorderLayout.CENTER);

        distributedButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(frame,
                    "Please run the distributed version through the distributed configuration.",
                    "Distributed Mode Info",
                    JOptionPane.INFORMATION_MESSAGE);
            frame.dispose();

        });

        parallelButton.addActionListener(e -> {
            frame.dispose();
            try {
                org.parallel.Main.main(new String[]{});
            } catch (Exception ex) {
                showError(ex);
            }
        });

        sequentialButton.addActionListener(e -> {
            frame.dispose();
            try {
                org.example.Main.main(new String[]{});
            } catch (Exception ex) {
                showError(ex);
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void showError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
