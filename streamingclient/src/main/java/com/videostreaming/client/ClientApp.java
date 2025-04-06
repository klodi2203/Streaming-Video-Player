package com.videostreaming.client;

import com.videostreaming.client.dialog.ProtocolSelectionDialog;
import com.videostreaming.client.dialog.ProtocolSelectionDialog.StreamingProtocol;
import com.videostreaming.client.model.Video;
import com.videostreaming.client.service.NetworkService;
import com.videostreaming.client.util.SpeedTestUtil;
import com.videostreaming.client.util.StreamingUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class for the Streaming Client.
 * Provides the GUI interface for users to select and stream videos.
 */
public class ClientApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());
    
    // UI Components
    private ListView<Video> videoListView;
    private ObservableList<Video> videoItems;
    private ProgressIndicator speedTestIndicator;
    private ComboBox<String> formatComboBox;
    private TextArea logArea;
    private Button startStreamingButton;
    private Button stopStreamingButton;
    private Label speedValueLabel;
    private Label speedTestStatusLabel;
    private ProgressIndicator networkProgressIndicator;
    private Label networkStatusLabel;
    
    // Properties
    private StringProperty currentStatus = new SimpleStringProperty();
    private Task<Double> speedTestTask;
    private double downloadSpeed = 0.0;
    private Service<List<Video>> videoRequestService;
    private boolean speedTestCompleted = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Streaming Client");
        
        // Create layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        
        // Add header
        Label headerLabel = new Label("Video Streaming Client");
        headerLabel.getStyleClass().add("header-label");
        root.setTop(headerLabel);
        
        // Main content area - use HBox for side-by-side layout
        HBox contentContainer = new HBox(15);
        contentContainer.setPadding(new Insets(15, 0, 0, 0));
        
        // LEFT SIDE - Video list
        VBox leftPanel = new VBox(10);
        leftPanel.getStyleClass().add("panel");
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(400);
        leftPanel.setMaxHeight(Double.MAX_VALUE);
        leftPanel.setMinHeight(Region.USE_COMPUTED_SIZE);
        leftPanel.setBorder(new Border(new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
        
        Label videosLabel = new Label("Available Videos");
        videosLabel.getStyleClass().add("section-label");
        
        videoListView = new ListView<>();
        videoItems = FXCollections.observableArrayList();
        videoListView.setItems(videoItems);
        videoListView.getStyleClass().add("video-list");
        videoListView.setPrefHeight(Double.MAX_VALUE);
        videoListView.setMinHeight(200);
        videoListView.setPlaceholder(new Label("No videos available"));
        
        // Configure the cell factory to display video information
        videoListView.setCellFactory(new Callback<ListView<Video>, ListCell<Video>>() {
            @Override
            public ListCell<Video> call(ListView<Video> param) {
                return new ListCell<Video>() {
                    @Override
                    protected void updateItem(Video video, boolean empty) {
                        super.updateItem(video, empty);
                        
                        if (empty || video == null) {
                            setText(null);
                        } else {
                            setText(video.getName() + " (" + video.getResolution() + ", " + video.getFormat() + ")");
                        }
                    }
                };
            }
        });
        
        // Add selection listener for the list view
        videoListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Enable the streaming button when a video is selected
            startStreamingButton.setDisable(newVal == null);
        });
        
        leftPanel.getChildren().addAll(videosLabel, videoListView);
        VBox.setVgrow(videoListView, Priority.ALWAYS);
        
        // RIGHT SIDE - Controls and log
        VBox rightPanel = new VBox(15);
        rightPanel.setPrefWidth(350);
        rightPanel.setMaxHeight(Double.MAX_VALUE);
        
        // Speed test section
        VBox speedTestSection = new VBox(5);
        speedTestSection.getStyleClass().add("panel");
        speedTestSection.setPadding(new Insets(10));
        
        Label speedTestHeading = new Label("Download Speed Test");
        speedTestHeading.getStyleClass().add("section-label");
        
        // Speed test status
        speedTestStatusLabel = new Label("Testing download speed...");
        speedTestStatusLabel.setTextFill(Color.GRAY);
        
        // Speed display with indicator
        HBox speedDisplayBox = new HBox(10);
        speedDisplayBox.setAlignment(Pos.CENTER);
        
        speedTestIndicator = new ProgressIndicator(0);
        speedTestIndicator.setPrefSize(40, 40);
        
        speedValueLabel = new Label("0.00 Mbps");
        speedValueLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        speedDisplayBox.getChildren().addAll(speedTestIndicator, speedValueLabel);
        
        speedTestSection.getChildren().addAll(speedTestHeading, speedTestStatusLabel, speedDisplayBox);
        
        // Format selection section
        VBox formatSection = new VBox(5);
        formatSection.getStyleClass().add("panel");
        formatSection.setPadding(new Insets(10));
        
        Label formatLabel = new Label("Video Format");
        formatLabel.getStyleClass().add("section-label");
        
        formatComboBox = new ComboBox<>();
        formatComboBox.getItems().addAll("mp4", "avi", "mkv");
        formatComboBox.setValue("mp4");
        formatComboBox.setDisable(true); // Initially disabled until speed test completes
        formatComboBox.setPrefWidth(Double.MAX_VALUE);
        
        // Add network progress section
        HBox networkStatusBox = new HBox(10);
        networkStatusBox.setAlignment(Pos.CENTER_LEFT);
        
        networkProgressIndicator = new ProgressIndicator(-1);
        networkProgressIndicator.setPrefSize(24, 24);
        networkProgressIndicator.setVisible(false);
        
        networkStatusLabel = new Label("Ready to request videos");
        
        networkStatusBox.getChildren().addAll(networkProgressIndicator, networkStatusLabel);
        
        formatSection.getChildren().addAll(formatLabel, formatComboBox, networkStatusBox);
        
        // Log section
        VBox logSection = new VBox(10);
        logSection.getStyleClass().add("panel");
        logSection.setPadding(new Insets(10));
        logSection.setMaxHeight(Double.MAX_VALUE);
        
        Label logLabel = new Label("Client Log");
        logLabel.getStyleClass().add("section-label");
        
        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setPrefHeight(200);
        
        // Button section
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        startStreamingButton = new Button("Start Streaming");
        startStreamingButton.setPrefWidth(120);
        startStreamingButton.setDisable(true); // Initially disabled
        startStreamingButton.setOnAction(event -> handleStartStreamingButtonClick());
        
        stopStreamingButton = new Button("Stop Streaming");
        stopStreamingButton.setPrefWidth(120);
        stopStreamingButton.setDisable(true); // Initially disabled
        stopStreamingButton.setOnAction(event -> handleStopStreamingButtonClick());
        
        buttonBox.getChildren().addAll(stopStreamingButton, startStreamingButton);
        
        logSection.getChildren().addAll(logLabel, logArea, buttonBox);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        
        // Add all sections to right panel
        rightPanel.getChildren().addAll(speedTestSection, formatSection, logSection);
        VBox.setVgrow(logSection, Priority.ALWAYS);
        
        // Add both panels to the content container
        contentContainer.getChildren().addAll(leftPanel, rightPanel);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.NEVER);
        
        root.setCenter(contentContainer);
        
        // Initialize scene with same size as server
        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);
        primaryStage.show();
        
        // Add initial log message
        log("Streaming client initialized");
        
        // Check if FFMPEG is available
        checkFFmpegAvailability();
        
        // Set up event handler for the format combo box
        formatComboBox.setOnAction(event -> {
            if (speedTestCompleted) {
                String selectedFormat = formatComboBox.getValue();
                log("Format selected: " + selectedFormat);
                log("Requesting videos with format: " + selectedFormat + " and speed: " + String.format("%.2f", downloadSpeed) + " Mbps");
                requestVideosFromServer(selectedFormat);
            }
        });
        
        // Start the speed test after a short delay to let the UI render
        Platform.runLater(() -> {
            startSpeedTest();
        });
    }
    
    /**
     * Start a download speed test and update the UI with the results
     */
    private void startSpeedTest() {
        log("Starting download speed test...");
        speedTestStatusLabel.setText("Testing download speed (5 seconds)...");
        
        // Create and configure the speed test task
        speedTestTask = SpeedTestUtil.createSpeedTestTask();
        
        // Bind the progress indicator to the task progress
        speedTestIndicator.progressProperty().bind(speedTestTask.progressProperty());
        
        // Update the speed value label with the current speed
        speedTestTask.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                speedValueLabel.setText(newVal);
            }
        });
        
        // Handle task completion
        speedTestTask.setOnSucceeded(event -> {
            double finalSpeed = speedTestTask.getValue();
            downloadSpeed = finalSpeed; // Store for later use
            speedTestCompleted = true;
            String formattedSpeed = String.format("%.2f Mbps", finalSpeed);
            
            // Update UI
            speedValueLabel.setText(formattedSpeed);
            speedTestIndicator.progressProperty().unbind();
            speedTestIndicator.setProgress(1.0);
            speedTestStatusLabel.setText("Speed test completed!");
            
            // Enable format selection
            formatComboBox.setDisable(false);
            networkStatusLabel.setText("Select a format to request videos");
            
            log("Speed test completed: " + formattedSpeed);
            
            // Add speed rating based on the result
            String speedRating;
            if (finalSpeed < 5) {
                speedRating = "Low speed - may experience buffering with high resolution videos";
                speedTestStatusLabel.setTextFill(Color.RED);
            } else if (finalSpeed < 20) {
                speedRating = "Medium speed - suitable for standard definition streaming";
                speedTestStatusLabel.setTextFill(Color.ORANGE);
            } else {
                speedRating = "Good speed - suitable for HD streaming";
                speedTestStatusLabel.setTextFill(Color.GREEN);
            }
            
            log(speedRating);
            log("Please select a video format to continue.");
            
            // Automatically request videos with the default format
            String selectedFormat = formatComboBox.getValue();
            log("Automatically requesting videos with format: " + selectedFormat);
            requestVideosFromServer(selectedFormat);
        });
        
        // Handle task failure
        speedTestTask.setOnFailed(event -> {
            Throwable ex = speedTestTask.getException();
            log("Speed test failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            
            // Update UI
            speedValueLabel.setText("Test failed");
            speedTestIndicator.progressProperty().unbind();
            speedTestIndicator.setProgress(0);
            speedTestStatusLabel.setText("Speed test failed. Using default settings.");
            speedTestStatusLabel.setTextFill(Color.RED);
            
            // Enable format selection anyway to allow user to continue
            formatComboBox.setDisable(false);
            speedTestCompleted = true;
            downloadSpeed = 10.0; // Default speed if test fails
            networkStatusLabel.setText("Select a format to request videos");
            
            log("Using default speed value of 10.0 Mbps");
            log("Please select a video format to continue.");
            
            // Automatically request videos with the default format
            String selectedFormat = formatComboBox.getValue();
            log("Automatically requesting videos with format: " + selectedFormat);
            requestVideosFromServer(selectedFormat);
        });
        
        // Start the task
        Thread speedTestThread = new Thread(speedTestTask);
        speedTestThread.setDaemon(true);
        speedTestThread.start();
    }
    
    /**
     * Request videos from the server based on selected format and measured speed
     */
    private void requestVideosFromServer(String format) {
        // Clear existing videos
        videoItems.clear();
        
        // Update UI
        networkProgressIndicator.setVisible(true);
        networkStatusLabel.setText("Connecting to server...");
        networkStatusLabel.setTextFill(Color.BLUE);
        
        // Create the service for requesting videos
        videoRequestService = NetworkService.createVideoRequestService(downloadSpeed, format);
        
        // Bind progress indicator
        networkProgressIndicator.progressProperty().bind(videoRequestService.progressProperty());
        
        // Listen for status updates
        videoRequestService.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                networkStatusLabel.setText(newVal);
            }
        });
        
        // Handle completion
        videoRequestService.setOnSucceeded(event -> {
            List<Video> videos = videoRequestService.getValue();
            
            // Update UI
            networkProgressIndicator.progressProperty().unbind();
            networkProgressIndicator.setProgress(1.0);
            networkStatusLabel.setText("Received " + videos.size() + " videos");
            networkStatusLabel.setTextFill(Color.GREEN);
            
            log("DEBUG: Received " + videos.size() + " videos from server");
            for (Video video : videos) {
                log("DEBUG: Video: " + video.getName() + " (" + video.getResolution() + ", " + video.getFormat() + ")");
            }
            
            // Add videos to the list on the JavaFX application thread
            Platform.runLater(() -> {
                log("DEBUG: Clearing existing videos");
                videoItems.clear();
                
                log("DEBUG: Adding videos to the list");
                videoItems.addAll(videos);
                
                log("DEBUG: Video list size after adding: " + videoItems.size());
                log("DEBUG: ListView item count: " + videoListView.getItems().size());
            });
            
            log("Received " + videos.size() + " compatible videos from server");
            log("Ready to stream. Select a video to begin.");
        });
        
        // Handle failure
        videoRequestService.setOnFailed(event -> {
            Throwable ex = videoRequestService.getException();
            log("Server communication failed: " + (ex != null ? ex.getMessage() : "Unknown error"));
            
            // Update UI
            networkProgressIndicator.progressProperty().unbind();
            networkProgressIndicator.setProgress(0);
            networkStatusLabel.setText("Failed to connect to server");
            networkStatusLabel.setTextFill(Color.RED);
        });
        
        // Start the service
        videoRequestService.start();
    }
    
    /**
     * Check if FFMPEG is available
     */
    private void checkFFmpegAvailability() {
        new Thread(() -> {
            boolean ffmpegAvailable = StreamingUtil.isFFmpegAvailable();
            Platform.runLater(() -> {
                if (ffmpegAvailable) {
                    log("FFMPEG is available on this system.");
                } else {
                    log("WARNING: FFMPEG is not available on this system. Streaming functionality will be limited.");
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("FFMPEG Not Found");
                    alert.setHeaderText("FFMPEG is required for video streaming");
                    alert.setContentText("FFMPEG could not be found on your system. Please install FFMPEG to enable video streaming functionality.\n\n" +
                            "You can download FFMPEG from: https://ffmpeg.org/download.html");
                    alert.showAndWait();
                }
            });
        }).start();
    }
    
    /**
     * Handle the Start Streaming button click
     * This will show the protocol selection dialog and start streaming
     */
    private void handleStartStreamingButtonClick() {
        Video selectedVideo = videoListView.getSelectionModel().getSelectedItem();
        if (selectedVideo == null) {
            log("No video selected");
            return;
        }
        
        log("Starting stream for: " + selectedVideo.getName() + " (" + selectedVideo.getResolution() + ", " + selectedVideo.getFormat() + ")");
        
        // Show the protocol selection dialog
        Optional<StreamingProtocol> protocolResult = ProtocolSelectionDialog.show(selectedVideo);
        
        protocolResult.ifPresent(protocol -> {
            log("Selected protocol: " + protocol);
            
            // Disable streaming button during request
            startStreamingButton.setDisable(true);
            
            // Create the streaming request service
            Service<Boolean> streamingService = NetworkService.createStreamingRequestService(selectedVideo, protocol);
            
            // Show progress while connecting
            networkProgressIndicator.setVisible(true);
            networkProgressIndicator.progressProperty().bind(streamingService.progressProperty());
            
            // Update status
            streamingService.messageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    networkStatusLabel.setText(newVal);
                }
            });
            
            // Handle success
            streamingService.setOnSucceeded(e -> {
                networkProgressIndicator.progressProperty().unbind();
                networkProgressIndicator.setProgress(1.0);
                networkStatusLabel.setText("Stream request accepted");
                networkStatusLabel.setTextFill(Color.GREEN);
                
                log("Server accepted streaming request, starting FFMPEG...");
                
                // Start FFMPEG to receive the stream
                try {
                    StreamingUtil.startStreaming(
                        selectedVideo, 
                        protocol, 
                        message -> log(message), // Log output
                        () -> {
                            // Streaming completed
                            Platform.runLater(() -> {
                                log("Streaming completed");
                                startStreamingButton.setDisable(false);
                                stopStreamingButton.setDisable(true);
                                networkStatusLabel.setText("Streaming completed");
                            });
                        }
                    );
                    
                    // Enable stop button
                    stopStreamingButton.setDisable(false);
                    
                } catch (IOException ex) {
                    log("Error starting FFMPEG: " + ex.getMessage());
                    LOGGER.log(Level.SEVERE, "Error starting FFMPEG", ex);
                    startStreamingButton.setDisable(false);
                }
            });
            
            // Handle failure
            streamingService.setOnFailed(e -> {
                Throwable ex = streamingService.getException();
                log("Failed to start streaming: " + (ex != null ? ex.getMessage() : "Unknown error"));
                
                networkProgressIndicator.progressProperty().unbind();
                networkProgressIndicator.setProgress(0);
                networkStatusLabel.setText("Streaming failed");
                networkStatusLabel.setTextFill(Color.RED);
                
                startStreamingButton.setDisable(false);
            });
            
            // Start the service
            streamingService.start();
        });
    }
    
    /**
     * Handle the Stop Streaming button click
     */
    private void handleStopStreamingButtonClick() {
        log("Stopping stream...");
        StreamingUtil.stopStreaming();
        stopStreamingButton.setDisable(true);
        startStreamingButton.setDisable(false);
        networkStatusLabel.setText("Streaming stopped");
        networkStatusLabel.setTextFill(Color.ORANGE);
    }
    
    /**
     * Show a notification dialog that the stream has started
     * In a real application, this would launch the video player
     */
    private void showStreamingNotification(Video video, StreamingProtocol protocol) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Streaming Started");
            alert.setHeaderText("Video Streaming Started");
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            
            Label videoLabel = new Label("Video: " + video.getName());
            Label resolutionLabel = new Label("Resolution: " + video.getResolution());
            Label formatLabel = new Label("Format: " + video.getFormat());
            Label protocolLabel = new Label("Protocol: " + protocol);
            
            content.getChildren().addAll(
                    videoLabel, resolutionLabel, formatLabel, protocolLabel,
                    new Separator(),
                    new Label("The video will be played using your system's default video player.")
            );
            
            alert.getDialogPane().setContent(content);
            alert.showAndWait();
        });
    }
    
    /**
     * Adds a message to the log area
     * @param message The message to log
     */
    private void log(String message) {
        LOGGER.info(message);
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            // Auto-scroll to bottom
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    @Override
    public void stop() throws Exception {
        super.stop();
        
        // Cancel any running tasks when the application is closed
        if (speedTestTask != null && speedTestTask.isRunning()) {
            speedTestTask.cancel();
        }
        
        if (videoRequestService != null && videoRequestService.isRunning()) {
            videoRequestService.cancel();
        }
        
        // Stop any active streaming
        StreamingUtil.stopStreaming();
        
        // Clean up temporary files - wrapped in try-catch to prevent application crash on exit
        try {
            StreamingUtil.cleanupTempFiles();
        } catch (Exception e) {
            // Log the error but don't re-throw to allow application to exit cleanly
            LOGGER.log(Level.WARNING, "Error during cleanup: " + e.getMessage(), e);
        }
    }
} 