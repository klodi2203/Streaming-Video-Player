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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for converting videos to different formats and resolutions using FFMPEG
 */
public class VideoConversionService extends Service<Void> {
    
    private static final Logger LOGGER = Logger.getLogger(VideoConversionService.class.getName());
    private static final int MAX_CONCURRENT_CONVERSIONS = 2;
    
    private final List<Video> videos;
    private final String videoDirectory;
    private final Consumer<String> logConsumer;
    private final ConcurrentLinkedQueue<ConversionJob> conversionQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService conversionExecutor;
    private final List<Future<?>> conversionFutures = new ArrayList<>();
    private final AtomicInteger totalJobs = new AtomicInteger(0);
    private final AtomicInteger completedJobs = new AtomicInteger(0);
    
    // Callback for when a new video has been created
    private Consumer<Video> onNewVideoCreated;
    private boolean ffmpegAvailable = false;
    private Task<Void> currentTask;
    
    // Nested class to hold conversion job info
    private static class ConversionJob {
        final String sourcePath;
        final String targetPath;
        final String resolution;
        final String format;
        final String baseName;
        
        ConversionJob(String sourcePath, String targetPath, String resolution, String format, String baseName) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.resolution = resolution;
            this.format = format;
            this.baseName = baseName;
        }
    }
    
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
        this.conversionExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_CONVERSIONS);
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
        return new ConversionTask();
    }
    
    /**
     * Custom Task implementation with public methods to update progress
     */
    private class ConversionTask extends Task<Void> {
        
        /**
         * Public method to update progress
         */
        public void updateConversionProgress(int completed, int total) {
            updateProgress(completed, total);
        }
        
        @Override
        protected Void call() throws Exception {
            // Store a reference to the current task
            currentTask = this;
            
            log("Starting conversion process for " + videos.size() + " videos");
            
            // Reset counters
            totalJobs.set(0);
            completedJobs.set(0);
            conversionQueue.clear();
            conversionFutures.clear();
            
            // Check if FFMPEG is installed
            ffmpegAvailable = isFFmpegInstalled();
            if (!ffmpegAvailable) {
                log("ERROR: FFMPEG is not installed or not available in PATH");
                LOGGER.severe("FFMPEG is not installed or not available in PATH");
                return null;
            }
            
            // Process each video to generate conversion jobs
            for (Video video : videos) {
                if (isCancelled()) {
                    break;
                }
                processVideo(video);
            }
            
            // Process all jobs from the queue
            int jobsCount = totalJobs.get();
            log("Queue prepared with " + jobsCount + " conversion jobs");
            
            if (jobsCount == 0) {
                log("No conversions needed");
                updateProgress(1, 1);
                return null;
            }
            
            // Start worker threads to process the queue
            for (int i = 0; i < MAX_CONCURRENT_CONVERSIONS; i++) {
                if (isCancelled()) {
                    break;
                }
                
                Future<?> future = conversionExecutor.submit(() -> {
                    while (!currentTask.isCancelled() && !conversionQueue.isEmpty()) {
                        ConversionJob job = conversionQueue.poll();
                        if (job != null) {
                            processConversionJob(job);
                        }
                    }
                });
                
                conversionFutures.add(future);
            }
            
            // Wait for all conversions to complete
            for (Future<?> future : conversionFutures) {
                try {
                    if (!isCancelled()) {
                        future.get();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error waiting for conversion to complete", e);
                }
            }
            
            if (isCancelled()) {
                log("Conversion process was canceled");
                for (Future<?> future : conversionFutures) {
                    future.cancel(true);
                }
            } else {
                log("Conversion process completed");
            }
            
            return null;
        }
        
        @Override
        protected void cancelled() {
            super.cancelled();
            // Cancel all pending futures
            for (Future<?> future : conversionFutures) {
                future.cancel(true);
            }
            // Shutdown the executor
            conversionExecutor.shutdownNow();
        }
        
        @Override
        protected void succeeded() {
            super.succeeded();
            // Shutdown the executor
            conversionExecutor.shutdown();
        }
        
        @Override
        protected void failed() {
            super.failed();
            // Shutdown the executor
            conversionExecutor.shutdownNow();
        }
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
                
                // Add to conversion queue
                conversionQueue.add(new ConversionJob(sourceFilePath, targetFilePath, resolution, format, baseName));
                totalJobs.incrementAndGet();
            }
        }
    }
    
    /**
     * Process a single conversion job
     * @param job The conversion job to process
     */
    private void processConversionJob(ConversionJob job) {
        if (currentTask.isCancelled()) {
            return;
        }
        
        boolean success = convertVideo(job.sourcePath, job.targetPath, job.resolution, job.format);
        
        // If conversion was successful, create a new Video object and notify listeners
        if (success) {
            Video newVideo = new Video(job.baseName, job.resolution, job.format, job.targetPath);
            
            // Notify listeners about the new video on the JavaFX Application Thread
            Platform.runLater(() -> {
                if (onNewVideoCreated != null) {
                    onNewVideoCreated.accept(newVideo);
                }
            });
        }
        
        // Update progress
        int completed = completedJobs.incrementAndGet();
        int total = totalJobs.get();
        
        Platform.runLater(() -> {
            ((ConversionTask)currentTask).updateConversionProgress(completed, total);
            double progressPercentage = (double) completed / total * 100;
            log(String.format("Progress: %.1f%% (%d/%d)", progressPercentage, completed, total));
        });
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
        // Check if we've been cancelled
        if (currentTask.isCancelled()) {
            return false;
        }
        
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
            
            while (!currentTask.isCancelled() && (line = reader.readLine()) != null) {
                output.append(line).append("\n");
                LOGGER.fine(line);
            }
            
            // If cancelled, kill the process
            if (currentTask.isCancelled()) {
                process.destroy();
                log("Conversion was cancelled: " + new File(targetFilePath).getName());
                return false;
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
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log("Conversion was interrupted: " + new File(targetFilePath).getName());
            } else {
                log("ERROR: Conversion failed: " + e.getMessage());
                LOGGER.log(Level.SEVERE, "Conversion failed", e);
            }
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