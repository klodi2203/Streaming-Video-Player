package com.videostreaming.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoStreamingServer {
    private static final Logger logger = LoggerFactory.getLogger(VideoStreamingServer.class);
    
    private final int port;
    private final String videosDirectory;
    private ServerSocket serverSocket;
    private VideoProcessor videoProcessor;
    private boolean running;
    
    public VideoStreamingServer(int port, String videosDirectory) {
        this.port = port;
        this.videosDirectory = videosDirectory;
        // Create VideoProcessor with default constructor (which scans immediately)
        this.videoProcessor = new VideoProcessor();
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        
        logger.info("Video Streaming Server started on port {}", port);
        logger.info("Videos directory: {}", videosDirectory);
        
        // Accept client connections in a loop
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connection from: {}", clientSocket.getInetAddress());
                
                // Create a new thread to handle this client
                ClientHandler clientHandler = new ClientHandler(clientSocket, videoProcessor);
                new Thread(clientHandler).start();
                
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        logger.info("Server stopped");
    }
} 