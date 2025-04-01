package com.videostreaming.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Use the VideoFile from common package, not the server package
import com.videostreaming.common.VideoFile;

public class StreamingServer {
    private static final Logger logger = LoggerFactory.getLogger(StreamingServer.class);
    private final int port;
    private final VideoProcessor videoProcessor;
    private boolean running;
    private ServerGUI gui;

    public StreamingServer(int port, VideoProcessor videoProcessor) {
        this.port = port;
        this.videoProcessor = videoProcessor;
        this.running = false;
    }

    public void start() {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log(String.format("Server started on port %d", port));
            
            while (running) {
                log("Waiting for client connection...");
                Socket clientSocket = serverSocket.accept();
                log(String.format("Client connected from: %s", clientSocket.getInetAddress()));
                
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            // Create streams without try-with-resources to avoid auto-closing
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            
            // Process client requests in a loop
            boolean clientConnected = true;
            while (clientConnected) {
                try {
                    // Read client request
                    Object request = in.readObject();
                    
                    if (request instanceof String) {
                        String command = (String) request;
                        
                        if ("GET_VIDEOS".equals(command)) {
                            // Send available videos list
                            out.writeObject(videoProcessor.getAvailableVideos());
                            out.flush();
                            log("Sent video list to client");
                        } else if ("GET_FORMATS".equals(command)) {
                            // Get all unique formats from available videos
                            List<String> formats = videoProcessor.getAvailableVideos().stream()
                                .map(v -> v.getFormat())  // Use lambda instead of method reference
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList());
                            
                            // Make sure we have at least some formats
                            if (formats.isEmpty()) {
                                formats.add(".mp4");
                                formats.add(".mkv");
                                formats.add(".avi");
                            }
                            
                            // Send formats to client
                            out.writeObject(formats);
                            out.flush();
                            log("Sent available formats: " + formats);
                        }
                    } else if (request instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> requestMap = (Map<String, Object>) request;
                        
                        if ("FILTER_VIDEOS".equals(requestMap.get("action"))) {
                            String format = (String) requestMap.get("format");
                            double bandwidth = (double) requestMap.get("bandwidth");
                            
                            log("Client requested videos with format: " + format + 
                                ", bandwidth: " + bandwidth + " Mbps");
                            
                            // Filter videos by format and bandwidth
                            List<VideoFile> filteredVideos = videoProcessor.getAvailableVideos().stream()
                                .filter(v -> v.getFormat().equals(format))
                                .filter(v -> {
                                    // Get maximum supported resolution based on bandwidth
                                    String maxResolution = getMaxResolutionForBandwidth(bandwidth);
                                    int maxHeight = getHeightFromResolution(maxResolution);
                                    int videoHeight = getHeightFromResolution(v.getResolution());
                                    return videoHeight <= maxHeight;
                                })
                                .collect(Collectors.toList());
                            
                            // Send filtered list to client
                            out.writeObject(filteredVideos);
                            out.flush();
                            log("Sent " + filteredVideos.size() + " filtered videos to client");
                        }
                    }
                } catch (ClassNotFoundException e) {
                    log("Error processing client request: " + e.getMessage());
                } catch (IOException e) {
                    log("Client disconnected: " + e.getMessage());
                    clientConnected = false;
                }
            }
        } catch (IOException e) {
            log("Error setting up client streams: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                log("Client connection closed");
            } catch (IOException e) {
                log("Error closing client socket: " + e.getMessage());
            }
        }
    }

    // Helper methods for video filtering
    private String getMaxResolutionForBandwidth(double bandwidth) {
        // Based on the YouTube resolution/bitrate table
        if (bandwidth < 2.0) {
            return "240p";
        } else if (bandwidth < 5.0) {
            return "360p";
        } else if (bandwidth < 8.0) {
            return "480p";
        } else if (bandwidth < 12.0) {
            return "720p";
        } else {
            return "1080p";
        }
    }

    private int getHeightFromResolution(String resolution) {
        // Extract height value from resolution string (e.g., "720p" -> 720)
        return Integer.parseInt(resolution.substring(0, resolution.length() - 1));
    }

    public void stop() {
        running = false;
    }

    public void setGUI(ServerGUI gui) {
        this.gui = gui;
    }

    private void log(String message) {
        logger.info(message);
        if (gui != null) {
            gui.addLogMessage(message);
        }
    }
} 