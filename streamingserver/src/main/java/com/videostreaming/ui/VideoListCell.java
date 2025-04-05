package com.videostreaming.ui;

import com.videostreaming.model.Video;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Custom ListCell for displaying Video objects in a ListView
 */
public class VideoListCell extends ListCell<Video> {

    private final HBox container;
    private final Label nameLabel;
    private final Label detailsLabel;

    public VideoListCell() {
        nameLabel = new Label();
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        detailsLabel = new Label();
        detailsLabel.setStyle("-fx-text-fill: #555555;");
        
        VBox contentBox = new VBox(3);
        contentBox.getChildren().addAll(nameLabel, detailsLabel);
        HBox.setHgrow(contentBox, Priority.ALWAYS);
        
        container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 8, 5, 8));
        container.getChildren().addAll(contentBox);
    }

    @Override
    protected void updateItem(Video video, boolean empty) {
        super.updateItem(video, empty);
        
        if (empty || video == null) {
            setText(null);
            setGraphic(null);
        } else {
            nameLabel.setText(video.getName());
            detailsLabel.setText(video.getResolution() + " • " + video.getFormat().toUpperCase());
            setGraphic(container);
        }
    }
} 