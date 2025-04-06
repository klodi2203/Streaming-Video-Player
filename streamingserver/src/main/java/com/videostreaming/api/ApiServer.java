package com.videostreaming.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.videostreaming.model.Video;
import com.videostreaming.service.VideoScanService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Simple HTTP server to handle API requests from the client application.
 */
public class ApiServer {
    
    private static final Logger LOGGER = Logger.getLogger(ApiServer.class.getName());
    
    private final int port;
    private final VideoScanService videoScanService;
    private HttpServer server;
    private final StreamingServer streamingServer;
    
    /**
     * Create a new API server
     * @param port The port to listen on
     * @param videoScanService The video scan service to use for retrieving videos
     */
    public ApiServer(int port, VideoScanService videoScanService) {
        this.port = port;
        this.videoScanService = videoScanService;
        this.streamingServer = new StreamingServer();
    }
    
    /**
     * Start the API server
     */
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/videos", new VideosHandler());
            server.createContext("/api/request", new StreamRequestHandler());
            server.createContext("/api/status", new StatusHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            LOGGER.info("API server started on port " + port);
            
            // Start the streaming server
            streamingServer.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start API server", e);
        }
    }
    
    /**
     * Stop the API server
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("API server stopped");
        }
        
        // Stop the streaming server
        if (streamingServer != null && streamingServer.isRunning()) {
            streamingServer.stop();
        }
    }
    
    /**
     * Get the streaming server instance
     * @return The streaming server
     */
    public StreamingServer getStreamingServer() {
        return streamingServer;
    }
    
    /**
     * Handler for /api/videos endpoint
     */
    private class VideosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Enable CORS
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                
                if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                    // Handle preflight request
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                
                if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    // Read request body
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String requestBody = br.lines().collect(Collectors.joining());
                    LOGGER.info("Received request: " + requestBody);
                    
                    // Parse request (simplified parsing for demonstration)
                    String format = extractValue(requestBody, "format");
                    double speed = extractNumberValue(requestBody, "speed");
                    
                    LOGGER.info("Client requested format: " + format + " with speed: " + speed + " Mbps");
                    
                    // Get videos matching the requested format
                    List<Video> allVideos = videoScanService.getVideoList();
                    List<Video> matchingVideos = new ArrayList<>();
                    
                    for (Video video : allVideos) {
                        if (video.getFormat().equalsIgnoreCase(format)) {
                            // For low speeds, filter out high resolution videos
                            if (speed < 5.0 && "1080p".equals(video.getResolution())) {
                                continue;
                            }
                            matchingVideos.add(video);
                        }
                    }
                    
                    // Build JSON response
                    StringBuilder jsonResponse = new StringBuilder();
                    jsonResponse.append("{\"videos\":[");
                    
                    for (int i = 0; i < matchingVideos.size(); i++) {
                        Video video = matchingVideos.get(i);
                        jsonResponse.append("{");
                        jsonResponse.append("\"name\":\"").append(video.getName()).append("\",");
                        jsonResponse.append("\"resolution\":\"").append(video.getResolution()).append("\",");
                        jsonResponse.append("\"format\":\"").append(video.getFormat()).append("\",");
                        jsonResponse.append("\"url\":\"").append(video.getFilePath()).append("\"");
                        jsonResponse.append("}");
                        
                        if (i < matchingVideos.size() - 1) {
                            jsonResponse.append(",");
                        }
                    }
                    
                    jsonResponse.append("]}");
                    
                    // Send response
                    String response = jsonResponse.toString();
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                    
                    LOGGER.info("Sent response with " + matchingVideos.size() + " videos");
                } else {
                    // Method not allowed
                    exchange.sendResponseHeaders(405, 0);
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling request", e);
                String errorResponse = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        
        /**
         * Extract a value from a simple JSON string
         * Note: This is a simplified parser for demonstration purposes
         */
        private String extractValue(String json, String key) {
            int startIndex = json.indexOf("\"" + key + "\":\"");
            if (startIndex == -1) return "";
            
            startIndex += key.length() + 4; // Skip past the key and ":"
            int endIndex = json.indexOf("\"", startIndex);
            
            if (endIndex == -1) return "";
            
            return json.substring(startIndex, endIndex);
        }
        
        /**
         * Extract a number value from a simple JSON string
         * Note: This is a simplified parser for demonstration purposes
         */
        private double extractNumberValue(String json, String key) {
            int startIndex = json.indexOf("\"" + key + "\":");
            if (startIndex == -1) return 0.0;
            
            startIndex += key.length() + 3; // Skip past the key and ":"
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = json.indexOf("}", startIndex);
            }
            
            if (endIndex == -1) return 0.0;
            
            String valueStr = json.substring(startIndex, endIndex).trim();
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
    }
    
    /**
     * Handler for /api/request endpoint - handles streaming requests
     */
    private class StreamRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Enable CORS
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                
                if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                    // Handle preflight request
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                
                if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    // Read request body
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String requestBody = br.lines().collect(Collectors.joining());
                    LOGGER.info("Received streaming request: " + requestBody);
                    
                    // Parse request
                    String videoName = extractValue(requestBody, "videoName");
                    String resolution = extractValue(requestBody, "resolution");
                    String format = extractValue(requestBody, "format");
                    String protocol = extractValue(requestBody, "protocol");
                    
                    LOGGER.info("Client requested to stream video: " + videoName +
                            " (" + resolution + ", " + format + ") using protocol: " + protocol);
                    
                    // Find the requested video
                    Video requestedVideo = findRequestedVideo(videoName, resolution, format);
                    
                    if (requestedVideo != null) {
                        // Get the client IP address
                        InetAddress clientAddress = exchange.getRemoteAddress().getAddress();
                        
                        // Start streaming the video
                        streamingServer.streamVideo(requestedVideo, protocol, clientAddress);
                        
                        // Build success JSON response
                        String response = String.format(
                                "{\"status\":\"streaming\",\"message\":\"Started streaming %s using %s\",\"streamUrl\":\"%s://%s:%d\"}",
                                videoName, protocol, 
                                protocol.toLowerCase().replace("/", ""), 
                                "localhost",
                                getPortForProtocol(protocol)
                        );
                        
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                        
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes(StandardCharsets.UTF_8));
                        }
                        
                        LOGGER.info("Acknowledged streaming request for " + requestedVideo.getFilePath());
                    } else {
                        // Video not found
                        String errorResponse = "{\"error\":\"Video not found: " + videoName + "\"}";
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(404, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                        
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                        }
                        
                        LOGGER.warning("Video not found: " + videoName);
                    }
                } else {
                    // Method not allowed
                    exchange.sendResponseHeaders(405, 0);
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling stream request", e);
                String errorResponse = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        
        /**
         * Find a video by name, resolution, and format
         * @param name The video name
         * @param resolution The video resolution
         * @param format The video format
         * @return The video, or null if not found
         */
        private Video findRequestedVideo(String name, String resolution, String format) {
            List<Video> allVideos = videoScanService.getVideoList();
            
            for (Video video : allVideos) {
                if (video.getName().equals(name) && 
                    video.getResolution().equals(resolution) && 
                    video.getFormat().equals(format)) {
                    return video;
                }
            }
            
            return null;
        }
        
        /**
         * Get the port number for a streaming protocol
         * @param protocol The protocol name
         * @return The port number
         */
        private int getPortForProtocol(String protocol) {
            if (protocol.equalsIgnoreCase("TCP")) {
                return 8081;
            } else if (protocol.equalsIgnoreCase("UDP")) {
                return 8082;
            } else if (protocol.equalsIgnoreCase("RTP/UDP")) {
                return 8083;
            } else {
                return 8081; // Default to TCP
            }
        }
        
        /**
         * Extract a value from a simple JSON string
         * Note: This is a simplified parser for demonstration purposes
         */
        private String extractValue(String json, String key) {
            int startIndex = json.indexOf("\"" + key + "\":\"");
            if (startIndex == -1) return "";
            
            startIndex += key.length() + 4; // Skip past the key and ":"
            int endIndex = json.indexOf("\"", startIndex);
            
            if (endIndex == -1) return "";
            
            return json.substring(startIndex, endIndex);
        }
    }
    
    /**
     * Handler for /api/status endpoint - reports server status
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Enable CORS
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                
                if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                    // Handle preflight request
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                
                if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    // Get server status
                    String serverStatus = streamingServer.getServerStatus();
                    
                    // Create JSON response
                    String jsonResponse = String.format(
                        "{\"status\":\"%s\",\"activeClients\":%d,\"activeStreams\":%d,\"totalClients\":%d}",
                        streamingServer.isRunning() ? "running" : "stopped",
                        streamingServer.getActiveClientsCount(),
                        streamingServer.getActiveStreamsCount(),
                        streamingServer.getTotalClientCount()
                    );
                    
                    LOGGER.info("Status request from " + exchange.getRemoteAddress().getAddress() + 
                            " - Active clients: " + streamingServer.getActiveClientsCount());
                    
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
                    
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    // Method not allowed
                    exchange.sendResponseHeaders(405, 0);
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling status request", e);
                String errorResponse = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }
} 