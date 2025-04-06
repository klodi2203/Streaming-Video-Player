package com.videostreaming;

import com.videostreaming.model.Video;
import com.videostreaming.service.VideoConversionService;
import com.videostreaming.service.VideoScanService;
import com.videostreaming.ui.VideoListCell;
import com.videostreaming.api.ApiServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class for the Streaming Server
 */
public class MainApp extends Application {

    private static final String DEFAULT_VIDEOS_DIR = "videos";
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    
    private ListView<Video> videoListView;
    private TextArea logTextArea;
    private Button rescanButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    private VideoScanService videoScanService;
    private VideoConversionService videoConversionService;
    private ApiServer apiServer;
    
    // Server status components
    private Label activeClientsLabel;
    private Label activeStreamsLabel;
    private Label totalClientsLabel;
    private Label serverStatusLabel;
    private ScheduledExecutorService statusUpdateExecutor;
    
    // Executor for UI updates
    private final ScheduledExecutorService uiUpdateExecutor = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void start(Stage primaryStage) {
        setupLogger();
        
        primaryStage.setTitle("Streaming Server");

        // Create the main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(15));

        // Create header
        Label headerLabel = new Label("Video Streaming Server");
        headerLabel.getStyleClass().add("header-label");
        
        // Create the video list section
        Label videoListLabel = new Label("Available Videos");
        videoListLabel.getStyleClass().add("section-label");
        
        videoListView = new ListView<>();
        videoListView.setCellFactory(param -> new VideoListCell());
        
        VBox videoSection = new VBox(10);
        videoSection.getChildren().addAll(videoListLabel, videoListView);
        VBox.setVgrow(videoListView, Priority.ALWAYS);
        
        // Add video section to a panel container
        VBox videoPanel = new VBox(videoSection);
        videoPanel.getStyleClass().add("panel");
        videoPanel.setPadding(new Insets(10));
        videoPanel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(videoPanel, Priority.ALWAYS);

        // Create the log text area
        Label logLabel = new Label("Server Logs");
        logLabel.getStyleClass().add("section-label");
        
        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        
        // Create status components
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-style: italic;");
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        
        // Create server status section
        VBox serverStatusBox = new VBox(5);
        serverStatusBox.getStyleClass().add("status-section");
        serverStatusBox.setPadding(new Insets(10));
        
        Label serverStatusLabel = new Label("Server Status");
        serverStatusLabel.getStyleClass().add("status-heading");
        
        activeClientsLabel = new Label("Active Clients: 0");
        activeStreamsLabel = new Label("Active Streams: 0");
        totalClientsLabel = new Label("Total Clients: 0");
        
        serverStatusBox.getChildren().addAll(
            serverStatusLabel, 
            activeClientsLabel, 
            activeStreamsLabel, 
            totalClientsLabel
        );
        
        // Create the buttons
        rescanButton = new Button("Rescan");
        rescanButton.setPrefWidth(120);
        rescanButton.setOnAction(event -> scanVideosDirectory());
        
        cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(120);
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> cancelOperations());
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.getChildren().addAll(rescanButton, cancelButton);

        // Create the right panel with log and button
        VBox rightSection = new VBox(10);
        rightSection.getChildren().addAll(logLabel, logTextArea, serverStatusBox, statusLabel, progressBar, buttonBox);
        VBox.setVgrow(logTextArea, Priority.ALWAYS);
        
        // Add log section to a panel container
        VBox rightPanel = new VBox(rightSection);
        rightPanel.getStyleClass().add("panel");
        rightPanel.setPadding(new Insets(10));
        rightPanel.setMinWidth(300);
        rightPanel.setPrefWidth(300);

        // Create a content container with spacing
        HBox contentContainer = new HBox(15);
        contentContainer.getChildren().addAll(videoPanel, rightPanel);
        contentContainer.setPadding(new Insets(15, 0, 0, 0));
        HBox.setHgrow(contentContainer, Priority.ALWAYS);
        
        // Set up the layout
        mainLayout.setTop(headerLabel);
        mainLayout.setCenter(contentContainer);

        // Create the scene
        Scene scene = new Scene(mainLayout, 900, 600);
        
        // Load CSS
        String cssPath = getClass().getResource("/css/styles.css").toExternalForm();
        scene.getStylesheets().add(cssPath);
        
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        primaryStage.show();

        // Initialize video scan service
        initVideoScanService();
        
        // Initialize API server
        initApiServer();
        
        // Start periodic status updates
        startStatusUpdates();
        
        // Automatically scan videos directory on startup
        log("Application started");
        scanVideosDirectory();
        
        // Add shutdown hook for executor
        primaryStage.setOnCloseRequest(event -> {
            if (uiUpdateExecutor != null) {
                uiUpdateExecutor.shutdown();
            }
            if (statusUpdateExecutor != null) {
                statusUpdateExecutor.shutdown();
            }
        });
    }
    
    /**
     * Cancel ongoing operations
     */
    private void cancelOperations() {
        if (videoScanService != null && videoScanService.isRunning()) {
            videoScanService.cancel();
            log("Video scanning canceled");
        }
        
        if (videoConversionService != null && videoConversionService.isRunning()) {
            videoConversionService.cancel();
            log("Video conversion canceled");
        }
        
        // Re-enable the rescan button
        rescanButton.setDisable(false);
        cancelButton.setDisable(true);
        progressBar.setVisible(false);
        statusLabel.setText("Ready");
    }
    
    /**
     * Initialize the video scan service
     */
    private void initVideoScanService() {
        // Resolve videos directory path - use current directory if absolute path not available
        String videosPath = getVideosDirectoryPath();
        log("Using videos directory: " + videosPath);
        
        // Create the video scan service
        videoScanService = new VideoScanService(videosPath, this::log);
        
        // Bind the video list to the service's observable list
        videoListView.setItems(videoScanService.getVideoList());
        
        // Bind progress and status updates
        videoScanService.progressProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                progressBar.setProgress(newVal.doubleValue());
            });
        });
        
        // Add running state listeners
        videoScanService.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            Platform.runLater(() -> {
                if (isRunning) {
                    statusLabel.setText("Scanning directory...");
                    progressBar.setVisible(true);
                    rescanButton.setDisable(true);
                    cancelButton.setDisable(false);
                } else if (!wasRunning) {
                    // Only update if we were running before
                    progressBar.setVisible(false);
                    cancelButton.setDisable(true);
                }
            });
        });
        
        // Add failure handler
        videoScanService.setOnFailed(event -> {
            log("ERROR: Video scan failed - " + event.getSource().getException().getMessage());
            event.getSource().getException().printStackTrace();
            
            Platform.runLater(() -> {
                rescanButton.setDisable(false);
                cancelButton.setDisable(true);
                progressBar.setVisible(false);
                statusLabel.setText("Scan failed");
            });
        });
        
        // Add success handler to check for necessary video conversions
        videoScanService.setOnSucceeded(event -> {
            // Get the scanned videos
            List<Video> videos = videoScanService.getValue();
            
            Platform.runLater(() -> {
                if (!videoConversionService.isRunning()) {
                    // Only update UI if we're not immediately starting a conversion
                    rescanButton.setDisable(false);
                    cancelButton.setDisable(true);
                    progressBar.setVisible(false);
                    statusLabel.setText("Scan completed");
                }
            });
            
            if (videos.isEmpty()) {
                log("No videos found in directory");
                return;
            }
            
            log("Checking for necessary video conversions");
            
            // Find highest resolution videos to use as sources
            Map<String, Video> highestResolutionVideos = new HashMap<>();
            
            for (Video video : videos) {
                String baseName = video.getName();
                
                if (!highestResolutionVideos.containsKey(baseName)) {
                    highestResolutionVideos.put(baseName, video);
                } else {
                    Video existing = highestResolutionVideos.get(baseName);
                    if (video.compareResolution(existing) > 0) {
                        highestResolutionVideos.put(baseName, video);
                    }
                }
            }
            
            // Check for missing format-resolution combinations
            List<Video> sourceVideos = new ArrayList<>(highestResolutionVideos.values());
            
            // Show conversion button if we have videos
            if (!sourceVideos.isEmpty()) {
                log("Found " + sourceVideos.size() + " unique videos");
                startConversion(sourceVideos);
            }
        });
    }
    
    /**
     * Start scanning videos directory in background thread
     */
    private void scanVideosDirectory() {
        if (videoScanService.isRunning()) {
            log("Video scan already in progress");
            return;
        }
        
        Platform.runLater(() -> {
            rescanButton.setDisable(true);
            cancelButton.setDisable(false);
            progressBar.setVisible(true);
            progressBar.setProgress(-1); // Indeterminate progress
            statusLabel.setText("Starting scan...");
        });
        
        log("Starting video directory scan...");
        videoScanService.restart();
    }
    
    /**
     * Start the conversion process to generate all needed format/resolution combinations
     * @param sourceVideos Source videos to convert (highest resolution of each video)
     */
    private void startConversion(List<Video> sourceVideos) {
        Platform.runLater(() -> {
            rescanButton.setDisable(true);
            cancelButton.setDisable(false);
            progressBar.setVisible(true);
            statusLabel.setText("Starting video conversion...");
        });
        
        log("Starting automatic video conversion for " + sourceVideos.size() + " videos...");
        
        String videosPath = getVideosDirectoryPath();
        
        // Create a copy of the videos list to avoid concurrent modification
        List<Video> videosCopy = new ArrayList<>(sourceVideos);
        videoConversionService = new VideoConversionService(videosCopy, videosPath, this::log);
        
        // Bind progress updates
        videoConversionService.progressProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                progressBar.setProgress(newVal.doubleValue());
            });
        });
        
        // Add running state change listener
        videoConversionService.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            Platform.runLater(() -> {
                if (isRunning) {
                    rescanButton.setDisable(true);
                    cancelButton.setDisable(false);
                    progressBar.setVisible(true);
                    statusLabel.setText("Converting videos...");
                } else if (!wasRunning) {
                    progressBar.setVisible(false);
                    cancelButton.setDisable(true);
                }
            });
        });
        
        // Set up callback for when new videos are created
        videoConversionService.setOnNewVideoCreated(video -> {
            // Add the new video to the VideoScanService (which updates the UI)
            videoScanService.addVideo(video);
        });
        
        videoConversionService.setOnSucceeded(event -> {
            log("Video conversion completed - refreshing video list");
            // After all conversions are done, do a full refresh of the video list
            videoScanService.refreshVideoList();
            
            Platform.runLater(() -> {
                rescanButton.setDisable(false);
                cancelButton.setDisable(true);
                progressBar.setVisible(false);
                statusLabel.setText("Conversion completed");
            });
        });
        
        videoConversionService.setOnFailed(event -> {
            log("ERROR: Video conversion failed - " + event.getSource().getException().getMessage());
            event.getSource().getException().printStackTrace();
            
            Platform.runLater(() -> {
                rescanButton.setDisable(false);
                cancelButton.setDisable(true);
                progressBar.setVisible(false);
                statusLabel.setText("Conversion failed");
            });
        });
        
        videoConversionService.start();
    }
    
    /**
     * Get the path to the videos directory
     */
    private String getVideosDirectoryPath() {
        return Paths.get(System.getProperty("user.dir"), DEFAULT_VIDEOS_DIR).toString();
    }
    
    /**
     * Set up the logger
     */
    private void setupLogger() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        
        // Remove existing handlers
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        
        // Add console handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        rootLogger.addHandler(consoleHandler);
        
        LOGGER.info("Logger initialized");
    }
    
    /**
     * Log a message to the UI
     */
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String logMessage = timestamp + " " + message;
        
        LOGGER.info(message);
        
        // Update the UI on the JavaFX application thread
        Platform.runLater(() -> {
            logTextArea.appendText(logMessage + "\n");
            // Auto-scroll to the bottom
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * Initialize the API server
     */
    private void initApiServer() {
        // Create and start API server on port 8080
        apiServer = new ApiServer(8080, videoScanService);
        apiServer.start();
        log("API server started on port 8080");
    }

    /**
     * Start periodic server status updates
     */
    private void startStatusUpdates() {
        // Create a scheduled executor to update the status labels
        statusUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        
        statusUpdateExecutor.scheduleWithFixedDelay(() -> {
            Platform.runLater(() -> {
                try {
                    // Update server status information
                    com.videostreaming.api.StreamingServer streamingServer = apiServer.getStreamingServer();
                    activeClientsLabel.setText("Active Clients: " + streamingServer.getActiveClientsCount());
                    activeStreamsLabel.setText("Active Streams: " + streamingServer.getActiveStreamsCount());
                    totalClientsLabel.setText("Total Clients: " + streamingServer.getTotalClientCount());
                    
                    // Get the size of the registered client IDs map through reflection to show connected clients
                    // directly from registered clients rather than just active streams
                    try {
                        java.lang.reflect.Field registeredClientIdsField = 
                                streamingServer.getClass().getDeclaredField("registeredClientIds");
                        registeredClientIdsField.setAccessible(true);
                        Map<?, ?> registeredClientIds = (Map<?, ?>) registeredClientIdsField.get(streamingServer);
                        
                        // Show a more detailed message about registered clients vs active streams
                        if (registeredClientIds != null) {
                            StringBuilder statusText = new StringBuilder();
                            statusText.append("Server Status: ");
                            statusText.append(streamingServer.isRunning() ? "Running" : "Stopped");
                            statusText.append(" | Registered Clients: ").append(registeredClientIds.size());
                            statusText.append(" | Active Streams: ").append(streamingServer.getActiveStreamsCount());
                            
                            serverStatusLabel.setText(statusText.toString());
                        }
                    } catch (Exception e) {
                        // If we can't access the registeredClientIds field, just use the regular status
                        serverStatusLabel.setText("Server Status: " + 
                                (streamingServer.isRunning() ? "Running" : "Stopped"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    serverStatusLabel.setText("Server Status: Error updating");
                }
            });
        }, 0, 2, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void stop() {
        // Shutdown the scheduled executor service
        if (uiUpdateExecutor != null && !uiUpdateExecutor.isShutdown()) {
            uiUpdateExecutor.shutdownNow();
        }
        
        // Shutdown the status update executor
        if (statusUpdateExecutor != null && !statusUpdateExecutor.isShutdown()) {
            statusUpdateExecutor.shutdownNow();
        }
        
        // Stop API server
        if (apiServer != null) {
            apiServer.stop();
        }
    }
} 