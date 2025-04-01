package com.videostreaming.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.FFmpegExecutor;
import com.videostreaming.common.VideoFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VideoProcessor.class);
    private static final String VIDEO_DIR = "videos";
    private static final String[] SUPPORTED_FORMATS = {".avi", ".mp4", ".mkv"};
    private static final String[] SUPPORTED_RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};
    
    private FFmpeg ffmpeg;
    private FFprobe ffprobe;
    private FFmpegExecutor executor;
    private List<VideoFile> availableVideos;

    public VideoProcessor() {
        this(true);
    }

    public VideoProcessor(boolean scanImmediately) {
        try {
            ffmpeg = new FFmpeg();
            ffprobe = new FFprobe();
            executor = new FFmpegExecutor(ffmpeg, ffprobe);
            availableVideos = new ArrayList<>();
            
            if (scanImmediately) {
                initializeVideoDirectory();
                scanVideos();
            }
        } catch (IOException e) {
            logger.error("Failed to initialize FFmpeg: {}", e.getMessage());
        }
    }

    public void initializeVideoDirectory() {
        File videoDir = new File(VIDEO_DIR);
        if (!videoDir.exists()) {
            videoDir.mkdir();
            logger.info("Created videos directory");
        }
    }

    public void scanVideos() {
        logger.info("Scanning videos directory...");
        
        // Clear the existing list before scanning
        availableVideos.clear();
        logger.info("Cleared existing video list");
        
        try {
            Files.walk(Paths.get(VIDEO_DIR))
                .filter(Files::isRegularFile)
                .filter(this::isVideoFile)
                .forEach(this::processVideo);
            
            // Verify all videos exist
            verifyVideoFiles();
            
            logger.info("Scan complete. Found {} videos", availableVideos.size());
        } catch (IOException e) {
            logger.error("Error scanning videos: {}", e.getMessage());
        }
    }

    private boolean isVideoFile(Path path) {
        String fileName = path.toString().toLowerCase();
        for (String format : SUPPORTED_FORMATS) {
            if (fileName.endsWith(format)) {
                return true;
            }
        }
        return false;
    }

    private void processVideo(Path videoPath) {
        try {
            String fileName = videoPath.getFileName().toString();
            logger.info("Processing video file: {}", fileName);
            
            // Extract base name and current format/resolution
            String baseName;
            String currentResolution;
            String currentFormat;
            
            // Parse the filename to extract information
            if (fileName.contains("-")) {
                baseName = fileName.substring(0, fileName.lastIndexOf('-'));
                currentResolution = extractResolution(fileName);
                currentFormat = extractFormat(fileName);
            } else {
                // If filename doesn't follow our convention, use defaults
                baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                currentResolution = "480p"; // default
                currentFormat = extractFormat(fileName);
            }
            
            logger.info("Extracted info - baseName: {}, resolution: {}, format: {}", 
                       baseName, currentResolution, currentFormat);
            
            // Add the video to available videos
            VideoFile video = new VideoFile(
                baseName, 
                currentFormat, 
                currentResolution, 
                videoPath.toString()
            );
            availableVideos.add(video);
            logger.info("Added video: {}", video);
            
        } catch (Exception e) {
            logger.error("Error processing video {}: {}", videoPath, e.getMessage(), e);
        }
    }

    private void createVideoVersion(Path originalVideo, String baseName, String resolution, String format) {
        String outputPath = String.format("%s/%s-%s%s", VIDEO_DIR, baseName, resolution, format);
        logger.info("Attempting to create: {}", outputPath);
        
        if (new File(outputPath).exists()) {
            logger.info("File already exists, adding to list: {}", outputPath);
            availableVideos.add(new VideoFile(baseName, format, resolution, outputPath));
            return;
        }

        try {
            int height = getHeightFromResolution(resolution);
            // Calculate width and ensure it's even
            int width = (int) Math.round(height * 16.0 / 9.0);
            if (width % 2 != 0) {
                width++; // Make it even
            }
            
            logger.info("Using dimensions: {}x{}", width, height);
            
            // Build a more robust conversion command
            FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(originalVideo.toString())
                .overrideOutputFiles(true)
                .addOutput(outputPath)
                .setVideoResolution(width, height)
                .setVideoCodec("libx264")     // Use H.264 codec for better compatibility
                .setVideoFrameRate(30)        // Set frame rate
                .setVideoBitRate(calculateBitrate(height)) // Calculate appropriate bitrate
                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL)
                .done();

            logger.info("Starting FFmpeg conversion for: {}", outputPath);
            FFmpegJob job = executor.createJob(builder);
            job.run();

            if (job.getState() == FFmpegJob.State.FINISHED) {
                availableVideos.add(new VideoFile(baseName, format, resolution, outputPath));
                logger.info("Successfully created: {}", outputPath);
            } else {
                logger.error("Failed to create: {} - Job state: {}", outputPath, job.getState());
            }
        } catch (Exception e) {
            logger.error("Error creating video version {}: {}", outputPath, e.getMessage(), e);
        }
    }

    // Helper method to calculate appropriate bitrate based on resolution
    private int calculateBitrate(int height) {
        // These are reasonable bitrates for different resolutions
        if (height <= 240) return 400_000;      // 400 Kbps for 240p
        else if (height <= 360) return 700_000; // 700 Kbps for 360p
        else if (height <= 480) return 1_500_000; // 1.5 Mbps for 480p
        else return 2_500_000;                  // 2.5 Mbps for 720p
    }

    public void streamVideo(String videoName, Socket clientSocket) {
        VideoFile video = findVideo(videoName);
        if (video == null) {
            logger.error("Video not found: {}", videoName);
            return;
        }

        logger.info("Starting to stream video: {} to client at {}", videoName, clientSocket.getInetAddress());
        
        Process process = null;
        
        try {
            // Get the path to the FFmpeg executable
            String ffmpegPath = ffmpeg.getPath();
            logger.info("Using FFmpeg at: {}", ffmpegPath);
            
            // Create a process to pipe the FFmpeg output to the socket
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", video.getPath(),
                "-f", "mpegts", 
                "-codec:v", "libx264",
                "-codec:a", "aac",
                "-"
            );
            
            pb.redirectErrorStream(true);
            process = pb.start();
            
            // Create a final reference to the process for use in the lambda
            final Process finalProcess = process;
            
            // Create a thread to read from FFmpeg and write to socket
            Thread streamingThread = new Thread(() -> {
                try (InputStream in = finalProcess.getInputStream();
                     OutputStream out = clientSocket.getOutputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    
                    logger.info("Starting to pipe video data to client");
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        out.flush();
                    }
                    
                    logger.info("Finished streaming video to client");
                } catch (IOException e) {
                    logger.error("Error streaming to client: {}", e.getMessage());
                } finally {
                    try {
                        // Only close the socket after streaming is complete
                        clientSocket.close();
                        logger.info("Closed client socket after streaming");
                    } catch (IOException e) {
                        logger.error("Error closing client socket: {}", e.getMessage());
                    }
                }
            });
            
            streamingThread.start();
            // Wait for streaming to complete
            streamingThread.join();
            
        } catch (Exception e) {
            logger.error("Error setting up video stream: {}", e.getMessage(), e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private VideoFile findVideo(String videoName) {
        return availableVideos.stream()
            .filter(v -> v.toString().equals(videoName))
            .findFirst()
            .orElse(null);
    }

    private String extractResolution(String fileName) {
        for (String resolution : SUPPORTED_RESOLUTIONS) {
            if (fileName.contains("-" + resolution)) {
                return resolution;
            }
        }
        return "480p"; // default resolution
    }

    private String extractFormat(String fileName) {
        fileName = fileName.toLowerCase();
        for (String format : SUPPORTED_FORMATS) {
            if (fileName.endsWith(format)) {
                return format;
            }
        }
        return ".mp4"; // default format
    }

    private int getHeightFromResolution(String resolution) {
        return Integer.parseInt(resolution.substring(0, resolution.length() - 1));
    }

    public List<VideoFile> getAvailableVideos() {
        return new ArrayList<>(availableVideos);
    }

    /**
     * Verifies that all video files in the list actually exist on disk
     * and removes any that don't exist.
     */
    public void verifyVideoFiles() {
        logger.info("Verifying {} video files in the list", availableVideos.size());
        List<VideoFile> validVideos = new ArrayList<>();
        
        for (VideoFile video : availableVideos) {
            File file = new File(video.getPath());
            if (file.exists() && file.isFile()) {
                validVideos.add(video);
            } else {
                logger.warn("Video file not found on disk, removing from list: {}", video.getPath());
            }
        }
        
        int removed = availableVideos.size() - validVideos.size();
        if (removed > 0) {
            logger.info("Removed {} non-existent video files from the list", removed);
        }
        
        availableVideos = validVideos;
    }
} 