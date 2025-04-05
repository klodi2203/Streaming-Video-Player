package com.videostreaming.client;

/**
 * Launcher class that serves as the entry point for the application when packaged as a JAR.
 * This class is needed because JavaFX modules require special handling when packaged.
 */
public class Launcher {
    public static void main(String[] args) {
        ClientApp.main(args);
    }
} 