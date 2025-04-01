package com.videostreaming.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

import java.io.File;

public class VlcjDiagnostics {
    private static final Logger logger = LoggerFactory.getLogger(VlcjDiagnostics.class);

    public static boolean checkVlcjEnvironment() {
        logger.info("Running VLCJ diagnostics...");
        
        // Check operating system
        String os = System.getProperty("os.name").toLowerCase();
        logger.info("Operating System: {}", System.getProperty("os.name"));
        logger.info("Architecture: {}", System.getProperty("os.arch"));
        
        // Check JNA paths
        logger.info("JNA Library Path: {}", System.getProperty("jna.library.path"));
        
        // Try to discover native libraries
        boolean discovered = new NativeDiscovery().discover();
        logger.info("Native discovery successful: {}", discovered);
        
        if (discovered) {
            try {
                // Try to create a media player factory to verify VLC is available
                uk.co.caprica.vlcj.factory.MediaPlayerFactory factory = new uk.co.caprica.vlcj.factory.MediaPlayerFactory();
                String version = factory.application().version();
                logger.info("VLC version: {}", version);
                factory.release();
                return true;
            } catch (Throwable t) {
                logger.error("Error loading VLC: {}", t.getMessage(), t);
            }
        }
        
        // If we get here, try to find VLC manually
        logger.info("Trying to locate VLC manually...");
        String[] vlcPaths = {
            "/usr/lib/x86_64-linux-gnu/libvlc.so",
            "/usr/lib/i386-linux-gnu/libvlc.so",
            "/usr/local/lib/libvlc.so",
            "C:\\Program Files\\VideoLAN\\VLC\\libvlc.dll",
            "C:\\Program Files (x86)\\VideoLAN\\VLC\\libvlc.dll"
        };
        
        for (String path : vlcPaths) {
            File file = new File(path);
            if (file.exists()) {
                logger.info("Found VLC library at: {}", path);
                // Set the JNA library path
                System.setProperty("jna.library.path", file.getParent());
                return true;
            }
        }
        
        logger.error("Could not find VLC libraries. Please ensure VLC is installed.");
        return false;
    }
} 