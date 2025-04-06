package com.videostreaming.client.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A standalone video player window using VLCJ to embed VLC media player.
 * Supports playing both local video files and network streams.
 */
public class VideoPlayerWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(VideoPlayerWindow.class);
    
    // Keep track of all active player windows for proper cleanup
    private static final List<VideoPlayerWindow> activeWindows = new ArrayList<>();
    private static MediaPlayerFactory sharedFactory = null;
    
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private final JButton playPauseButton;
    private final JButton stopButton;
    private final JButton rewindButton;
    private final JButton forwardButton;
    private final JSlider positionSlider;
    private final JLabel timeLabel;
    private final Timer updateTimer;
    private boolean isPlaying = false;
    private final String mediaPath;
    private final Consumer<String> logCallback;
    private boolean isReleasing = false;
    
    /**
     * Perform cleanup of all VLC resources when the application is shutting down.
     * This should be called from the application's shutdown hook.
     */
    public static void cleanupAll() {
        logger.info("Performing VLC cleanup for all windows");
        
        // Make a copy of the list to avoid concurrent modification
        List<VideoPlayerWindow> windowsToCleanup = new ArrayList<>(activeWindows);
        for (VideoPlayerWindow window : windowsToCleanup) {
            try {
                window.closeGracefully();
            } catch (Exception e) {
                logger.error("Error closing player window: " + e.getMessage(), e);
            }
        }
        
        activeWindows.clear();
        
        // Release the shared factory if it exists
        if (sharedFactory != null) {
            try {
                logger.info("Releasing VLC media player factory");
                sharedFactory.release();
                sharedFactory = null;
            } catch (Exception e) {
                logger.error("Error releasing media player factory: " + e.getMessage(), e);
            }
        }
        
        // Force a GC run to clean up any remaining native resources
        try {
            System.gc();
            Thread.sleep(100);  // Give GC a moment to run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("VLC cleanup completed");
    }
    
    /**
     * Static factory method to create and show a new video player window.
     * 
     * @param mediaPath The path to the media (file or URL)
     * @param title Window title
     * @param logCallback Callback for logging messages
     * @return The created window instance
     */
    public static VideoPlayerWindow show(String mediaPath, String title, Consumer<String> logCallback) {
        // Check if VLC is installed and discoverable
        if (!new NativeDiscovery().discover()) {
            logCallback.accept("[PLAYER] VLC not found. Please install VLC media player.");
            throw new RuntimeException("VLC not found. Please install VLC media player.");
        }
        
        // Determine if this is a file or URL
        boolean isFile = true;
        try {
            File file = new File(mediaPath);
            isFile = file.exists() && file.isFile();
        } catch (Exception e) {
            isFile = false;
        }
        
        // Create and configure the window
        VideoPlayerWindow window = new VideoPlayerWindow(mediaPath, title, logCallback);
        window.setVisible(true);
        window.startPlayback();
        
        // Track this window for cleanup
        synchronized (activeWindows) {
            activeWindows.add(window);
        }
        
        return window;
    }
    
    /**
     * Create a new video player window
     * 
     * @param mediaPath The path to the media (file or URL)
     * @param title Window title
     * @param logCallback Callback for logging messages
     */
    private VideoPlayerWindow(String mediaPath, String title, Consumer<String> logCallback) {
        this.mediaPath = mediaPath;
        this.logCallback = logCallback;
        
        // Initialize shared factory if needed
        synchronized (VideoPlayerWindow.class) {
            if (sharedFactory == null) {
                sharedFactory = new MediaPlayerFactory();
                logger.info("Created shared VLC media player factory");
            }
        }
        
        // Setup window
        setTitle(title != null ? title : "Video Player");
        setSize(800, 600);
        setLocationRelativeTo(null); // Center on screen
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Create media player component
        mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        add(mediaPlayerComponent, BorderLayout.CENTER);
        
        // Create control panel
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Create buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        
        // Create playback control buttons
        rewindButton = new JButton("<<");
        rewindButton.setToolTipText("Rewind 10 seconds");
        rewindButton.addActionListener(e -> rewind());
        
        playPauseButton = new JButton("Pause");
        playPauseButton.addActionListener(e -> togglePause());
        
        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stop());
        
        forwardButton = new JButton(">>");
        forwardButton.setToolTipText("Forward 10 seconds");
        forwardButton.addActionListener(e -> forward());
        
        // Add buttons to panel
        buttonsPanel.add(rewindButton);
        buttonsPanel.add(playPauseButton);
        buttonsPanel.add(stopButton);
        buttonsPanel.add(forwardButton);
        
        // Create slider panel
        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        // Create position slider
        positionSlider = new JSlider(0, 100, 0);
        positionSlider.addChangeListener(e -> {
            if (positionSlider.getValueIsAdjusting()) {
                float position = positionSlider.getValue() / 100.0f;
                mediaPlayerComponent.mediaPlayer().controls().setPosition(position);
            }
        });
        
        // Create time label
        timeLabel = new JLabel("00:00:00 / 00:00:00", JLabel.CENTER);
        timeLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        
        sliderPanel.add(positionSlider, BorderLayout.CENTER);
        sliderPanel.add(timeLabel, BorderLayout.EAST);
        
        // Add components to control panel
        controlPanel.add(buttonsPanel, BorderLayout.CENTER);
        controlPanel.add(sliderPanel, BorderLayout.NORTH);
        
        add(controlPanel, BorderLayout.SOUTH);
        
        // Create update timer for position slider and time label
        updateTimer = new Timer(500, e -> updatePositionAndTime());
        
        // Add window listener to clean up resources on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeGracefully();
            }
        });
        
        log("Video player window created for: " + mediaPath);
    }
    
    /**
     * Call this method after making the window visible
     */
    public void startPlayback() {
        // Use a small delay to ensure the window is fully initialized
        Timer startTimer = new Timer(200, e -> {
            ((Timer)e.getSource()).stop();
            playMedia();
        });
        startTimer.setRepeats(false);
        startTimer.start();
    }
    
    /**
     * Start playing the media
     */
    private void playMedia() {
        try {
            log("Playing media: " + mediaPath);
            
            boolean started = mediaPlayerComponent.mediaPlayer().media().play(mediaPath);
            
            if (started) {
                log("Media playback started successfully");
                isPlaying = true;
                updateTimer.start();
                
                // Add a listener for playback events
                mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(
                    new MediaPlayerEventAdapter() {
                        @Override
                        public void finished(MediaPlayer mediaPlayer) {
                            log("Playback finished");
                            SwingUtilities.invokeLater(() -> {
                                playPauseButton.setText("Play");
                                isPlaying = false;
                                updateTimer.stop();
                            });
                        }
                        
                        @Override
                        public void error(MediaPlayer mediaPlayer) {
                            log("Playback error");
                            SwingUtilities.invokeLater(() -> {
                                playPauseButton.setText("Play");
                                isPlaying = false;
                                updateTimer.stop();
                                JOptionPane.showMessageDialog(VideoPlayerWindow.this,
                                    "Error playing video",
                                    "Playback Error",
                                    JOptionPane.ERROR_MESSAGE);
                            });
                        }
                        
                        @Override
                        public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                            // Update position slider and time label when time changes
                            SwingUtilities.invokeLater(() -> updatePositionAndTime());
                        }
                    }
                );
            } else {
                log("Failed to start media playback");
                JOptionPane.showMessageDialog(this,
                    "Failed to start media playback",
                    "Playback Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            log("Error playing media: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error playing media: " + e.getMessage(),
                "Playback Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Toggle between play and pause
     */
    private void togglePause() {
        if (mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
            mediaPlayerComponent.mediaPlayer().controls().pause();
            playPauseButton.setText("Play");
            isPlaying = false;
            log("Playback paused");
        } else {
            mediaPlayerComponent.mediaPlayer().controls().play();
            playPauseButton.setText("Pause");
            isPlaying = true;
            log("Playback resumed");
        }
    }
    
    /**
     * Stop playback
     */
    private void stop() {
        mediaPlayerComponent.mediaPlayer().controls().stop();
        playPauseButton.setText("Play");
        isPlaying = false;
        updateTimer.stop();
        positionSlider.setValue(0);
        timeLabel.setText("00:00:00 / 00:00:00");
        log("Playback stopped");
    }
    
    /**
     * Rewind 10 seconds
     */
    private void rewind() {
        long time = mediaPlayerComponent.mediaPlayer().status().time();
        // Rewind 10 seconds (10000 milliseconds)
        long newTime = Math.max(0, time - 10000);
        mediaPlayerComponent.mediaPlayer().controls().setTime(newTime);
        log("Rewind to " + formatTime(newTime));
    }
    
    /**
     * Forward 10 seconds
     */
    private void forward() {
        long time = mediaPlayerComponent.mediaPlayer().status().time();
        long length = mediaPlayerComponent.mediaPlayer().status().length();
        // Forward 10 seconds (10000 milliseconds)
        long newTime = Math.min(length, time + 10000);
        mediaPlayerComponent.mediaPlayer().controls().setTime(newTime);
        log("Forward to " + formatTime(newTime));
    }
    
    /**
     * Update the position slider and time label
     */
    private void updatePositionAndTime() {
        if (mediaPlayerComponent.mediaPlayer().status().isPlayable()) {
            // Update position slider
            int position = Math.round(mediaPlayerComponent.mediaPlayer().status().position() * 100);
            if (!positionSlider.getValueIsAdjusting()) {
                positionSlider.setValue(position);
            }
            
            // Update time label
            long time = mediaPlayerComponent.mediaPlayer().status().time();
            long length = mediaPlayerComponent.mediaPlayer().status().length();
            timeLabel.setText(formatTime(time) + " / " + formatTime(length));
        }
    }
    
    /**
     * Format time in milliseconds to HH:MM:SS
     */
    private String formatTime(long timeInMs) {
        long totalSeconds = timeInMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Safely close the player and release resources
     */
    public void closeGracefully() {
        if (isReleasing) {
            return; // Prevent double-release
        }
        
        isReleasing = true;
        log("Closing video player");
        
        // Stop playback first
        try {
            if (mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
                mediaPlayerComponent.mediaPlayer().controls().stop();
            }
        } catch (Exception e) {
            log("Error stopping media player: " + e.getMessage());
        }
        
        // Stop the timer
        if (updateTimer != null && updateTimer.isRunning()) {
            updateTimer.stop();
        }
        
        // Release in a new thread to avoid blocking the UI thread
        SwingUtilities.invokeLater(() -> {
            try {
                if (mediaPlayerComponent != null) {
                    mediaPlayerComponent.release();
                }
            } catch (Exception e) {
                log("Error releasing media player component: " + e.getMessage());
            }
            
            // Remove from active windows list
            synchronized (activeWindows) {
                activeWindows.remove(VideoPlayerWindow.this);
            }
            
            dispose();
        });
    }
    
    /**
     * Close the player and release resources
     */
    public void close() {
        closeGracefully();
    }
    
    /**
     * Check if the player is currently playing
     */
    public boolean isPlaying() {
        try {
            return isPlaying && mediaPlayerComponent.mediaPlayer().status().isPlaying();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Log a message
     */
    private void log(String message) {
        logger.info(message);
        if (logCallback != null) {
            logCallback.accept("[PLAYER] " + message);
        }
    }
    
    /**
     * Utility method to check if VLC is available
     */
    public static boolean isVlcAvailable() {
        return new NativeDiscovery().discover();
    }
} 