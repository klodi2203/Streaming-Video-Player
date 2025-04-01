package com.videostreaming.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.videostreaming.common.VideoFile;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    
    private Socket clientSocket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private VideoProcessor videoProcessor;

    public ClientHandler(Socket socket, VideoProcessor videoProcessor) throws IOException {
        this.clientSocket = socket;
        this.videoProcessor = videoProcessor;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        
        log("New client connected: " + socket.getInetAddress());
    }

    private void log(String message) {
        logger.info("[Client {}] {}", clientSocket.getInetAddress(), message);
    }

    private void handleGetVideos() throws IOException {
        // Get the complete list of videos
        List<VideoFile> videos = videoProcessor.getAvailableVideos();
        
        // Sort videos: first by format, then by resolution (in descending order)
        videos.sort((v1, v2) -> {
            // First compare formats
            int formatCompare = v1.getFormat().compareTo(v2.getFormat());
            if (formatCompare != 0) {
                return formatCompare;
            }
            
            // If formats are the same, compare resolutions (higher resolution first)
            int res1 = getHeightFromResolution(v1.getResolution());
            int res2 = getHeightFromResolution(v2.getResolution());
            return Integer.compare(res2, res1); // Descending order
        });
        
        // Send the sorted list to the client
        out.writeObject(videos);
        log("Sent complete video list (" + videos.size() + " videos)");
    }

    private void handleGetFormats() throws IOException {
        // Get all unique formats from available videos
        List<String> formats = videoProcessor.getAvailableVideos().stream()
            .map(VideoFile::getFormat)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        // Make sure we have at least some formats
        if (formats.isEmpty()) {
            // Add some default formats if none were found
            formats.add(".mp4");
            formats.add(".mkv");
            formats.add(".avi");
        }
        
        // Send formats to client
        out.writeObject(formats);
        log("Sent available formats: " + formats);
    }

    private void handleFilterVideos(Map<String, Object> request) throws IOException {
        String format = (String) request.get("format");
        double bandwidth = (double) request.get("bandwidth");
        String recommendedResolution = (String) request.get("resolution");
        
        // Log the request
        log("Client requested videos with format: " + format + ", bandwidth: " + bandwidth + " Mbps");
        
        // Filter videos by format and resolution based on bandwidth
        List<VideoFile> filteredVideos = filterVideosByFormatAndBandwidth(format, bandwidth);
        
        // Sort filtered videos by resolution (higher resolution first)
        filteredVideos.sort((v1, v2) -> {
            int res1 = getHeightFromResolution(v1.getResolution());
            int res2 = getHeightFromResolution(v2.getResolution());
            return Integer.compare(res2, res1); // Descending order
        });
        
        // Send filtered list to client
        out.writeObject(filteredVideos);
        
        log("Sent " + filteredVideos.size() + " filtered videos to client");
    }

    private List<VideoFile> filterVideosByFormatAndBandwidth(String format, double bandwidth) {
        // Get maximum supported resolution based on bandwidth
        String maxResolution = getMaxResolutionForBandwidth(bandwidth);
        int maxHeight = getHeightFromResolution(maxResolution);
        
        log("Maximum supported resolution for " + bandwidth + " Mbps: " + maxResolution);
        
        // Filter videos by format and resolution
        return videoProcessor.getAvailableVideos().stream()
            .filter(video -> video.getFormat().equals(format))
            .filter(video -> {
                int videoHeight = getHeightFromResolution(video.getResolution());
                return videoHeight <= maxHeight;
            })
            .collect(Collectors.toList());
    }

    private String getMaxResolutionForBandwidth(double bandwidth) {
        // Convert Mbps to Kbps (1 Mbps = 1000 Kbps)
        double kbps = bandwidth * 1000;
        
        // Based on the provided YouTube resolution/bitrate table
        if (kbps < 700) {
            return "240p"; // Maximum bitrate for 240p is 700 Kbps
        } else if (kbps < 2000) {
            return "360p"; // Maximum bitrate for 360p is 1000 Kbps, but we allow up to 2000 Kbps
        } else if (kbps < 4000) {
            return "480p"; // Maximum bitrate for 480p is 2000 Kbps, but we allow up to 4000 Kbps
        } else if (kbps < 6000) {
            return "720p"; // Maximum bitrate for 720p is 4000 Kbps, but we allow up to 6000 Kbps
        } else {
            return "1080p"; // Maximum bitrate for 1080p is 6000 Kbps
        }
    }

    private int getHeightFromResolution(String resolution) {
        // Extract height value from resolution string (e.g., "720p" -> 720)
        return Integer.parseInt(resolution.substring(0, resolution.length() - 1));
    }

    @Override
    public void run() {
        try {
            // Main communication loop
            while (true) {
                Object message = in.readObject();
                
                if (message instanceof String) {
                    String command = (String) message;
                    if (command.equals("GET_VIDEOS")) {
                        handleGetVideos();
                    } else if (command.equals("GET_FORMATS")) {
                        handleGetFormats();
                    }
                } else if (message instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> request = (Map<String, Object>) message;
                    String action = (String) request.get("action");
                    
                    if ("FILTER_VIDEOS".equals(action)) {
                        handleFilterVideos(request);
                    }
                }
            }
        } catch (IOException e) {
            log("Connection error: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            log("Error processing client message: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                log("Client disconnected");
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }
} 