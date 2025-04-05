package com.videostreaming.service;

import com.videostreaming.model.Video;
import com.videostreaming.util.VideoFormatUtil;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for converting videos to different formats and resolutions using FFMPEG
 */
public class VideoConversionService extends Service<Void> {
    
    private static final Logger LOGGER = Logger.getLogger(VideoConversionService.class.getName());
    
    private final List<Video> videos;
    private final String videoDirectory;
    private final Consumer<String> logConsumer;
    private final ConcurrentLinkedQueue<String> conversionQueue = new ConcurrentLinkedQueue<>();
    
    // Callback for when a new video has been created
    private Consumer<Video> onNewVideoCreated;
    
    /**
     * Create a video conversion service
     * @param videos List of videos to check for missing versions
     * @param videoDirectory Directory where videos are stored
     * @param logConsumer Consumer to log messages to the UI
     */
    public VideoConversionService(List<Video> videos, String videoDirectory, Consumer<String> logConsumer) {
        this.videos = videos;
        this.videoDirectory = videoDirectory;
        this.logConsumer = logConsumer;
    }
    
    /**
     * Set a callback to be called when a new video is created
     * @param onNewVideoCreated Callback function
     */
    public void setOnNewVideoCreated(Consumer<Video> onNewVideoCreated) {
        this.onNewVideoCreated = onNewVideoCreated;
    }
    
    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                log("Starting conversion process for " + videos.size() + " videos");
                
                // Check if FFMPEG is installed
                if (!isFFmpegInstalled()) {
                    log("ERROR: FFMPEG is not installed or not available in PATH");
                    LOGGER.severe("FFMPEG is not installed or not available in PATH");
                    return null;
                }
                
                // Process each video
                for (Video video : videos) {
                    processVideo(video);
                }
                
                log("Conversion process completed");
                return null;
            }
        };
    }
    
    /**
     * Process a video to generate all format/resolution combinations
     * @param video Source video to process
     */
    private void processVideo(Video video) {
        log("Processing video '" + video.getName() + "' to generate all format/resolution combinations");
        LOGGER.info("Processing video '" + video.getName() + "' to generate all format/resolution combinations");
        
        // Get base name and source file information
        String baseName = video.getName();
        String sourceResolution = video.getResolution();
        String sourceFormat = video.getFormat();
        String sourceFilePath = video.getFilePath();
        
        // Get all supported formats and resolutions
        List<String> formats = VideoFormatUtil.getSupportedFormats();
        List<String> resolutions = VideoFormatUtil.getResolutionsUpTo(sourceResolution);
        
        // Generate all combinations
        for (String format : formats) {
            for (String resolution : resolutions) {
                // Skip the source file (it already exists)
                if (format.equals(sourceFormat) && resolution.equals(sourceResolution)) {
                    continue;
                }
                
                // Generate target file path
                String targetFileName = VideoFormatUtil.generateFilename(baseName, format, resolution);
                String targetFilePath = videoDirectory + File.separator + targetFileName;
                
                // Check if target file already exists
                File targetFile = new File(targetFilePath);
                if (targetFile.exists()) {
                    log("Target file already exists: " + targetFileName + " - skipping conversion");
                    continue;
                }
                
                // Convert the video
                boolean success = convertVideo(sourceFilePath, targetFilePath, resolution, format);
                
                // If conversion was successful, create a new Video object and notify listeners
                if (success) {
                    Video newVideo = new Video(baseName, resolution, format, targetFilePath);
                    
                    // Notify listeners about the new video on the JavaFX Application Thread
                    Platform.runLater(() -> {
                        if (onNewVideoCreated != null) {
                            onNewVideoCreated.accept(newVideo);
                        }
                    });
                }
            }
        }
    }
    
    /**
     * Notify listeners that a new video has been created
     * @param newVideo The new video that was created
     */
    private void notifyNewVideoCreated(Video newVideo) {
        if (onNewVideoCreated != null) {
            Platform.runLater(() -> onNewVideoCreated.accept(newVideo));
        }
    }
    
    /**
     * Convert a video to a different format and resolution using FFMPEG
     * @param sourceFilePath Source video file path
     * @param targetFilePath Target video file path
     * @param targetResolution Target resolution
     * @param targetFormat Target format
     * @return true if conversion was successful, false otherwise
     */
    private boolean convertVideo(String sourceFilePath, String targetFilePath, String targetResolution, String targetFormat) {
        // Extract resolution values (e.g., "480p" -> 480)
        int resolutionValue = Integer.parseInt(targetResolution.replace("p", ""));
        
        // Calculate height based on standard 16:9 aspect ratio
        int width = -1; // Let FFMPEG determine width based on aspect ratio
        int height = resolutionValue;
        
        log("Starting conversion: " + new File(sourceFilePath).getName() + " to " + new File(targetFilePath).getName());
        LOGGER.info("Starting conversion: " + sourceFilePath + " to " + targetFilePath);
        
        try {
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-i");
            command.add(sourceFilePath);
            
            // Add video scaling parameters - ensure width is even for libx264
            command.add("-vf");
            command.add("scale=-2:" + height);
            
            // Add format-specific parameters
            switch (targetFormat) {
                case "mp4":
                    command.add("-c:v");
                    command.add("libx264");
                    command.add("-crf");
                    command.add("23");
                    command.add("-preset");
                    command.add("medium");
                    break;
                case "avi":
                    command.add("-c:v");
                    command.add("mpeg4");
                    command.add("-q:v");
                    command.add("6");
                    break;
                case "mkv":
                    command.add("-c:v");
                    command.add("libx264");
                    command.add("-crf");
                    command.add("23");
                    break;
                default:
                    log("Unknown format: " + targetFormat + " - using default encoding");
                    break;
            }
            
            // Add audio codec and output file
            command.add("-c:a");
            command.add("aac");
            command.add("-strict");
            command.add("-2");
            command.add("-y"); // Overwrite output file if it exists
            command.add(targetFilePath);
            
            // Create and start process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // Read and log process output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                LOGGER.fine(line);
            }
            
            int exitCode = process.waitFor();
            
            // Check process exit code
            if (exitCode == 0) {
                log("Conversion completed: " + new File(targetFilePath).getName());
                LOGGER.info("Conversion completed successfully: " + targetFilePath);
                return true;
            } else {
                log("ERROR: Conversion failed with exit code " + exitCode + ": " + new File(targetFilePath).getName());
                LOGGER.log(Level.SEVERE, "Conversion failed with exit code " + exitCode + ": " + targetFilePath + "\n" + output);
                return false;
            }
            
        } catch (IOException | InterruptedException e) {
            log("ERROR: Conversion failed: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Conversion failed", e);
            return false;
        }
    }
    
    /**
     * Check if FFMPEG is installed
     * @return true if FFMPEG is installed, false otherwise
     */
    private boolean isFFmpegInstalled() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ffmpeg", "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            int exitCode = process.waitFor();
            if (exitCode == 0 && line != null && line.startsWith("ffmpeg version")) {
                log(line);
                return true;
            }
            
            return false;
        } catch (IOException | InterruptedException e) {
            log("ERROR: Unable to check FFMPEG installation: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Unable to check FFMPEG installation", e);
            return false;
        }
    }
    
    /**
     * Compare two resolutions
     * @param res1 First resolution (e.g., "480p")
     * @param res2 Second resolution (e.g., "720p")
     * @return -1 if res1 < res2, 0 if res1 == res2, 1 if res1 > res2
     */
    private int compareResolutions(String res1, String res2) {
        int val1 = Integer.parseInt(res1.replace("p", ""));
        int val2 = Integer.parseInt(res2.replace("p", ""));
        return Integer.compare(val1, val2);
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