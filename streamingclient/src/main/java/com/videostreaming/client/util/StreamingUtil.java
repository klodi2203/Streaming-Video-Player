package com.videostreaming.client.util;

import com.videostreaming.client.dialog.ProtocolSelectionDialog.StreamingProtocol;
import com.videostreaming.client.model.Video;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for handling video streaming using FFMPEG.
 */
public class StreamingUtil {
    
    private static final Logger LOGGER = Logger.getLogger(StreamingUtil.class.getName());
    static {
        LoggingUtil.configureLogger(LOGGER);
    }
    
    // FFMPEG executable path - could be configured based on OS
    private static final String FFMPEG_PATH = "ffmpeg";
    
    // Default server settings
    private static final String SERVER_HOST = "localhost";
    private static final int TCP_PORT = 8081;
    private static final int UDP_PORT = 8082;
    private static final int RTP_PORT = 8083;
    
    // Random for port generation
    private static final Random RANDOM = new Random();
    
    // Temp directory for downloaded streams
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    
    private static Process ffmpegProcess;
    private static Process playerProcess;
    private static final AtomicBoolean isStreaming = new AtomicBoolean(false);
    
    /**
     * Start streaming a video using the selected protocol
     * 
     * @param video The video to stream
     * @param protocol The protocol to use for streaming
     * @param outputCallback Callback for output messages
     * @param completionCallback Callback for when streaming ends
     * @throws IOException If an error occurs starting the stream
     */
    public static void startStreaming(
            Video video, 
            StreamingProtocol protocol, 
            Consumer<String> outputCallback,
            Runnable completionCallback) throws IOException {
        
        if (isStreaming.get()) {
            LoggingUtil.logWithUi(LOGGER, Level.INFO, "STREAM_CONTROL", 
                    "Stopping existing stream before starting a new one", outputCallback);
            stopStreaming();
        }
        
        isStreaming.set(true);
        LoggingUtil.logStreaming(LOGGER, protocol.toString(), "STARTING", 
                "Initiating " + protocol + " streaming for " + video.getName(), outputCallback);
        
        // For debug/demo purposes, check if we can directly play the file
        // Note: This is just for testing and should be removed in a real app
        String urlString = video.getUrl();
        if (urlString != null && !urlString.isEmpty()) {
            File directFile = new File(urlString);
            if (directFile.exists() && directFile.isFile() && directFile.canRead()) {
                LoggingUtil.logStreaming(LOGGER, protocol.toString(), "DIRECT_PLAY", 
                        "Direct file access detected for " + urlString, outputCallback);
                playVideo(urlString, outputCallback);
                return;
            }
        }
        
        // Create a unique temporary file for this stream
        String tempFilename = video.getName().replaceAll("[^a-zA-Z0-9]", "_") + 
                "_" + System.currentTimeMillis() + "." + video.getFormat();
        String tempFilePath = Paths.get(TEMP_DIR, tempFilename).toString();
        
        LoggingUtil.logWithUi(LOGGER, Level.INFO, "FFMPEG", 
                "Setting up FFMPEG to receive stream", outputCallback);
        
        // Build the FFMPEG command based on the selected protocol
        List<String> command = new ArrayList<>();
        command.add(FFMPEG_PATH);
        
        // Get a random local port to avoid binding issues
        int localPort = 10000 + RANDOM.nextInt(50000);
        LoggingUtil.info(LOGGER, "NETWORK", "Using local port " + localPort + " for receiving stream");
        
        // Input options based on protocol
        switch (protocol) {
            case TCP:
                command.add("-i");
                command.add("tcp://" + SERVER_HOST + ":" + TCP_PORT);
                LoggingUtil.logNetworkActivity(LOGGER, "CONNECTING", "tcp://" + SERVER_HOST + ":" + TCP_PORT,
                        "TCP Stream", "Using TCP protocol", outputCallback);
                break;
            case UDP:
                command.add("-i");
                command.add("udp://" + SERVER_HOST + ":" + UDP_PORT + "?localport=" + localPort);
                LoggingUtil.logNetworkActivity(LOGGER, "CONNECTING", "udp://" + SERVER_HOST + ":" + UDP_PORT,
                        "UDP Stream", "Using UDP protocol with localport=" + localPort, outputCallback);
                break;
            case RTP_UDP:
                command.add("-protocol_whitelist");
                command.add("file,rtp,udp");
                command.add("-i");
                command.add("rtp://" + SERVER_HOST + ":" + RTP_PORT + "?localport=" + localPort);
                LoggingUtil.logNetworkActivity(LOGGER, "CONNECTING", "rtp://" + SERVER_HOST + ":" + RTP_PORT,
                        "RTP Stream", "Using RTP/UDP protocol with localport=" + localPort, outputCallback);
                break;
        }
        
        // Add timeout options to prevent FFMPEG from waiting indefinitely
        command.add("-timeout");
        command.add("5000000"); // 5 seconds timeout
        
        // Output options - save to a temporary file and play
        command.add("-c");
        command.add("copy"); // Use same codec
        command.add("-y"); // Overwrite output file if it exists
        command.add(tempFilePath);
        
        LoggingUtil.logWithUi(LOGGER, Level.INFO, "FFMPEG", 
                "Starting FFMPEG with command: " + String.join(" ", command), outputCallback);
        
        // Start FFMPEG process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // Redirect error stream to output stream
        
        ffmpegProcess = processBuilder.start();
        LoggingUtil.info(LOGGER, "FFMPEG", "FFMPEG process started with PID: " + ffmpegProcess.pid());
        
        // Read the output in a separate thread
        new Thread(() -> {
            boolean streamReceived = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String finalLine = line;
                    LoggingUtil.info(LOGGER, "FFMPEG", finalLine);
                    outputCallback.accept("[FFMPEG] " + finalLine);
                    
                    // Check if FFMPEG has started receiving data
                    if ((line.contains("Input #0") || line.contains("Stream mapping")) && !streamReceived) {
                        streamReceived = true;
                        LoggingUtil.logStreaming(LOGGER, protocol.toString(), "RECEIVING", 
                                "Stream data is being received from server", outputCallback);
                        
                        // Give it a moment to buffer before playing
                        new Thread(() -> {
                            try {
                                // Wait up to 10 seconds for the file to be created and have some data
                                File file = new File(tempFilePath);
                                int maxWaits = 20; // 20 * 500ms = 10 seconds max
                                int waited = 0;
                                
                                LoggingUtil.logWithUi(LOGGER, Level.INFO, "BUFFER", 
                                        "Waiting for stream data to buffer...", outputCallback);
                                
                                while ((!file.exists() || file.length() < 10000) && waited < maxWaits) {
                                    Thread.sleep(500);
                                    waited++;
                                    
                                    if (waited % 4 == 0) { // Log every 2 seconds
                                        LoggingUtil.info(LOGGER, "BUFFER", 
                                                "Buffering... " + (file.exists() ? 
                                                file.length() + " bytes received" : "File not created yet"));
                                    }
                                }
                                
                                if (file.exists() && file.length() > 0 && isStreaming.get()) {
                                    LoggingUtil.logStreaming(LOGGER, protocol.toString(), "BUFFERED", 
                                            "Stream buffered (" + file.length() + " bytes), starting player", outputCallback);
                                    playVideo(tempFilePath, outputCallback);
                                } else {
                                    LoggingUtil.logWithUi(LOGGER, Level.WARNING, "BUFFER", 
                                            "Insufficient data received for playback", outputCallback);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LoggingUtil.error(LOGGER, "THREAD", "Buffer thread interrupted: " + e.getMessage());
                            } catch (IOException e) {
                                LoggingUtil.logWithUi(LOGGER, Level.SEVERE, "PLAYER", 
                                        "Error starting video player: " + e.getMessage(), outputCallback);
                            }
                        }).start();
                    }
                }
                
                // FFMPEG process has exited
                int exitCode = ffmpegProcess.waitFor();
                LoggingUtil.logWithUi(LOGGER, Level.INFO, "FFMPEG", 
                        "FFMPEG process completed with exit code: " + exitCode, outputCallback);
                
                // Check if we have a valid output file to play
                File outputFile = new File(tempFilePath);
                if (!streamReceived && outputFile.exists() && outputFile.length() > 0) {
                    LoggingUtil.logStreaming(LOGGER, protocol.toString(), "COMPLETED", 
                            "Stream completed, file saved (" + formatSize(outputFile.length()) + ")", outputCallback);
                    try {
                        playVideo(tempFilePath, outputCallback);
                    } catch (IOException e) {
                        LoggingUtil.logWithUi(LOGGER, Level.SEVERE, "PLAYER", 
                                "Error starting video player: " + e.getMessage(), outputCallback);
                    }
                }
                
                // Clean up
                isStreaming.set(false);
                ffmpegProcess = null;
                
                // Call completion callback
                LoggingUtil.logStreaming(LOGGER, protocol.toString(), "FINISHED", 
                        "Streaming session ended", outputCallback);
                completionCallback.run();
                
            } catch (IOException e) {
                LoggingUtil.logWithUi(LOGGER, Level.SEVERE, "FFMPEG", 
                        "Error reading FFMPEG output: " + e.getMessage(), outputCallback);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LoggingUtil.logWithUi(LOGGER, Level.SEVERE, "FFMPEG", 
                        "FFMPEG process interrupted", outputCallback);
            }
        }).start();
        
        LoggingUtil.logWithUi(LOGGER, Level.INFO, "STREAM_CONTROL", 
                "Stream initialization complete, waiting for data...", outputCallback);
    }
    
    /**
     * Play the video using the system's default player
     * 
     * @param videoPath Path to the video file
     * @param outputCallback Callback for output messages
     * @throws IOException If an error occurs starting the player
     */
    private static void playVideo(String videoPath, Consumer<String> outputCallback) throws IOException {
        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            String errorMsg = "Video file does not exist: " + videoPath;
            LoggingUtil.error(LOGGER, "PLAYER", errorMsg);
            throw new IOException(errorMsg);
        }
        
        // Make sure the file has some content before trying to play it
        if (videoFile.length() < 1000) {
            LoggingUtil.logWithUi(LOGGER, Level.WARNING, "PLAYER", 
                    "Video file is too small to play: " + videoFile.length() + " bytes", outputCallback);
            return;
        }
        
        LoggingUtil.logWithUi(LOGGER, Level.INFO, "PLAYER", 
                "Starting system video player for: " + videoPath, outputCallback);
        
        List<String> command = new ArrayList<>();
        
        // Use the appropriate command based on OS
        String os = System.getProperty("os.name").toLowerCase();
        LoggingUtil.info(LOGGER, "SYSTEM", "Detected OS: " + os);
        
        if (os.contains("win")) {
            // Windows
            command.add("cmd");
            command.add("/c");
            command.add("start");
            command.add("\"Video Player\"");
            command.add(videoPath);
        } else if (os.contains("mac")) {
            // macOS
            command.add("open");
            command.add(videoPath);
        } else {
            // Linux and others - use xdg-open
            command.add("xdg-open");
            command.add(videoPath);
        }
        
        LoggingUtil.logWithUi(LOGGER, Level.INFO, "PLAYER", 
                "Player command: " + String.join(" ", command), outputCallback);
        
        // Start the player process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        playerProcess = processBuilder.start();
        LoggingUtil.info(LOGGER, "PLAYER", "Player process started with PID: " + playerProcess.pid());
        
        // Log when the player process completes
        new Thread(() -> {
            try {
                int exitCode = playerProcess.waitFor();
                LoggingUtil.logWithUi(LOGGER, Level.INFO, "PLAYER", 
                        "Video player exited with code: " + exitCode, outputCallback);
                playerProcess = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LoggingUtil.error(LOGGER, "THREAD", "Player process interrupted: " + e.getMessage());
                outputCallback.accept("[PLAYER] Process interrupted");
            }
        }).start();
    }
    
    /**
     * Stop the current streaming session
     */
    public static void stopStreaming() {
        LoggingUtil.info(LOGGER, "STREAM_CONTROL", "Stopping streaming session");
        
        if (ffmpegProcess != null) {
            LoggingUtil.info(LOGGER, "FFMPEG", "Stopping FFMPEG process");
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
        
        if (playerProcess != null) {
            LoggingUtil.info(LOGGER, "PLAYER", "Stopping video player process");
            playerProcess.destroy();
            playerProcess = null;
        }
        
        isStreaming.set(false);
        LoggingUtil.info(LOGGER, "STREAM_CONTROL", "Streaming stopped");
    }
    
    /**
     * Check if FFMPEG is available in the system
     * 
     * @return true if FFMPEG is available, false otherwise
     */
    public static boolean isFFmpegAvailable() {
        LoggingUtil.info(LOGGER, "SYSTEM", "Checking FFMPEG availability");
        try {
            Process process = new ProcessBuilder(FFMPEG_PATH, "-version").start();
            
            // Read the version information
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String versionLine = reader.readLine();
                LoggingUtil.info(LOGGER, "FFMPEG", "Found FFMPEG: " + versionLine);
            }
            
            int exitCode = process.waitFor();
            boolean available = exitCode == 0;
            LoggingUtil.info(LOGGER, "SYSTEM", "FFMPEG availability check result: " + 
                    (available ? "AVAILABLE" : "NOT AVAILABLE"));
            return available;
        } catch (IOException | InterruptedException e) {
            LoggingUtil.error(LOGGER, "SYSTEM", "FFMPEG check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete all temporary streaming files
     */
    public static void cleanupTempFiles() {
        LoggingUtil.info(LOGGER, "CLEANUP", "Cleaning up temporary streaming files in: " + TEMP_DIR);
        try {
            Files.walk(Paths.get(TEMP_DIR))
                .filter(path -> {
                    String filename = path.getFileName().toString();
                    return Files.isRegularFile(path) && 
                           (filename.endsWith(".mp4") || 
                            filename.endsWith(".avi") || 
                            filename.endsWith(".mkv"));
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        LoggingUtil.info(LOGGER, "CLEANUP", "Deleted temporary file: " + path);
                    } catch (IOException e) {
                        LoggingUtil.warning(LOGGER, "CLEANUP", "Failed to delete temporary file: " + path);
                    }
                });
            LoggingUtil.info(LOGGER, "CLEANUP", "Temporary file cleanup completed");
        } catch (IOException e) {
            LoggingUtil.error(LOGGER, "CLEANUP", "Error cleaning up temporary files: " + e.getMessage());
        }
    }
    
    /**
     * Format a size in bytes to a human-readable string
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
} 