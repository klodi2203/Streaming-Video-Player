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
            stopStreaming();
        }
        
        isStreaming.set(true);
        
        // For debug/demo purposes, check if we can directly play the file
        // Note: This is just for testing and should be removed in a real app
        String urlString = video.getUrl();
        if (urlString != null && !urlString.isEmpty()) {
            File directFile = new File(urlString);
            if (directFile.exists() && directFile.isFile() && directFile.canRead()) {
                outputCallback.accept("DEBUG MODE: Direct file access detected. Playing file directly: " + urlString);
                playVideo(urlString, outputCallback);
                return;
            }
        }
        
        // Create a unique temporary file for this stream
        String tempFilename = video.getName().replaceAll("[^a-zA-Z0-9]", "_") + 
                "_" + System.currentTimeMillis() + "." + video.getFormat();
        String tempFilePath = Paths.get(TEMP_DIR, tempFilename).toString();
        
        outputCallback.accept("Setting up FFMPEG to receive stream...");
        
        // Build the FFMPEG command based on the selected protocol
        List<String> command = new ArrayList<>();
        command.add(FFMPEG_PATH);
        
        // Get a random local port to avoid binding issues
        int localPort = 10000 + RANDOM.nextInt(50000);
        
        // Input options based on protocol
        switch (protocol) {
            case TCP:
                command.add("-i");
                command.add("tcp://" + SERVER_HOST + ":" + TCP_PORT);
                break;
            case UDP:
                command.add("-i");
                command.add("udp://" + SERVER_HOST + ":" + UDP_PORT + "?localport=" + localPort);
                break;
            case RTP_UDP:
                command.add("-protocol_whitelist");
                command.add("file,rtp,udp");
                command.add("-i");
                command.add("rtp://" + SERVER_HOST + ":" + RTP_PORT + "?localport=" + localPort);
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
        
        outputCallback.accept("Starting FFMPEG with command: " + String.join(" ", command));
        
        // Start FFMPEG process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // Redirect error stream to output stream
        
        ffmpegProcess = processBuilder.start();
        
        // Read the output in a separate thread
        new Thread(() -> {
            boolean streamReceived = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String finalLine = line;
                    LOGGER.info("FFMPEG: " + finalLine);
                    outputCallback.accept("FFMPEG: " + finalLine);
                    
                    // Check if FFMPEG has started receiving data
                    if ((line.contains("Input #0") || line.contains("Stream mapping")) && !streamReceived) {
                        streamReceived = true;
                        outputCallback.accept("FFMPEG is receiving the stream...");
                        
                        // Give it a moment to buffer before playing
                        new Thread(() -> {
                            try {
                                // Wait up to 10 seconds for the file to be created and have some data
                                File file = new File(tempFilePath);
                                int maxWaits = 20; // 20 * 500ms = 10 seconds max
                                int waited = 0;
                                
                                while ((!file.exists() || file.length() < 10000) && waited < maxWaits) {
                                    Thread.sleep(500);
                                    waited++;
                                }
                                
                                if (file.exists() && file.length() > 0 && isStreaming.get()) {
                                    outputCallback.accept("Starting video player...");
                                    playVideo(tempFilePath, outputCallback);
                                } else {
                                    outputCallback.accept("Could not start player - insufficient data received.");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } catch (IOException e) {
                                LOGGER.log(Level.SEVERE, "Error starting video player", e);
                                outputCallback.accept("Error starting video player: " + e.getMessage());
                            }
                        }).start();
                    }
                }
                
                // FFMPEG process has exited
                int exitCode = ffmpegProcess.waitFor();
                outputCallback.accept("FFMPEG process completed with exit code: " + exitCode);
                
                // Check if we have a valid output file to play
                File outputFile = new File(tempFilePath);
                if (!streamReceived && outputFile.exists() && outputFile.length() > 0) {
                    outputCallback.accept("Video received, starting player...");
                    try {
                        playVideo(tempFilePath, outputCallback);
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Error starting video player", e);
                        outputCallback.accept("Error starting video player: " + e.getMessage());
                    }
                }
                
                // Clean up
                isStreaming.set(false);
                ffmpegProcess = null;
                
                // Call completion callback
                completionCallback.run();
                
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error reading FFMPEG output", e);
                outputCallback.accept("Error reading FFMPEG output: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.SEVERE, "FFMPEG process interrupted", e);
                outputCallback.accept("FFMPEG process interrupted");
            }
        }).start();
        
        outputCallback.accept("Waiting for stream to start...");
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
            throw new IOException("Video file does not exist: " + videoPath);
        }
        
        // Make sure the file has some content before trying to play it
        if (videoFile.length() < 1000) {
            outputCallback.accept("Video file is too small to play: " + videoFile.length() + " bytes");
            return;
        }
        
        outputCallback.accept("Starting video player for: " + videoPath);
        
        List<String> command = new ArrayList<>();
        
        // Use the appropriate command based on OS
        String os = System.getProperty("os.name").toLowerCase();
        
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
        
        outputCallback.accept("Starting system player with command: " + String.join(" ", command));
        
        // Start the player process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        playerProcess = processBuilder.start();
        
        // Log when the player process completes
        new Thread(() -> {
            try {
                int exitCode = playerProcess.waitFor();
                outputCallback.accept("Video player exited with code: " + exitCode);
                playerProcess = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.SEVERE, "Player process interrupted", e);
                outputCallback.accept("Player process interrupted");
            }
        }).start();
    }
    
    /**
     * Stop the current streaming session
     */
    public static void stopStreaming() {
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
        
        if (playerProcess != null) {
            playerProcess.destroy();
            playerProcess = null;
        }
        
        isStreaming.set(false);
        LOGGER.info("Streaming stopped");
    }
    
    /**
     * Check if FFMPEG is available in the system
     * 
     * @return true if FFMPEG is available, false otherwise
     */
    public static boolean isFFmpegAvailable() {
        try {
            Process process = new ProcessBuilder(FFMPEG_PATH, "-version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "FFMPEG check failed", e);
            return false;
        }
    }
    
    /**
     * Delete all temporary streaming files
     */
    public static void cleanupTempFiles() {
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
                        LOGGER.info("Deleted temporary file: " + path);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to delete temporary file: " + path, e);
                    }
                });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error cleaning up temporary files", e);
        }
    }
} 