package com.videostreaming;

import com.videostreaming.model.Video;
import com.videostreaming.service.VideoConversionService;
import com.videostreaming.service.VideoScanService;
import com.videostreaming.ui.VideoListCell;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
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
    private VideoScanService videoScanService;
    private VideoConversionService videoConversionService;

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
        
        // Create the button
        rescanButton = new Button("Rescan");
        rescanButton.setPrefWidth(120);
        rescanButton.setOnAction(event -> scanVideosDirectory());
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.getChildren().add(rescanButton);

        // Create the right panel with log and button
        VBox rightSection = new VBox(10);
        rightSection.getChildren().addAll(logLabel, logTextArea, buttonBox);
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
        
        // Automatically scan videos directory on startup
        log("Application started");
        scanVideosDirectory();
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
        
        // Add failure handler
        videoScanService.setOnFailed(event -> {
            log("ERROR: Video scan failed - " + event.getSource().getException().getMessage());
            event.getSource().getException().printStackTrace();
            rescanButton.setDisable(false);
        });
        
        // Add success handler to check for necessary video conversions
        videoScanService.setOnSucceeded(event -> {
            rescanButton.setDisable(false);
            
            // Get the scanned videos
            List<Video> videos = videoScanService.getValue();
            
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
        rescanButton.setDisable(true);
        log("Starting video directory scan...");
        
        videoScanService.restart();
    }
    
    /**
     * Start the conversion process to generate all needed format/resolution combinations
     * @param sourceVideos Source videos to convert (highest resolution of each video)
     */
    private void startConversion(List<Video> sourceVideos) {
        rescanButton.setDisable(true);
        log("Starting automatic video conversion for " + sourceVideos.size() + " videos...");
        
        String videosPath = getVideosDirectoryPath();
        
        // Create a copy of the videos list to avoid concurrent modification
        List<Video> videosCopy = new ArrayList<>(sourceVideos);
        videoConversionService = new VideoConversionService(videosCopy, videosPath, this::log);
        
        // Set up callback for when new videos are created
        videoConversionService.setOnNewVideoCreated(video -> {
            // Add the new video to the VideoScanService (which updates the UI)
            videoScanService.addVideo(video);
        });
        
        videoConversionService.setOnSucceeded(event -> {
            log("Video conversion completed - refreshing video list");
            // After all conversions are done, do a full refresh of the video list
            videoScanService.refreshVideoList();
            rescanButton.setDisable(false);
        });
        
        videoConversionService.setOnFailed(event -> {
            log("ERROR: Video conversion failed - " + event.getSource().getException().getMessage());
            event.getSource().getException().printStackTrace();
            rescanButton.setDisable(false);
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
     * Show an alert dialog
     * @param title Alert title
     * @param message Alert message
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Log a message to the log text area
     */
    private void log(String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = dateFormat.format(new Date());
        logTextArea.appendText("\n[" + timestamp + "] " + message);
        
        // Scroll to the bottom
        logTextArea.positionCaret(logTextArea.getText().length());
    }

    /**
     * Main method that launches the application
     */
    public static void main(String[] args) {
        launch(args);
    }
} 