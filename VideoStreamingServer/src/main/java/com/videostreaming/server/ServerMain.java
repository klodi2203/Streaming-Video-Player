package com.videostreaming.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);
    private static final int PORT = 8080;

    public static void main(String[] args) {
        logger.info("Starting Video Streaming Server...");
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI(PORT);
            gui.setVisible(true);
        });
    }
} 