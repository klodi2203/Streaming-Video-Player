package com.videostreaming.service;

import com.videostreaming.model.Video;
import com.videostreaming.util.VideoFormatUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to scan a directory for video files and parse their names
 */
public class VideoScanService extends Service<List<Video>> {
    
    private static final Logger LOGGER = Logger.getLogger(VideoScanService.class.getName());
    
    private final String videoDirectory;
    private final Consumer<String> logConsumer;
    private final ObservableList<Video> videoList = FXCollections.observableArrayList();
    private final Map<String, Video> videoMap = new HashMap<>();
    
    // Pattern to match filenames like videoName-<resolution>.<format>
    private static final Pattern VIDEO_FILENAME_PATTERN = 
            Pattern.compile("(.+)-(\\d+p)\\.(\\w+)$");
    
    public VideoScanService(String videoDirectory, Consumer<String> logConsumer) {
        this.videoDirectory = videoDirectory;
        this.logConsumer = logConsumer;
    }
    
    public ObservableList<Video> getVideoList() {
        return videoList;
    }
    
    /**
     * Add a new video to the list
     * @param video Video to add
     */
    public void addVideo(Video video) {
        if (video == null) {
            LOGGER.warning("Attempted to add null video");
            return;
        }
        
        LOGGER.info("Adding video: " + video);
        
        // Ensure UI updates happen on the JavaFX Application Thread
        Platform.runLater(() -> {
            String key = video.getName() + "-" + video.getResolution() + "." + video.getFormat();
            
            // Check if this exact video already exists
            if (videoMap.containsKey(key)) {
                log("Video already exists: " + video.getName() + " (" + video.getResolution() + ", " + video.getFormat() + ")");
                return;
            }
            
            // Add to our collections
            videoList.add(video);
            videoMap.put(key, video);
            
            log("Added new video: " + video.getName() + " (" + video.getResolution() + ", " + video.getFormat() + ")");
            
            // Refresh to sort the list
            refreshVideoList();
        });
    }
    
    /**
     * Force a refresh of the video list in the UI
     */
    public void refreshVideoList() {
        Platform.runLater(() -> {
            // Sort the list by name, then by resolution (highest first)
            videoList.sort((v1, v2) -> {
                int nameCompare = v1.getName().compareTo(v2.getName());
                if (nameCompare != 0) {
                    return nameCompare;
                }
                
                // For same name, sort by resolution (highest first)
                return -VideoFormatUtil.compareResolutions(v1.getResolution(), v2.getResolution());
            });
            
            LOGGER.info("Video list refreshed with " + videoList.size() + " videos");
        });
    }
    
    @Override
    protected Task<List<Video>> createTask() {
        return new ScanTask();
    }
    
    /**
     * Custom Task implementation with better progress tracking
     */
    private class ScanTask extends Task<List<Video>> {
        @Override
        protected List<Video> call() {
            log("Starting video directory scan: " + videoDirectory);
            
            // Clear existing videos
            Platform.runLater(() -> {
                videoList.clear();
                videoMap.clear();
            });
            
            updateProgress(0, 100);
            updateMessage("Checking directory");
            
            File directory = new File(videoDirectory);
            if (!directory.exists()) {
                log("Directory not found: " + videoDirectory);
                directory.mkdirs();
                log("Created directory: " + videoDirectory);
                updateProgress(100, 100);
                updateMessage("Scan complete - created empty directory");
                return new ArrayList<>();
            }
            
            if (!directory.isDirectory()) {
                log("Not a directory: " + videoDirectory);
                updateProgress(100, 100);
                updateMessage("Scan failed - not a directory");
                return new ArrayList<>();
            }
            
            updateProgress(10, 100);
            updateMessage("Reading directory contents");
            
            File[] files = directory.listFiles();
            if (files == null) {
                log("Unable to list files in directory: " + videoDirectory);
                updateProgress(100, 100);
                updateMessage("Scan failed - unable to list files");
                return new ArrayList<>();
            }
            
            log("Found " + files.length + " files in directory");
            
            List<Video> allVideos = new ArrayList<>();
            int totalFiles = files.length;
            int processedCount = 0;
            
            updateMessage("Processing " + totalFiles + " files");
            
            // Process each file
            for (File file : files) {
                if (isCancelled()) {
                    log("Scan cancelled");
                    return allVideos;
                }
                
                processedCount++;
                double progress = 10 + ((double) processedCount / totalFiles * 90); // 10-100% range
                updateProgress(progress, 100);
                updateMessage("Processing file " + processedCount + " of " + totalFiles);
                
                if (!file.isFile()) {
                    continue;
                }
                
                String filename = file.getName();
                Matcher matcher = VIDEO_FILENAME_PATTERN.matcher(filename);
                
                if (!matcher.matches()) {
                    log("File doesn't match expected pattern: " + filename);
                    continue;
                }
                
                String baseName = matcher.group(1);
                String resolution = matcher.group(2);
                String format = matcher.group(3);
                
                // Check if format and resolution are supported
                if (!VideoFormatUtil.isFormatSupported(format) || 
                    !VideoFormatUtil.isResolutionSupported(resolution)) {
                    log("Unsupported format or resolution: " + filename);
                    continue;
                }
                
                // Create a new video object
                Video video = new Video(baseName, resolution, format, file.getAbsolutePath());
                
                // Add to our collections
                allVideos.add(video);
                
                // Generate a unique key for this video
                final String key = baseName + "-" + resolution + "." + format;
                
                // Update UI on the JavaFX thread
                Platform.runLater(() -> {
                    videoList.add(video);
                    videoMap.put(key, video);
                });
                
                log("Found video: " + filename);
            }
            
            updateProgress(100, 100);
            updateMessage("Scan complete - found " + allVideos.size() + " videos");
            log("Video scan complete. Found " + allVideos.size() + " videos");
            return allVideos;
        }
    }
    
    @Override
    protected void succeeded() {
        super.succeeded();
        refreshVideoList();
    }
    
    @Override
    protected void cancelled() {
        super.cancelled();
        log("Video scanning was cancelled");
    }
    
    @Override
    protected void failed() {
        super.failed();
        Throwable ex = getException();
        if (ex != null) {
            log("ERROR: Video scanning failed: " + ex.getMessage());
            LOGGER.log(Level.SEVERE, "Video scanning failed", ex);
        } else {
            log("ERROR: Video scanning failed for unknown reason");
        }
    }
    
    /**
     * Log a message on the JavaFX Application Thread
     */
    private void log(String message) {
        LOGGER.info(message);
        if (logConsumer != null) {
            Platform.runLater(() -> logConsumer.accept(message));
        }
    }
} 