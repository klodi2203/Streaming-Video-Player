package com.videostreaming.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.videostreaming.common.VideoFile;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ServerGUI extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ServerGUI.class);
    private final JTextArea logArea;
    private final JList<String> videoList;
    private final DefaultListModel<String> listModel;
    private final VideoProcessor videoProcessor;
    private final StreamingServer server;

    public ServerGUI(int port) {
        // Setup main window
        setTitle("Video Streaming Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        // Initialize server components first
        videoProcessor = new VideoProcessor(false);
        server = new StreamingServer(port, videoProcessor);
        server.setGUI(this);

        // Create status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("Server Status: Running on port " + port);
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.NORTH);

        // Create split pane for log and video list
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // Create video list panel
        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);
        JScrollPane videoScrollPane = new JScrollPane(videoList);
        videoScrollPane.setBorder(BorderFactory.createTitledBorder("Available Videos"));
        splitPane.setLeftComponent(videoScrollPane);

        // Create log panel
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        splitPane.setRightComponent(logScrollPane);

        // Add split pane to frame
        add(splitPane, BorderLayout.CENTER);

        // Create control panel
        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh Video List");
        refreshButton.addActionListener(e -> refreshVideoList());
        controlPanel.add(refreshButton);
        JButton rescanButton = new JButton("Rescan Videos");
        rescanButton.addActionListener(e -> {
            addLogMessage("Rescanning videos directory...");
            videoProcessor.scanVideos();
            refreshVideoList();
            addLogMessage("Video list refreshed.");
        });
        controlPanel.add(rescanButton);
        add(controlPanel, BorderLayout.SOUTH);

        // Set split pane proportions
        splitPane.setDividerLocation(300);

        // Start server in separate thread
        new Thread(() -> server.start()).start();
        
        // Start video processing in background
        new Thread(() -> {
            addLogMessage("Starting video processing in background...");
            videoProcessor.initializeVideoDirectory();
            videoProcessor.scanVideos();
            SwingUtilities.invokeLater(this::refreshVideoList);
            addLogMessage("Video processing complete.");
        }).start();
    }

    private void refreshVideoList() {
        listModel.clear();
        
        // Get videos and sort them by format first, then by resolution
        List<VideoFile> videos = videoProcessor.getAvailableVideos();
        
        // Sort videos: first by format, then by resolution (in descending order)
        videos.sort((v1, v2) -> {
            // First compare formats
            int formatCompare = v1.getFormat().compareTo(v2.getFormat());
            if (formatCompare != 0) {
                return formatCompare;
            }
            
            // If formats are the same, compare resolutions (higher resolution first)
            int res1 = getHeightFromResolution(v1.getResolution());
            int res2 = getHeightFromResolution(v2.getResolution());
            return Integer.compare(res2, res1); // Descending order
        });
        
        // Add sorted videos to the list model
        for (VideoFile video : videos) {
            listModel.addElement(video.toString());
        }
    }

    // Helper method to extract height from resolution string
    private int getHeightFromResolution(String resolution) {
        return Integer.parseInt(resolution.substring(0, resolution.length() - 1));
    }

    public void addLogMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public VideoProcessor getVideoProcessor() {
        return videoProcessor;
    }

    public StreamingServer getServer() {
        return server;
    }
} 