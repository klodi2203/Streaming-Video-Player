package com.videostreaming.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class VideoPlayerWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(VideoPlayerWindow.class);
    
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private final JButton playPauseButton;
    private final JButton stopButton;
    private final JButton rewindButton;
    private final JButton forwardButton;
    private final JSlider positionSlider;
    private final JLabel timeLabel;
    private final Timer updateTimer;
    private boolean isPlaying = false;
    private final File videoFile;
    
    public VideoPlayerWindow(File videoFile) {
        this.videoFile = videoFile;
        
        // Setup window
        setTitle("Video Player - " + videoFile.getName());
        setSize(800, 600);
        setLayout(new BorderLayout());
        
        // Create media player component
        mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        add(mediaPlayerComponent, BorderLayout.CENTER);
        
        // Create control panel
        JPanel controlPanel = new JPanel(new BorderLayout());
        
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
                close();
            }
        });
        
        // We'll start the video after the window is shown
    }
    
    // Call this method after making the window visible
    public void startPlayback() {
        // Use a small delay to ensure the window is fully initialized
        Timer startTimer = new Timer(200, e -> {
            ((Timer)e.getSource()).stop();
            playVideo();
        });
        startTimer.setRepeats(false);
        startTimer.start();
    }
    
    private void playVideo() {
        try {
            logger.info("Playing video: {}", videoFile.getAbsolutePath());
            
            boolean started = mediaPlayerComponent.mediaPlayer().media().play(videoFile.getAbsolutePath());
            
            if (started) {
                logger.info("Video playback started successfully");
                isPlaying = true;
                updateTimer.start();
                
                // Add a listener for playback events
                mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(
                    new MediaPlayerEventAdapter() {
                        @Override
                        public void finished(MediaPlayer mediaPlayer) {
                            logger.info("Playback finished");
                            SwingUtilities.invokeLater(() -> {
                                playPauseButton.setText("Play");
                                isPlaying = false;
                                updateTimer.stop();
                            });
                        }
                        
                        @Override
                        public void error(MediaPlayer mediaPlayer) {
                            logger.error("Playback error");
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
                logger.error("Failed to start video playback");
                JOptionPane.showMessageDialog(this,
                    "Failed to start video playback",
                    "Playback Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            logger.error("Error playing video: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(this,
                "Error playing video: " + e.getMessage(),
                "Playback Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void togglePause() {
        if (mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
            mediaPlayerComponent.mediaPlayer().controls().pause();
            playPauseButton.setText("Play");
            isPlaying = false;
        } else {
            mediaPlayerComponent.mediaPlayer().controls().play();
            playPauseButton.setText("Pause");
            isPlaying = true;
        }
    }
    
    private void stop() {
        mediaPlayerComponent.mediaPlayer().controls().stop();
        playPauseButton.setText("Play");
        isPlaying = false;
        updateTimer.stop();
        positionSlider.setValue(0);
        timeLabel.setText("00:00:00 / 00:00:00");
    }
    
    private void rewind() {
        long time = mediaPlayerComponent.mediaPlayer().status().time();
        // Rewind 10 seconds (10000 milliseconds)
        long newTime = Math.max(0, time - 10000);
        mediaPlayerComponent.mediaPlayer().controls().setTime(newTime);
    }
    
    private void forward() {
        long time = mediaPlayerComponent.mediaPlayer().status().time();
        long length = mediaPlayerComponent.mediaPlayer().status().length();
        // Forward 10 seconds (10000 milliseconds)
        long newTime = Math.min(length, time + 10000);
        mediaPlayerComponent.mediaPlayer().controls().setTime(newTime);
    }
    
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
    
    private String formatTime(long timeInMs) {
        long totalSeconds = timeInMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    public void close() {
        updateTimer.stop();
        mediaPlayerComponent.release();
        dispose();
    }
} 