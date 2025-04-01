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
import com.videostreaming.common.VideoFile;

public class VideoStreamingClient extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(VideoStreamingClient.class);
    private final String serverHost;
    private final int serverPort;
    private final JList<String> videoList;
    private final DefaultListModel<String> listModel;
    private EmbeddedMediaPlayerComponent mediaPlayerComponent = null;
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

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
        scrollPane.setPreferredSize(new Dimension(200, getHeight()));
        add(scrollPane, BorderLayout.WEST);

        // Create media player component
        try {
            mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
            add(mediaPlayerComponent, BorderLayout.CENTER);
            logger.info("Successfully created media player component");
        } catch (Exception e) {
            logger.error("Failed to create media player component: {}", e.getMessage(), e);
            
            // Create a fallback panel
            JPanel fallbackPanel = new JPanel();
            fallbackPanel.setBackground(Color.BLACK);
            fallbackPanel.setLayout(new BorderLayout());
            
            JLabel fallbackLabel = new JLabel("Media player component could not be created. Videos will open in external player.");
            fallbackLabel.setForeground(Color.WHITE);
            fallbackLabel.setHorizontalAlignment(JLabel.CENTER);
            fallbackPanel.add(fallbackLabel, BorderLayout.CENTER);
            
            add(fallbackPanel, BorderLayout.CENTER);
        }

        // Create control panel
        JPanel controlPanel = new JPanel();
        JButton playButton = new JButton("Play");
        playButton.addActionListener(e -> playSelectedVideo());
        
        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopPlayback());
        
        controlPanel.add(playButton);
        controlPanel.add(stopButton);
        add(controlPanel, BorderLayout.SOUTH);
        
        // Add window listener to clean up resources on close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopPlayback();
                if (mediaPlayerComponent != null) {
                    mediaPlayerComponent.release();
                }
            }
        });
    }

    public void start() {
        try {
            connectToServer();
            fetchVideoList();
            setVisible(true);
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
        List<?> videos = (List<?>) in.readObject();
        listModel.clear();
        for (Object video : videos) {
            listModel.addElement(video.toString());
        }
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

        // Extract the video filename
        String videoFileName = selectedVideo;
        File videoFile = null;
        
        try {
            // Look for the video in the server's videos directory
            videoFile = new File("../VideoStreamingServer/videos/" + videoFileName);
            
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
            
            logger.info("Playing video in embedded player: {}", videoFile.getAbsolutePath());
            
            // Stop any existing playback
            stopPlayback();
            
            // Update window title
            setTitle("Video Streaming Client - Playing: " + videoFileName);
            
            // If mediaPlayerComponent is null, go straight to fallback
            if (mediaPlayerComponent == null) {
                logger.info("Media player component not available, using external player");
                fallbackToExternalPlayer(videoFile);
                return;
            }
            
            // Play the video in the embedded player
            boolean started = false;
            try {
                started = mediaPlayerComponent.mediaPlayer().media().play(videoFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Error starting media playback: {}", e.getMessage(), e);
                fallbackToExternalPlayer(videoFile);
                return;
            }
            
            if (started) {
                logger.info("Video playback started successfully");
                
                // Add a listener for playback events
                mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(
                    new MediaPlayerEventAdapter() {
                        @Override
                        public void finished(MediaPlayer mediaPlayer) {
                            logger.info("Playback finished");
                        }
                        
                        @Override
                        public void error(MediaPlayer mediaPlayer) {
                            logger.error("Playback error");
                        }
                    }
                );
            } else {
                logger.error("Failed to start video playback");
                fallbackToExternalPlayer(videoFile);
            }
            
        } catch (Exception e) {
            logger.error("Error playing video: {}", e.getMessage(), e);
            if (videoFile != null) {
                fallbackToExternalPlayer(videoFile);
            }
        }
    }
    
    private void stopPlayback() {
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.mediaPlayer().controls().stop();
            logger.info("Playback stopped");
        }
        
        // Reset window title
        setTitle("Video Streaming Client");
    }

    private void fallbackToExternalPlayer(File videoFile) {
        try {
            logger.info("Falling back to external player for: {}", videoFile.getAbsolutePath());
            
            // Try to open with system default player
            Desktop.getDesktop().open(videoFile);
            
            JOptionPane.showMessageDialog(this,
                "The embedded player failed to play the video.\n" +
                "Opening the video in your system's default player instead.",
                "Using External Player",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (IOException e) {
            logger.error("Error opening external player: {}", e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Could not open the video with any player: " + e.getMessage(),
                "Playback Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
} 