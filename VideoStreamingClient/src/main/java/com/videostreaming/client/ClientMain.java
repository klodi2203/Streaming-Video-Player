package com.videostreaming.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMain {
    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);
    
    public static void main(String[] args) {
        logger.info("Starting Video Streaming Client...");
        
        // Run VLCJ diagnostics
        boolean vlcjReady = VlcjDiagnostics.checkVlcjEnvironment();
        if (!vlcjReady) {
            logger.error("VLCJ environment is not properly configured. Videos may not play correctly.");
            // You can choose to exit here if you want
            // System.exit(1);
        }
        
        // Set up system properties for VLCJ if needed
        if (System.getProperty("jna.library.path") == null) {
            // Try to set a reasonable default based on OS
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                System.setProperty("jna.library.path", "/usr/lib:/usr/local/lib");
            } else if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                System.setProperty("jna.library.path", "C:\\Program Files\\VideoLAN\\VLC");
            }
        }
        
        // Start the client
        VideoStreamingClient client = new VideoStreamingClient("localhost", 8080);
        client.start();
    }
} 