package com.videostreaming.client.service;

import com.videostreaming.client.dialog.ProtocolSelectionDialog.StreamingProtocol;
import com.videostreaming.client.model.Video;
import com.videostreaming.client.util.LoggingUtil;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for network operations, including sending data to the server
 * and receiving the list of available videos
 */
public class NetworkService {
    
    private static final Logger LOGGER = Logger.getLogger(NetworkService.class.getName());
    static {
        LoggingUtil.configureLogger(LOGGER);
    }
    
    // Server settings
    private static final String SERVER_BASE_URL = "http://localhost:8080";
    private static final String API_LIST_VIDEOS = "/api/videos";
    private static final String API_REQUEST_VIDEO = "/api/request";
    
    /**
     * Creates a service that sends the download speed and format to the server
     * and receives a list of compatible videos
     *
     * @param speed The measured download speed in Mbps
     * @param format The selected video format (mp4, avi, mkv)
     * @return A JavaFX service that returns a list of compatible videos
     */
    public static Service<List<Video>> createVideoRequestService(double speed, String format) {
        return new Service<>() {
            @Override
            protected Task<List<Video>> createTask() {
                return new Task<>() {
                    @Override
                    protected List<Video> call() throws Exception {
                        updateMessage("Connecting to server...");
                        LoggingUtil.logWithUi(LOGGER, Level.INFO, "VIDEO_REQUEST", 
                                "Initiating video request with speed=" + speed + " Mbps, format=" + format, 
                                this::updateMessage);
                        
                        try {
                            // Connect to the server API endpoint
                            String requestUrl = SERVER_BASE_URL + API_LIST_VIDEOS;
                            LoggingUtil.logNetworkActivity(LOGGER, "CONNECTING", requestUrl, 
                                    "API Request", null, this::updateMessage);
                            
                            // Create the request payload
                            String payload = String.format("{\"speed\":%.2f,\"format\":\"%s\"}", speed, format);
                            LoggingUtil.logNetworkActivity(LOGGER, "SENDING", requestUrl, 
                                    "API Request", "Payload: " + payload, this::updateMessage);
                            
                            // Create the HTTP connection
                            URL url = new URL(requestUrl);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Content-Type", "application/json");
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(5000);
                            connection.setReadTimeout(5000);
                            
                            updateProgress(10, 100);
                            
                            // Send request data
                            try (OutputStream os = connection.getOutputStream()) {
                                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                                os.write(input, 0, input.length);
                            }
                            
                            updateProgress(30, 100);
                            LoggingUtil.logNetworkActivity(LOGGER, "SENT", requestUrl, 
                                    "API Request", "Waiting for response...", this::updateMessage);
                            
                            // Check response status
                            int responseCode = connection.getResponseCode();
                            if (responseCode != 200) {
                                LoggingUtil.logNetworkActivity(LOGGER, "ERROR", requestUrl, 
                                        "API Response", "Server returned error code: " + responseCode, this::updateMessage);
                                throw new RuntimeException("Server returned error code: " + responseCode);
                            }
                            
                            updateProgress(60, 100);
                            LoggingUtil.logNetworkActivity(LOGGER, "RECEIVED", requestUrl, 
                                    "API Response", "Status code: 200 OK", this::updateMessage);
                            
                            // Read response
                            StringBuilder response = new StringBuilder();
                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = br.readLine()) != null) {
                                    response.append(line);
                                }
                            }
                            
                            String jsonResponse = response.toString();
                            LoggingUtil.logNetworkActivity(LOGGER, "PROCESSING", requestUrl, 
                                    "API Response", "Parsing JSON data", this::updateMessage);
                            
                            // Parse the response to get the list of videos
                            List<Video> videos = parseVideoList(jsonResponse);
                            
                            updateProgress(100, 100);
                            LoggingUtil.logWithUi(LOGGER, Level.INFO, "VIDEO_REQUEST", 
                                    "Received " + videos.size() + " compatible videos from server", 
                                    this::updateMessage);
                            
                            return videos;
                            
                        } catch (Exception e) {
                            LoggingUtil.error(LOGGER, "VIDEO_REQUEST", 
                                    "Error requesting videos: " + e.getMessage());
                            updateMessage("Error: " + e.getMessage());
                            
                            // If server communication fails, simulate a response for demonstration
                            LoggingUtil.warning(LOGGER, "VIDEO_REQUEST", 
                                    "Using simulated video response as fallback");
                            updateMessage("Using simulated data (server unavailable)");
                            
                            List<Video> simulatedVideos = simulateVideoResponse(speed, format);
                            return simulatedVideos;
                        }
                    }
                };
            }
        };
    }
    
    /**
     * Creates a service that sends a streaming request to the server
     * 
     * @param video The selected video to stream
     * @param protocol The selected streaming protocol
     * @return A JavaFX service that handles the streaming request
     */
    public static Service<Boolean> createStreamingRequestService(Video video, StreamingProtocol protocol) {
        return new Service<>() {
            @Override
            protected Task<Boolean> createTask() {
                return new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        LoggingUtil.logWithUi(LOGGER, Level.INFO, "STREAM_REQUEST", 
                                "Initiating streaming request for " + video.getName() + 
                                " using " + protocol + " protocol", this::updateMessage);
                        
                        try {
                            // Connect to the server API endpoint
                            String requestUrl = SERVER_BASE_URL + API_REQUEST_VIDEO;
                            LoggingUtil.logNetworkActivity(LOGGER, "CONNECTING", requestUrl, 
                                    "Stream Request", null, this::updateMessage);
                            
                            // Create the request payload
                            String payload = String.format(
                                "{\"videoName\":\"%s\",\"resolution\":\"%s\",\"format\":\"%s\",\"protocol\":\"%s\"}",
                                video.getName(), video.getResolution(), video.getFormat(), protocol.toString()
                            );
                            LoggingUtil.logNetworkActivity(LOGGER, "SENDING", requestUrl, 
                                    "Stream Request", "Payload: " + payload, this::updateMessage);
                            
                            // Create the HTTP connection
                            URL url = new URL(requestUrl);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Content-Type", "application/json");
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(5000);
                            connection.setReadTimeout(5000);
                            
                            updateProgress(30, 100);
                            
                            // Send request data
                            try (OutputStream os = connection.getOutputStream()) {
                                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                                os.write(input, 0, input.length);
                            }
                            
                            updateProgress(60, 100);
                            LoggingUtil.logNetworkActivity(LOGGER, "SENT", requestUrl, 
                                    "Stream Request", "Waiting for response...", this::updateMessage);
                            
                            // Check response status
                            int responseCode = connection.getResponseCode();
                            if (responseCode != 200) {
                                LoggingUtil.logNetworkActivity(LOGGER, "ERROR", requestUrl, 
                                        "Stream Response", "Server returned error code: " + responseCode, this::updateMessage);
                                throw new RuntimeException("Server returned error code: " + responseCode);
                            }
                            
                            updateProgress(80, 100);
                            LoggingUtil.logNetworkActivity(LOGGER, "RECEIVED", requestUrl, 
                                    "Stream Response", "Status code: 200 OK", this::updateMessage);
                            
                            // Read response
                            StringBuilder response = new StringBuilder();
                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = br.readLine()) != null) {
                                    response.append(line);
                                }
                            }
                            
                            String result = response.toString();
                            LoggingUtil.logNetworkActivity(LOGGER, "PROCESSING", requestUrl, 
                                    "Stream Response", "Response: " + result, this::updateMessage);
                            
                            updateProgress(100, 100);
                            LoggingUtil.logStreaming(LOGGER, protocol.toString(), "INITIALIZED", 
                                    "Server accepted streaming request", this::updateMessage);
                            
                            // In a real application, this would establish the stream connection
                            // For now, we just return success if the server accepted the request
                            return true;
                            
                        } catch (Exception e) {
                            LoggingUtil.error(LOGGER, "STREAM_REQUEST", 
                                    "Error sending streaming request: " + e.getMessage());
                            updateMessage("Error: " + e.getMessage());
                            
                            // If server communication fails, simulate success for demonstration
                            LoggingUtil.warning(LOGGER, "STREAM_REQUEST", 
                                    "Simulating streaming response as fallback");
                            updateProgress(100, 100);
                            LoggingUtil.logStreaming(LOGGER, protocol.toString(), "SIMULATED", 
                                    "Server unavailable, using direct file access", this::updateMessage);
                            
                            return true;
                        }
                    }
                };
            }
        };
    }
    
    /**
     * Simulate a video response for demonstration purposes when the server is unavailable
     */
    private static List<Video> simulateVideoResponse(double speed, String format) {
        LoggingUtil.info(LOGGER, "SIMULATION", "Generating simulated video list for " + 
                format + " with speed " + speed + " Mbps");
        
        List<Video> videos = new ArrayList<>();
        String[] names = {"Nature Documentary", "Action Movie", "Comedy Show"};
        String[] resolutions = {"240p", "360p", "480p", "720p", "1080p"};
        
        // Filter resolutions based on speed
        int maxResIndex = 4; // Default to all resolutions
        if (speed < 2.0) {
            maxResIndex = 1; // Only 240p and 360p
        } else if (speed < 5.0) {
            maxResIndex = 2; // Up to 480p
        } else if (speed < 10.0) {
            maxResIndex = 3; // Up to 720p
        }
        
        // Generate videos
        for (String name : names) {
            for (int i = 0; i <= maxResIndex; i++) {
                Video video = new Video(name, resolutions[i], format, "file:///simulated/video/" + name + "." + format);
                videos.add(video);
            }
        }
        
        LoggingUtil.info(LOGGER, "SIMULATION", "Generated " + videos.size() + " simulated videos");
        return videos;
    }
    
    /**
     * Parse a JSON response to get a list of videos
     */
    private static List<Video> parseVideoList(String json) {
        List<Video> videoList = new ArrayList<>();
        
        try {
            // Extract the videos array from the JSON
            int videosStart = json.indexOf("\"videos\":[") + 9;
            int videosEnd = json.lastIndexOf("]");
            
            if (videosStart > 9 && videosEnd > videosStart) {
                String videosJson = json.substring(videosStart, videosEnd + 1);
                
                // Remove the brackets
                String videosContent = videosJson.substring(1, videosJson.length() - 1).trim();
                
                // If there are videos
                if (!videosContent.isEmpty()) {
                    // Split by },{
                    String[] videoJsonArray = videosContent.split("\\},\\{");
                    
                    for (int i = 0; i < videoJsonArray.length; i++) {
                        String videoJson = videoJsonArray[i];
                        
                        // Add brackets if they were removed by the split
                        if (!videoJson.startsWith("{")) {
                            videoJson = "{" + videoJson;
                        }
                        if (!videoJson.endsWith("}")) {
                            videoJson = videoJson + "}";
                        }
                        
                        // Extract video properties
                        String name = extractValue(videoJson, "name");
                        String resolution = extractValue(videoJson, "resolution");
                        String format = extractValue(videoJson, "format");
                        String url = extractValue(videoJson, "url");
                        
                        // Create and add the video
                        Video video = new Video(name, resolution, format, url);
                        videoList.add(video);
                        
                        LoggingUtil.info(LOGGER, "PARSER", "Parsed video: " + name + 
                                " (" + resolution + ", " + format + ")");
                    }
                }
            }
        } catch (Exception e) {
            LoggingUtil.error(LOGGER, "PARSER", "Error parsing video list: " + e.getMessage());
        }
        
        return videoList;
    }
    
    /**
     * Extract a value from a simple JSON string
     */
    private static String extractValue(String json, String key) {
        int startIndex = json.indexOf("\"" + key + "\":\"");
        if (startIndex == -1) return "";
        
        startIndex += key.length() + 4; // Skip past the key and ":"
        int endIndex = json.indexOf("\"", startIndex);
        
        if (endIndex == -1) return "";
        
        return json.substring(startIndex, endIndex);
    }
} 