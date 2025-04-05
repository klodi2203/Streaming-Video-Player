package com.videostreaming.client.service;

import com.videostreaming.client.dialog.ProtocolSelectionDialog.StreamingProtocol;
import com.videostreaming.client.model.Video;
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
                        
                        try {
                            // Connect to the server API endpoint
                            String requestUrl = SERVER_BASE_URL + API_LIST_VIDEOS;
                            LOGGER.info("Sending request to: " + requestUrl);
                            updateMessage("Sending request to server...");
                            
                            // Create the request payload
                            String payload = String.format("{\"speed\":%.2f,\"format\":\"%s\"}", speed, format);
                            LOGGER.info("Sending request: " + payload);
                            
                            // Create the HTTP connection
                            URL url = new URL(requestUrl);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Content-Type", "application/json");
                            connection.setDoOutput(true);
                            connection.setConnectTimeout(5000);
                            connection.setReadTimeout(5000);
                            
                            updateProgress(20, 100);
                            
                            // Send request data
                            try (OutputStream os = connection.getOutputStream()) {
                                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                                os.write(input, 0, input.length);
                            }
                            
                            updateProgress(40, 100);
                            updateMessage("Waiting for server response...");
                            
                            // Check response status
                            int responseCode = connection.getResponseCode();
                            if (responseCode != 200) {
                                throw new RuntimeException("Server returned error code: " + responseCode);
                            }
                            
                            updateProgress(60, 100);
                            updateMessage("Receiving video list...");
                            
                            // Read response
                            StringBuilder response = new StringBuilder();
                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = br.readLine()) != null) {
                                    response.append(line);
                                }
                            }
                            
                            updateProgress(80, 100);
                            
                            // Parse JSON response
                            String json = response.toString();
                            LOGGER.info("Received response: " + json);
                            
                            List<Video> videos = parseVideoList(json);
                            
                            updateProgress(100, 100);
                            updateMessage("Received " + videos.size() + " videos");
                            LOGGER.info("Parsed " + videos.size() + " videos from server response");
                            
                            return videos;
                            
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error communicating with server", e);
                            updateMessage("Error: " + e.getMessage());
                            
                            // If server communication fails, try to use simulated data instead
                            LOGGER.info("Using simulated video data as fallback");
                            List<Video> videos = simulateVideoResponse(speed, format);
                            updateProgress(100, 100);
                            updateMessage("Using simulated data: " + videos.size() + " videos");
                            
                            return videos;
                        }
                    }
                    
                    /**
                     * Parse the video list from the JSON response
                     */
                    private List<Video> parseVideoList(String json) {
                        List<Video> videos = new ArrayList<>();
                        
                        try {
                            // Find the start of the videos array
                            int videosStart = json.indexOf("\"videos\":[");
                            if (videosStart == -1) {
                                LOGGER.warning("Couldn't find videos array in response");
                                return videos;
                            }
                            
                            videosStart = json.indexOf("[", videosStart);
                            int videosEnd = json.lastIndexOf("]");
                            
                            if (videosStart == -1 || videosEnd == -1) {
                                LOGGER.warning("Invalid videos array in response");
                                return videos;
                            }
                            
                            // Extract the videos array
                            String videosArray = json.substring(videosStart + 1, videosEnd);
                            
                            // Check if the array is empty
                            if (videosArray.trim().isEmpty()) {
                                return videos;
                            }
                            
                            // Split the array into individual video objects
                            int objStart = 0;
                            int nestingLevel = 0;
                            
                            for (int i = 0; i < videosArray.length(); i++) {
                                char c = videosArray.charAt(i);
                                
                                if (c == '{') {
                                    if (nestingLevel == 0) {
                                        objStart = i;
                                    }
                                    nestingLevel++;
                                } else if (c == '}') {
                                    nestingLevel--;
                                    if (nestingLevel == 0) {
                                        // Found a complete object
                                        String videoObj = videosArray.substring(objStart, i + 1);
                                        Video video = parseVideo(videoObj);
                                        if (video != null) {
                                            videos.add(video);
                                        }
                                    }
                                }
                            }
                            
                            return videos;
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error parsing video list", e);
                            return videos;
                        }
                    }
                    
                    /**
                     * Parse a video object from a JSON string
                     */
                    private Video parseVideo(String json) {
                        try {
                            String name = extractValue(json, "name");
                            String resolution = extractValue(json, "resolution");
                            String format = extractValue(json, "format");
                            String url = extractValue(json, "url");
                            
                            return new Video(name, resolution, format, url);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error parsing video", e);
                            return null;
                        }
                    }
                    
                    /**
                     * Extract a value from a simple JSON string
                     */
                    private String extractValue(String json, String key) {
                        int startIndex = json.indexOf("\"" + key + "\":\"");
                        if (startIndex == -1) return "";
                        
                        startIndex += key.length() + 4; // Skip past the key and ":"
                        int endIndex = json.indexOf("\"", startIndex);
                        
                        if (endIndex == -1) return "";
                        
                        return json.substring(startIndex, endIndex);
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
                        updateMessage("Sending streaming request to server...");
                        
                        try {
                            // Connect to the server API endpoint
                            String requestUrl = SERVER_BASE_URL + API_REQUEST_VIDEO;
                            LOGGER.info("Sending streaming request to: " + requestUrl);
                            
                            // Create the request payload
                            String payload = String.format(
                                "{\"videoName\":\"%s\",\"resolution\":\"%s\",\"format\":\"%s\",\"protocol\":\"%s\"}",
                                video.getName(), video.getResolution(), video.getFormat(), protocol.toString()
                            );
                            LOGGER.info("Sending request: " + payload);
                            
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
                            updateMessage("Waiting for server response...");
                            
                            // Check response status
                            int responseCode = connection.getResponseCode();
                            if (responseCode != 200) {
                                throw new RuntimeException("Server returned error code: " + responseCode);
                            }
                            
                            updateProgress(80, 100);
                            
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
                            LOGGER.info("Received response: " + result);
                            
                            updateProgress(100, 100);
                            updateMessage("Stream request processed");
                            
                            // In a real application, this would establish the stream connection
                            // For now, we just return success if the server accepted the request
                            return true;
                            
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error sending streaming request", e);
                            updateMessage("Error: " + e.getMessage());
                            
                            // If server communication fails, simulate success for demonstration
                            LOGGER.info("Simulating streaming response as fallback");
                            updateProgress(100, 100);
                            updateMessage("Using simulated streaming (server unavailable)");
                            
                            return true;
                        }
                    }
                };
            }
        };
    }
    
    /**
     * In a real application, this would parse the server response.
     * For now, we'll simulate a response based on the inputs.
     * This is used as a fallback when server communication fails.
     */
    private static List<Video> simulateVideoResponse(double speed, String format) {
        List<Video> videos = new ArrayList<>();
        
        // Different video options based on format
        if ("mp4".equals(format)) {
            videos.add(new Video("Earth Documentary", "720p", format, "http://example.com/earth-720p.mp4"));
            videos.add(new Video("Space Exploration", "480p", format, "http://example.com/space-480p.mp4"));
            videos.add(new Video("Ocean Life", "1080p", format, "http://example.com/ocean-1080p.mp4"));
        } else if ("avi".equals(format)) {
            videos.add(new Video("Wildlife", "720p", format, "http://example.com/wildlife-720p.avi"));
            videos.add(new Video("Mountains", "480p", format, "http://example.com/mountains-480p.avi"));
        } else if ("mkv".equals(format)) {
            videos.add(new Video("City Tour", "1080p", format, "http://example.com/city-1080p.mkv"));
            videos.add(new Video("Forest Journey", "720p", format, "http://example.com/forest-720p.mkv"));
        }
        
        // Add common videos
        videos.add(new Video("Tutorial Video", "480p", format, "http://example.com/tutorial-480p." + format));
        videos.add(new Video("Sample Video", "720p", format, "http://example.com/sample-720p." + format));
        
        // If the speed is low, remove high resolution videos
        if (speed < 5.0) {
            videos.removeIf(video -> "1080p".equals(video.getResolution()));
        }
        
        return videos;
    }
} 