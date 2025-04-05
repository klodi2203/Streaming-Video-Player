package com.videostreaming.client.dialog;

import com.videostreaming.client.model.Video;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Dialog for selecting a streaming protocol
 */
public class ProtocolSelectionDialog {

    private static final Logger LOGGER = Logger.getLogger(ProtocolSelectionDialog.class.getName());
    
    // Streaming protocols
    public enum StreamingProtocol {
        TCP("TCP - Reliable, slower but ensures data integrity"),
        UDP("UDP - Faster, but may lose packets"),
        RTP_UDP("RTP/UDP - Optimized for real-time streaming");
        
        private final String description;
        
        StreamingProtocol(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return name().replace("_", "/");
        }
    }
    
    /**
     * Show the protocol selection dialog and return the selected protocol
     * @param video The selected video
     * @return The selected protocol, or empty if canceled
     */
    public static Optional<StreamingProtocol> show(Video video) {
        // Create the dialog
        Dialog<StreamingProtocol> dialog = new Dialog<>();
        dialog.setTitle("Select Streaming Protocol");
        dialog.setHeaderText("Choose a protocol for streaming:");
        
        // Set the button types
        ButtonType selectButtonType = new ButtonType("Start Streaming", ButtonBar.ButtonData.OK_DONE);
        ButtonType autoSelectButtonType = new ButtonType("Auto-Select", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(selectButtonType, autoSelectButtonType, ButtonType.CANCEL);

        // Create the protocol options
        ToggleGroup protocolGroup = new ToggleGroup();
        
        VBox protocolOptions = new VBox(10);
        protocolOptions.setPadding(new Insets(10));
        
        // Video information
        Text videoInfo = new Text("Selected Video: " + video.getName() + " (" + video.getResolution() + ", " + video.getFormat() + ")");
        videoInfo.setFont(Font.font("System", FontWeight.BOLD, 12));
        protocolOptions.getChildren().add(videoInfo);
        
        // Add some explanation of each protocol
        Text infoText = new Text("Different protocols have different characteristics:");
        infoText.setFont(Font.font("System", FontWeight.NORMAL, 12));
        protocolOptions.getChildren().add(infoText);
        
        // Create radio buttons for each protocol
        for (StreamingProtocol protocol : StreamingProtocol.values()) {
            RadioButton rb = new RadioButton(protocol.toString() + " - " + protocol.getDescription());
            rb.setToggleGroup(protocolGroup);
            rb.setUserData(protocol);
            protocolOptions.getChildren().add(rb);
            
            // Select TCP by default
            if (protocol == StreamingProtocol.TCP) {
                rb.setSelected(true);
            }
        }
        
        // Auto-selection information
        GridPane autoSelectInfo = new GridPane();
        autoSelectInfo.setHgap(10);
        autoSelectInfo.setVgap(5);
        autoSelectInfo.setPadding(new Insets(10, 0, 0, 20));
        
        Text autoSelectHeader = new Text("Auto-selection rules:");
        autoSelectHeader.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        autoSelectInfo.add(autoSelectHeader, 0, 0, 2, 1);
        autoSelectInfo.add(new Label("240p:"), 0, 1);
        autoSelectInfo.add(new Label("TCP"), 1, 1);
        autoSelectInfo.add(new Label("360p/480p:"), 0, 2);
        autoSelectInfo.add(new Label("UDP"), 1, 2);
        autoSelectInfo.add(new Label("720p/1080p:"), 0, 3);
        autoSelectInfo.add(new Label("RTP/UDP"), 1, 3);
        
        Separator separator = new Separator();
        separator.setPadding(new Insets(10, 0, 10, 0));
        
        protocolOptions.getChildren().addAll(separator, autoSelectInfo);
        
        dialog.getDialogPane().setContent(protocolOptions);
        
        // Auto-select handler
        Button autoSelectButton = (Button) dialog.getDialogPane().lookupButton(autoSelectButtonType);
        autoSelectButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            StreamingProtocol autoSelectedProtocol = getAutoSelectedProtocol(video);
            dialog.setResult(autoSelectedProtocol);
            
            // Prevent the dialog from closing
            event.consume();
            
            // Update the radio button selection
            for (Toggle toggle : protocolGroup.getToggles()) {
                if (toggle.getUserData() == autoSelectedProtocol) {
                    toggle.setSelected(true);
                    break;
                }
            }
            
            LOGGER.info("Auto-selected protocol: " + autoSelectedProtocol + " for resolution " + video.getResolution());
        });
        
        // Convert the result when the select button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                Toggle selectedToggle = protocolGroup.getSelectedToggle();
                if (selectedToggle != null) {
                    return (StreamingProtocol) selectedToggle.getUserData();
                }
            }
            return null;
        });
        
        return dialog.showAndWait();
    }
    
    /**
     * Auto-select a protocol based on video resolution
     * @param video The video to stream
     * @return The auto-selected protocol
     */
    public static StreamingProtocol getAutoSelectedProtocol(Video video) {
        String resolution = video.getResolution();
        
        if (resolution.equals("240p")) {
            return StreamingProtocol.TCP;
        } else if (resolution.equals("360p") || resolution.equals("480p")) {
            return StreamingProtocol.UDP;
        } else { // 720p, 1080p, etc.
            return StreamingProtocol.RTP_UDP;
        }
    }
} 