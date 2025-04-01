package com.videostreaming.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.videostreaming.common.VideoFile;

public class VideoStreamingClient extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(VideoStreamingClient.class);
    private final String serverHost;
    private final int serverPort;
    private final JList<String> videoList;
    private final DefaultListModel<String> listModel;
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private JButton playButton;
    private String recommendedResolution;
    private double lastMeasuredBandwidth = -1;

    public VideoStreamingClient(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
        
        // Setup GUI
        setTitle("Video Streaming Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        // Create video list panel
        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(videoList);
        add(scrollPane, BorderLayout.CENTER);

        // Create a simple control panel with just the Play button
        JPanel controlPanel = new JPanel();
        playButton = new JButton("Play");
        playButton.addActionListener(e -> playSelectedVideo());
        controlPanel.add(playButton);
        add(controlPanel, BorderLayout.SOUTH);
        
        // Add a double-click listener to the video list
        videoList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    playSelectedVideo();
                }
            }
        });
    }

    public void start() {
        try {
            // Connect to server first, but don't fetch videos yet
            connectToServer();
            
            // Show the window
            setVisible(true);
            
            // Now run the speed test with a progress bar
            SwingUtilities.invokeLater(() -> runConnectionSpeedTest());
            
        } catch (Exception e) {
            logger.error("Error starting client: {}", e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Failed to connect to server: " + e.getMessage(),
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void connectToServer() throws IOException {
        logger.info("Connecting to server {}:{}", serverHost, serverPort);
        socket = new Socket(serverHost, serverPort);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    @SuppressWarnings("unchecked")
    private void fetchVideoList() throws IOException, ClassNotFoundException {
        List<VideoFile> videos = (List<VideoFile>) in.readObject();
        
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
        
        // Update the list model
        listModel.clear();
        for (VideoFile video : videos) {
            listModel.addElement(video.toString());
        }
    }

    // Helper method to extract height from resolution string
    private int getHeightFromResolution(String resolution) {
        return Integer.parseInt(resolution.substring(0, resolution.length() - 1));
    }

    private void playSelectedVideo() {
        String selectedVideo = videoList.getSelectedValue();
        if (selectedVideo == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a video first",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Extract the video filename
            final String videoFileName = selectedVideo;
            
            // Look for the video in the server's videos directory
            File videoFile = new File("../VideoStreamingServer/videos/" + videoFileName);
            
            if (!videoFile.exists()) {
                logger.error("Video file not found: {}", videoFile.getAbsolutePath());
                
                // Try alternative paths
                File[] alternativePaths = {
                    new File("VideoStreamingServer/videos/" + videoFileName),
                    new File("videos/" + videoFileName),
                    new File("/home/klodi/Desktop/video-streaming-app/VideoStreamingServer/videos/" + videoFileName)
                };
                
                boolean found = false;
                for (File altPath : alternativePaths) {
                    if (altPath.exists()) {
                        videoFile = altPath;
                        found = true;
                        logger.info("Found video at alternative path: {}", altPath.getAbsolutePath());
                        break;
                    }
                }
                
                if (!found) {
                    JOptionPane.showMessageDialog(this,
                        "Video file not found. Please check the path: " + videoFile.getAbsolutePath(),
                        "File Not Found",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            
            // Create a final copy of videoFile for use in the lambda
            final File finalVideoFile = videoFile;
            logger.info("Opening video in separate player window: {}", finalVideoFile.getAbsolutePath());
            
            // Create and show the video player window
            SwingUtilities.invokeLater(() -> {
                VideoPlayerWindow playerWindow = new VideoPlayerWindow(finalVideoFile);
                playerWindow.setVisible(true);
                playerWindow.startPlayback(); // Start playback after window is visible
            });
            
        } catch (Exception e) {
            logger.error("Error opening video player: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(this,
                "Error opening video player: " + e.getMessage(),
                "Player Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectFormatAndFetchVideos() {
        // Get available formats from the server
        try {
            // Request available formats
            out.writeObject("GET_FORMATS");
            out.flush();
            
            // Receive formats list
            @SuppressWarnings("unchecked")
            List<String> availableFormats = (List<String>) in.readObject();
            
            if (availableFormats == null || availableFormats.isEmpty()) {
                logger.warn("No formats received from server, using default formats");
                availableFormats = new ArrayList<>();
                availableFormats.add(".mp4");
                availableFormats.add(".mkv");
                availableFormats.add(".avi");
            }
            
            // Show format selection dialog
            String selectedFormat = showFormatSelectionDialog(availableFormats);
            
            if (selectedFormat != null) {
                // Send format and bandwidth to server
                double bandwidth = getLastMeasuredBandwidth();
                
                // Create request object with format and bandwidth
                Map<String, Object> request = new HashMap<>();
                request.put("action", "FILTER_VIDEOS");
                request.put("format", selectedFormat);
                request.put("bandwidth", bandwidth);
                request.put("resolution", recommendedResolution);
                
                // Send request to server
                out.writeObject(request);
                out.flush();
                
                // Receive filtered video list
                fetchFilteredVideoList();
                
                // Update window title to show selected format
                setTitle(String.format("Video Streaming Client - %.2f Mbps - %s", 
                    bandwidth, selectedFormat));
            } else {
                // If user cancels format selection, just fetch all videos
                out.writeObject("GET_VIDEOS");
                out.flush();
                fetchVideoList();
            }
        } catch (Exception e) {
            logger.error("Error selecting format: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(this,
                "Error communicating with server: " + e.getMessage() + 
                "\nFetching all available videos instead.",
                "Communication Error",
                JOptionPane.WARNING_MESSAGE);
            
            // Try to fetch all videos as a fallback
            try {
                out.writeObject("GET_VIDEOS");
                out.flush();
                fetchVideoList();
            } catch (Exception ex) {
                logger.error("Failed to fetch videos: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(this,
                    "Failed to fetch videos: " + ex.getMessage(),
                    "Communication Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String showFormatSelectionDialog(List<String> formats) {
        // Create a dialog for format selection
        JDialog dialog = new JDialog(this, "Select Video Format", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(this);
        
        // Create format selection panel
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel label = new JLabel("Select the video format you want to watch:");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Create radio buttons for each format
        ButtonGroup group = new ButtonGroup();
        JRadioButton[] radioButtons = new JRadioButton[formats.size()];
        
        for (int i = 0; i < formats.size(); i++) {
            radioButtons[i] = new JRadioButton(formats.get(i));
            radioButtons[i].setActionCommand(formats.get(i));
            radioButtons[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            if (i == 0) {
                radioButtons[i].setSelected(true);
            }
            group.add(radioButtons[i]);
            panel.add(radioButtons[i]);
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
        }
        
        // Add buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        // Add panels to dialog
        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // Set up result variable
        final String[] result = {null};
        
        // Add button actions
        okButton.addActionListener(e -> {
            ButtonModel selectedButton = group.getSelection();
            if (selectedButton != null) {
                result[0] = selectedButton.getActionCommand();
            }
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        // Show dialog
        dialog.setVisible(true);
        
        return result[0];
    }

    @SuppressWarnings("unchecked")
    private void fetchFilteredVideoList() throws IOException, ClassNotFoundException {
        List<VideoFile> videos = (List<VideoFile>) in.readObject();
        
        // Update the list model
        listModel.clear();
        for (VideoFile video : videos) {
            listModel.addElement(video.toString());
        }
        
        if (videos.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No videos available with the selected format and your connection speed.",
                "No Videos Available",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private double getLastMeasuredBandwidth() {
        // If we have a ConnectionSpeedTest instance, get the last measured bandwidth
        // Otherwise return a default value
        return lastMeasuredBandwidth > 0 ? lastMeasuredBandwidth : 4.0; // Default to 4 Mbps
    }

    private String getRecommendedResolutionForBandwidth(double bandwidth) {
        // Convert Mbps to Kbps (1 Mbps = 1000 Kbps)
        double kbps = bandwidth * 1000;
        
        // Based on the provided YouTube resolution/bitrate table
        if (kbps < 700) {
            return "240p"; // Maximum bitrate for 240p is 700 Kbps
        } else if (kbps < 2000) {
            return "360p"; // Maximum bitrate for 360p is 1000 Kbps, but we allow up to 2000 Kbps
        } else if (kbps < 4000) {
            return "480p"; // Maximum bitrate for 480p is 2000 Kbps, but we allow up to 4000 Kbps
        } else if (kbps < 6000) {
            return "720p"; // Maximum bitrate for 720p is 4000 Kbps, but we allow up to 6000 Kbps
        } else {
            return "1080p"; // Maximum bitrate for 1080p is 6000 Kbps
        }
    }

    private void runConnectionSpeedTest() {
        // Create a progress dialog with a progress bar
        JDialog progressDialog = new JDialog(this, "Connection Speed Test", true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);
        
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel messageLabel = new JLabel("Testing your connection speed...");
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setIndeterminate(true); // Start with indeterminate progress
        
        JLabel statusLabel = new JLabel("Initializing test...");
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        
        contentPanel.add(messageLabel, BorderLayout.NORTH);
        contentPanel.add(progressBar, BorderLayout.CENTER);
        contentPanel.add(statusLabel, BorderLayout.SOUTH);
        
        progressDialog.add(contentPanel, BorderLayout.CENTER);
        
        // Run the speed test in a background thread
        new Thread(() -> {
            try {
                // Create a custom speed test that updates the progress bar
                ConnectionSpeedTest speedTest = new ConnectionSpeedTest() {
                    @Override
                    public double testConnectionSpeed() {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Starting speed test..."));
                        return super.testConnectionSpeed();
                    }
                    
                    // Override to update progress
                    public void updateProgress(int percent, String message) {
                        SwingUtilities.invokeLater(() -> {
                            if (progressBar.isIndeterminate()) {
                                progressBar.setIndeterminate(false);
                            }
                            progressBar.setValue(percent);
                            statusLabel.setText(message);
                        });
                    }
                };
                
                // Run the test
                double downloadSpeed = speedTest.testConnectionSpeed();
                lastMeasuredBandwidth = downloadSpeed; // Store the bandwidth
                recommendedResolution = speedTest.getRecommendedResolution();
                
                // Close the progress dialog
                SwingUtilities.invokeLater(() -> progressDialog.dispose());
                
                // Show the results
                if (downloadSpeed > 0) {
                    // Show the results to the user
                    JOptionPane.showMessageDialog(this,
                        String.format("Connection speed test completed.\n\n" +
                            "Download speed: %.2f Mbps\n" +
                            "Recommended video quality: %s\n\n" +
                            "Videos will be automatically filtered to show this quality or lower.",
                            downloadSpeed, recommendedResolution),
                        "Connection Speed Test Results",
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    // Update the window title to show the connection speed
                    setTitle(String.format("Video Streaming Client - %.2f Mbps", downloadSpeed));
                    
                    // Now explicitly call the format selection method
                    SwingUtilities.invokeLater(() -> selectFormatAndFetchVideos());
                } else {
                    recommendedResolution = "480p"; // Default if test failed
                    JOptionPane.showMessageDialog(this,
                        "Connection speed test failed. Using default video quality (480p).",
                        "Connection Speed Test Failed",
                        JOptionPane.WARNING_MESSAGE);
                    
                    // Even if the test failed, still show format selection
                    SwingUtilities.invokeLater(() -> selectFormatAndFetchVideos());
                }
                
                logger.info("Connection speed test result: {} Mbps, recommended resolution: {}", 
                    downloadSpeed, recommendedResolution);
                
            } catch (Exception e) {
                logger.error("Error during speed test: {}", e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(VideoStreamingClient.this,
                        "Error during speed test: " + e.getMessage(),
                        "Speed Test Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        
        // Show the dialog (this will block until the dialog is closed)
        progressDialog.setVisible(true);
    }
} 