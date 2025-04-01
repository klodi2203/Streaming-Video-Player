package com.videostreaming.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class StreamingServer {
    private static final Logger logger = LoggerFactory.getLogger(StreamingServer.class);
    private final int port;
    private final VideoProcessor videoProcessor;
    private boolean running;
    private ServerGUI gui;

    public StreamingServer(int port, VideoProcessor videoProcessor) {
        this.port = port;
        this.videoProcessor = videoProcessor;
        this.running = false;
    }

    public void start() {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log(String.format("Server started on port %d", port));
            
            while (running) {
                log("Waiting for client connection...");
                Socket clientSocket = serverSocket.accept();
                log(String.format("Client connected from: %s", clientSocket.getInetAddress()));
                
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            // Create streams without try-with-resources to avoid auto-closing
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            
            // Send available videos list
            out.writeObject(videoProcessor.getAvailableVideos());
            out.flush();
            logger.info("Sent video list to client");

            // Get client's video selection
            String selectedVideo = (String) in.readObject();
            logger.info("Client requested video: {}", selectedVideo);

            // Stream the selected video - this will handle the socket
            videoProcessor.streamVideo(selectedVideo, clientSocket);
            
            // Note: We don't close the socket here - it will be closed by the streaming process

        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error handling client: {}", e.getMessage());
            try {
                clientSocket.close();
                logger.info("Client connection closed due to error");
            } catch (IOException ex) {
                logger.error("Error closing client socket: {}", ex.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
    }

    public void setGUI(ServerGUI gui) {
        this.gui = gui;
    }

    private void log(String message) {
        logger.info(message);
        if (gui != null) {
            gui.addLogMessage(message);
        }
    }
} 