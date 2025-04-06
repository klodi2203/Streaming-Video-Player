package com.videostreaming.api;

import com.videostreaming.model.Video;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Server for streaming videos over TCP, UDP, and RTP/UDP protocols
 * Supports multiple clients concurrently through multi-threading
 */
public class StreamingServer {
    
    private static final Logger LOGGER = Logger.getLogger(StreamingServer.class.getName());
    static {
        // Set up enhanced logging format
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new EnhancedLogFormatter());
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.ALL);
    }
    
    // Port configuration
    private static final int TCP_PORT = 8081;
    private static final int UDP_PORT = 8082;
    private static final int RTP_PORT = 8083;
    
    // Buffer size for reading video data
    private static final int BUFFER_SIZE = 1024 * 16; // 16KB buffer - reduced from 64KB
    private static final int RTP_MAX_PAYLOAD = 1400; // Maximum RTP payload size for UDP
    
    // Thread pool for handling multiple streaming requests
    private final ExecutorService executorService;
    
    // Servers for different protocols
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private DatagramSocket rtpServer;
    
    // Flags to control the server
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // Statistics
    private final AtomicInteger clientsConnected = new AtomicInteger(0);
    private final AtomicInteger activeStreams = new AtomicInteger(0);
    private final AtomicInteger totalClientCount = new AtomicInteger(0);
    private long bytesTransferred = 0;
    
    // Keep track of active client handlers
    private final Map<InetAddress, ClientHandler> activeClients = new ConcurrentHashMap<>();
    
    // Keep track of registered clients by their client IDs
    private final Map<String, InetAddress> registeredClientIds = new ConcurrentHashMap<>();
    
    /**
     * Create a new streaming server
     */
    public StreamingServer() {
        // Use a cached thread pool that creates threads as needed
        this.executorService = Executors.newCachedThreadPool();
        logWithTimestamp("Streaming server instance created");
    }
    
    /**
     * Start the streaming server for all protocols
     */
    public void start() {
        if (isRunning.get()) {
            logWithTimestamp("Server already running, ignoring start request");
            return;
        }
        
        logWithTimestamp("Starting streaming server on all protocols...");
        isRunning.set(true);
        
        try {
            // Start TCP server
            tcpServer = new ServerSocket(TCP_PORT);
            logWithTimestamp("TCP streaming server started - LISTENING on port " + TCP_PORT);
            logWithTimestamp("Waiting for TCP client connections...");
            executorService.submit(this::handleTcpConnections);
            
            // Start UDP server
            udpServer = new DatagramSocket(UDP_PORT);
            logWithTimestamp("UDP streaming server started - LISTENING on port " + UDP_PORT);
            
            // Start RTP/UDP server
            rtpServer = new DatagramSocket(RTP_PORT);
            logWithTimestamp("RTP/UDP streaming server started - LISTENING on port " + RTP_PORT);
            
            logWithTimestamp("Streaming server initialization complete - ready to serve clients");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start streaming server", e);
            stop();
        }
    }
    
    /**
     * Stop the streaming server
     */
    public void stop() {
        if (!isRunning.get()) {
            logWithTimestamp("Server not running, ignoring stop request");
            return;
        }
        
        logWithTimestamp("Stopping streaming server...");
        isRunning.set(false);
        
        // Disconnect all clients
        logWithTimestamp("Disconnecting all clients...");
        for (ClientHandler client : activeClients.values()) {
            try {
                client.disconnect();
                logWithTimestamp("Disconnected client: " + client.getClientAddress());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error disconnecting client", e);
            }
        }
        
        // Close TCP server
        if (tcpServer != null && !tcpServer.isClosed()) {
            try {
                tcpServer.close();
                logWithTimestamp("TCP server socket closed");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing TCP server", e);
            }
        }
        
        // Close UDP server
        if (udpServer != null && !udpServer.isClosed()) {
            udpServer.close();
            logWithTimestamp("UDP server socket closed");
        }
        
        // Close RTP server
        if (rtpServer != null && !rtpServer.isClosed()) {
            rtpServer.close();
            logWithTimestamp("RTP server socket closed");
        }
        
        // Shutdown thread pool
        executorService.shutdownNow();
        logWithTimestamp("Thread pool shutdown initiated");
        
        // Log final statistics
        logWithTimestamp("===== SERVER STATISTICS =====");
        logWithTimestamp("Total clients connected: " + totalClientCount.get());
        logWithTimestamp("Total bytes transferred: " + formatSize(bytesTransferred));
        logWithTimestamp("===========================");
        
        logWithTimestamp("Streaming server stopped");
    }
    
    /**
     * Handle TCP connections in a loop
     */
    private void handleTcpConnections() {
        while (isRunning.get()) {
            try {
                Socket clientSocket = tcpServer.accept();
                InetAddress clientAddress = clientSocket.getInetAddress();
                logWithTimestamp("NEW TCP CONNECTION from " + clientAddress + ":" + 
                        clientSocket.getPort() + " [Client #" + (totalClientCount.get() + 1) + "]");
                
                // Get or create a client handler for this address
                ClientHandler clientHandler = activeClients.computeIfAbsent(clientAddress, addr -> {
                    ClientHandler handler = new ClientHandler(addr);
                    // Start the client handler in a separate thread
                    executorService.submit(handler);
                    return handler;
                });
                
                // Set the TCP socket for this client
                clientHandler.setTcpSocket(clientSocket);
                
                // Handle this connection in a separate thread
                executorService.submit(() -> handleTcpClient(clientSocket));
            } catch (IOException e) {
                if (isRunning.get()) {
                    LOGGER.log(Level.SEVERE, "Error accepting TCP connection", e);
                }
            }
        }
    }
    
    /**
     * Handle a TCP client connection
     * @param clientSocket The client socket
     */
    private void handleTcpClient(Socket clientSocket) {
        try {
            InetAddress clientAddress = clientSocket.getInetAddress();
            logWithTimestamp("Handling client " + clientAddress + " on TCP thread " + 
                    Thread.currentThread().getName());
            
            // For now, we'll simulate by sending a message
            // In a real implementation, we would stream the video data
            OutputStream outputStream = clientSocket.getOutputStream();
            String message = "TCP streaming connected - Server ready\n";
            outputStream.write(message.getBytes());
            outputStream.flush();
            logWithTimestamp("Sent handshake message to client");
            
            // Keep the connection open for a while
            Thread.sleep(2000);
            
            // Note: Don't close the socket here since it's managed by the ClientHandler
            logWithTimestamp("TCP connection with " + clientAddress + " processed");
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error handling TCP client", e);
        }
    }
    
    /**
     * Stream a video over TCP
     * @param video The video to stream
     * @param clientAddress The client address
     */
    public void streamVideoOverTcp(Video video, InetAddress clientAddress) {
        // Get or create a client handler for this client
        ClientHandler clientHandler = activeClients.computeIfAbsent(clientAddress, addr -> {
            ClientHandler handler = new ClientHandler(addr);
            // Start the client handler in a separate thread
            executorService.submit(handler);
            return handler;
        });
        
        // Start streaming using the client handler
        clientHandler.streamTcp(video);
    }
    
    /**
     * Stream a video over UDP
     * @param video The video to stream
     * @param clientAddress The client address
     */
    public void streamVideoOverUdp(Video video, InetAddress clientAddress) {
        // Get or create a client handler for this client
        ClientHandler clientHandler = activeClients.computeIfAbsent(clientAddress, addr -> {
            ClientHandler handler = new ClientHandler(addr);
            // Start the client handler in a separate thread
            executorService.submit(handler);
            return handler;
        });
        
        // Start streaming using the client handler
        clientHandler.streamUdp(video);
    }
    
    /**
     * Stream a video over RTP/UDP
     * @param video The video to stream
     * @param clientAddress The client address
     */
    public void streamVideoOverRtp(Video video, InetAddress clientAddress) {
        // Get or create a client handler for this client
        ClientHandler clientHandler = activeClients.computeIfAbsent(clientAddress, addr -> {
            ClientHandler handler = new ClientHandler(addr);
            // Start the client handler in a separate thread
            executorService.submit(handler);
            return handler;
        });
        
        // Start streaming using the client handler
        clientHandler.streamRtp(video);
    }
    
    /**
     * Start streaming a video based on the protocol
     * @param video The video to stream
     * @param protocol The protocol to use
     * @param clientAddress The client address
     * @throws IllegalArgumentException If the protocol is not supported
     */
    public void streamVideo(Video video, String protocol, InetAddress clientAddress) {
        logWithTimestamp("Received streaming request for " + video.getName() + 
                " using " + protocol + " protocol from " + clientAddress);
        
        if (protocol.equalsIgnoreCase("TCP")) {
            logWithTimestamp("Using TCP protocol for streaming");
            streamVideoOverTcp(video, clientAddress);
        } else if (protocol.equalsIgnoreCase("UDP")) {
            logWithTimestamp("Using UDP protocol for streaming");
            streamVideoOverUdp(video, clientAddress);
        } else if (protocol.equalsIgnoreCase("RTP/UDP")) {
            logWithTimestamp("Using RTP/UDP protocol for streaming");
            streamVideoOverRtp(video, clientAddress);
        } else {
            String errorMsg = "Unsupported protocol requested: " + protocol;
            logWithTimestamp("ERROR: " + errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }
    
    /**
     * Check if the server is running
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Get the number of active clients
     * @return The number of active clients
     */
    public int getActiveClientsCount() {
        return clientsConnected.get();
    }
    
    /**
     * Get the number of active streams
     * @return The number of active streams
     */
    public int getActiveStreamsCount() {
        return activeStreams.get();
    }
    
    /**
     * Get the total number of clients that have connected
     * @return The total number of clients
     */
    public int getTotalClientCount() {
        return totalClientCount.get();
    }
    
    /**
     * Get server status information
     * @return A string containing server status information
     */
    public String getServerStatus() {
        StringBuilder status = new StringBuilder();
        status.append("SERVER STATUS\n");
        status.append("Server Running: ").append(isRunning.get()).append("\n");
        status.append("Active Clients: ").append(clientsConnected.get()).append("\n");
        status.append("Active Streams: ").append(activeStreams.get()).append("\n");
        status.append("Total Clients: ").append(totalClientCount.get()).append("\n");
        status.append("Total Bytes Transferred: ").append(formatSize(bytesTransferred)).append("\n");
        status.append("Active Client Details: \n");
        
        for (ClientHandler client : activeClients.values()) {
            status.append("  - Client: ").append(client.getClientAddress())
                  .append(", Active: ").append(client.isActive())
                  .append("\n");
        }
        
        return status.toString();
    }
    
    /**
     * Format a size in bytes to a human-readable string
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * Log a message with a timestamp
     */
    private void logWithTimestamp(String message) {
        LOGGER.info(message);
    }
    
    /**
     * Enhanced log formatter that includes the current thread name
     */
    private static class EnhancedLogFormatter extends Formatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(dateFormat.format(new Date(record.getMillis()))).append("]");
            sb.append(" [").append(Thread.currentThread().getName()).append("]");
            sb.append(" [").append(record.getLevel()).append("]");
            sb.append(" [StreamingServer] ");
            sb.append(record.getMessage());
            sb.append("\n");
            return sb.toString();
        }
    }
    
    /**
     * Client handler class to manage a single client connection
     * Each client runs in its own thread and manages its own resources
     */
    private class ClientHandler implements Runnable {
        private final InetAddress clientAddress;
        private final int clientId;
        private final AtomicBoolean isActive = new AtomicBoolean(false);
        private Socket tcpSocket;
        private long clientBytesTransferred = 0;
        private Video currentVideo;
        private String currentProtocol;
        
        /**
         * Create a new client handler
         * @param clientAddress The client's IP address
         */
        public ClientHandler(InetAddress clientAddress) {
            this.clientAddress = clientAddress;
            this.clientId = totalClientCount.incrementAndGet();
            logWithTimestamp("Created ClientHandler for " + clientAddress + " [Client #" + clientId + "]");
        }
        
        @Override
        public void run() {
            isActive.set(true);
            logWithTimestamp("ClientHandler started for " + clientAddress + " [Client #" + clientId + "]");
            clientsConnected.incrementAndGet();
            
            try {
                // This thread will be responsible for handling the client connection
                // For TCP clients, it will actually perform the streaming
                // For UDP and RTP, it will set up the streaming but another thread will handle it
                
                while (isActive.get() && isRunning.get()) {
                    // Wait for streaming requests or commands from the client
                    if (tcpSocket != null && tcpSocket.isConnected()) {
                        // Process client commands if any
                        // ...
                        
                        // In this implementation, we'll just sleep briefly
                        // since actual streaming is handled by separate methods
                        Thread.sleep(500);
                    } else {
                        // No active TCP connection, so just wait
                        Thread.sleep(1000);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logWithTimestamp("ClientHandler for " + clientAddress + " was interrupted");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in ClientHandler for " + clientAddress, e);
            } finally {
                cleanup();
            }
        }
        
        /**
         * Stream a video to this client using TCP
         * @param video The video to stream
         */
        public void streamTcp(Video video) {
            if (video == null) {
                logWithTimestamp("ERROR: Cannot stream null video to " + clientAddress);
                return;
            }
            
            this.currentVideo = video;
            this.currentProtocol = "TCP";
            activeStreams.incrementAndGet();
            
            executorService.submit(() -> {
                try {
                    logWithTimestamp("STREAMING REQUEST: TCP stream of '" + video.getName() + 
                            "' (" + video.getResolution() + ", " + video.getFormat() + ") to " + clientAddress);
                    
                    // Open the video file
                    File videoFile = new File(video.getFilePath());
                    if (!videoFile.exists()) {
                        logWithTimestamp("ERROR: Video file not found: " + video.getFilePath());
                        return;
                    }
                    
                    logWithTimestamp("Video file found: " + videoFile.getAbsolutePath() + 
                            " (" + formatSize(videoFile.length()) + ")");
                    
                    logWithTimestamp("Video file is available at: " + videoFile.getAbsolutePath());
                    logWithTimestamp("TCP stream is ready for client access");
                    
                    // For real streaming implementation:
                    /* 
                    // Wait for a client to connect on the TCP port
                    logWithTimestamp("Waiting for client " + clientAddress + " to connect for TCP streaming...");
                    Socket clientSocket = tcpServer.accept();
                    if (!clientSocket.getInetAddress().equals(clientAddress)) {
                        logWithTimestamp("WARNING: Unexpected client connected: " + clientSocket.getInetAddress() + 
                                " (expected " + clientAddress + ")");
                        clientSocket.close();
                        return;
                    }
                    
                    this.tcpSocket = clientSocket;
                    logWithTimestamp("Client " + clientAddress + " connected - starting TCP stream");
                    
                    // Stream the video data
                    try (FileInputStream fileInputStream = new FileInputStream(videoFile);
                         OutputStream outputStream = clientSocket.getOutputStream()) {
                        
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalBytesStreamed = 0;
                        long startTime = System.currentTimeMillis();
                        
                        while ((bytesRead = fileInputStream.read(buffer)) != -1 && isActive.get()) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                            totalBytesStreamed += bytesRead;
                            clientBytesTransferred += bytesRead;
                            
                            // Log progress every 5MB
                            if (totalBytesStreamed % (5 * 1024 * 1024) < BUFFER_SIZE) {
                                logWithTimestamp("TCP Stream progress: " + formatSize(totalBytesStreamed) + 
                                        " sent to " + clientAddress);
                            }
                        }
                        
                        long duration = System.currentTimeMillis() - startTime;
                        double speedMbps = (totalBytesStreamed * 8.0) / (duration / 1000.0) / 1_000_000;
                        
                        logWithTimestamp("TCP stream completed - sent " + formatSize(totalBytesStreamed) + 
                                " in " + (duration / 1000) + " seconds (" + String.format("%.2f", speedMbps) + " Mbps)");
                    }
                    
                    logWithTimestamp("Closing TCP connection with " + clientAddress);
                    clientSocket.close();
                    this.tcpSocket = null;
                    */
                    
                    // Sleep a bit to simulate streaming time
                    Thread.sleep(2000);
                    logWithTimestamp("TCP stream to " + clientAddress + " completed (simulation)");
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error streaming video over TCP to " + clientAddress, e);
                } finally {
                    activeStreams.decrementAndGet();
                }
            });
        }
        
        /**
         * Stream a video to this client using UDP
         * @param video The video to stream
         */
        public void streamUdp(Video video) {
            if (video == null) {
                logWithTimestamp("ERROR: Cannot stream null video to " + clientAddress);
                return;
            }
            
            this.currentVideo = video;
            this.currentProtocol = "UDP";
            activeStreams.incrementAndGet();
            
            executorService.submit(() -> {
                try {
                    logWithTimestamp("STREAMING REQUEST: UDP stream of '" + video.getName() + 
                            "' (" + video.getResolution() + ", " + video.getFormat() + ") to " + clientAddress);
                    
                    // Open the video file
                    File videoFile = new File(video.getFilePath());
                    if (!videoFile.exists()) {
                        logWithTimestamp("ERROR: Video file not found: " + video.getFilePath());
                        return;
                    }
                    
                    logWithTimestamp("Video file found: " + videoFile.getAbsolutePath() + 
                            " (" + formatSize(videoFile.length()) + ")");
                    logWithTimestamp("Starting UDP stream to " + clientAddress + ":" + UDP_PORT);
                    
                    // Send data packets
                    try (FileInputStream fileInputStream = new FileInputStream(videoFile)) {
                        
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long packetCount = 0;
                        long totalBytesStreamed = 0;
                        long startTime = System.currentTimeMillis();
                        
                        while ((bytesRead = fileInputStream.read(buffer)) != -1 && isActive.get()) {
                            try {
                                DatagramPacket packet = new DatagramPacket(
                                        buffer, bytesRead, clientAddress, UDP_PORT);
                                udpServer.send(packet);
                                packetCount++;
                                totalBytesStreamed += bytesRead;
                                clientBytesTransferred += bytesRead;
                                
                                // Log progress every 1000 packets
                                if (packetCount % 1000 == 0) {
                                    logWithTimestamp("UDP Stream progress: " + packetCount + " packets (" + 
                                            formatSize(totalBytesStreamed) + ") sent to " + clientAddress);
                                }
                                
                                // Simulate real-time speed by adding a small delay
                                Thread.sleep(50);
                            } catch (IOException e) {
                                logWithTimestamp("WARNING: Failed to send UDP packet: " + e.getMessage());
                            }
                        }
                        
                        long duration = System.currentTimeMillis() - startTime;
                        double speedMbps = (totalBytesStreamed * 8.0) / (duration / 1000.0) / 1_000_000;
                        
                        logWithTimestamp("UDP stream completed - sent " + packetCount + " packets (" + 
                                formatSize(totalBytesStreamed) + ") in " + (duration / 1000) + 
                                " seconds (" + String.format("%.2f", speedMbps) + " Mbps)");
                    }
                    
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "Error streaming video over UDP to " + clientAddress, e);
                } finally {
                    activeStreams.decrementAndGet();
                }
            });
        }
        
        /**
         * Stream a video to this client using RTP/UDP
         * @param video The video to stream
         */
        public void streamRtp(Video video) {
            if (video == null) {
                logWithTimestamp("ERROR: Cannot stream null video to " + clientAddress);
                return;
            }
            
            this.currentVideo = video;
            this.currentProtocol = "RTP/UDP";
            activeStreams.incrementAndGet();
            
            executorService.submit(() -> {
                try {
                    logWithTimestamp("STREAMING REQUEST: RTP/UDP stream of '" + video.getName() + 
                            "' (" + video.getResolution() + ", " + video.getFormat() + ") to " + clientAddress);
                    
                    // Open the video file
                    File videoFile = new File(video.getFilePath());
                    if (!videoFile.exists()) {
                        logWithTimestamp("ERROR: Video file not found: " + video.getFilePath());
                        return;
                    }
                    
                    logWithTimestamp("Video file found: " + videoFile.getAbsolutePath() + 
                            " (" + formatSize(videoFile.length()) + ")");
                    logWithTimestamp("Starting RTP/UDP stream to " + clientAddress + ":" + RTP_PORT);
                    
                    // Create RTP header
                    byte[] rtpHeader = new byte[12];
                    // Version 2, no padding, no extension, no CSRC
                    rtpHeader[0] = (byte) 0x80;
                    // Marker bit and payload type (e.g., 96 for dynamic)
                    rtpHeader[1] = (byte) 0x60;
                    
                    int sequenceNumber = 0;
                    long timestamp = System.currentTimeMillis();
                    
                    // Send data packets with RTP headers
                    try (FileInputStream fileInputStream = new FileInputStream(videoFile)) {
                        
                        byte[] buffer = new byte[RTP_MAX_PAYLOAD];
                        int bytesRead;
                        long packetCount = 0;
                        long totalBytesStreamed = 0;
                        long startTime = System.currentTimeMillis();
                        
                        while ((bytesRead = fileInputStream.read(buffer)) != -1 && isActive.get()) {
                            // Update sequence number (16 bits)
                            rtpHeader[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
                            rtpHeader[3] = (byte) (sequenceNumber & 0xFF);
                            
                            // Update timestamp (32 bits)
                            timestamp += 3600; // 90kHz clock rate
                            rtpHeader[4] = (byte) ((timestamp >> 24) & 0xFF);
                            rtpHeader[5] = (byte) ((timestamp >> 16) & 0xFF);
                            rtpHeader[6] = (byte) ((timestamp >> 8) & 0xFF);
                            rtpHeader[7] = (byte) (timestamp & 0xFF);
                            
                            // SSRC identifier (32 bits) - fixed
                            rtpHeader[8] = 0x12;
                            rtpHeader[9] = 0x34;
                            rtpHeader[10] = 0x56;
                            rtpHeader[11] = 0x78;
                            
                            // Combine RTP header with video data
                            byte[] packetData = new byte[rtpHeader.length + bytesRead];
                            System.arraycopy(rtpHeader, 0, packetData, 0, rtpHeader.length);
                            System.arraycopy(buffer, 0, packetData, rtpHeader.length, bytesRead);
                            
                            try {
                                DatagramPacket packet = new DatagramPacket(
                                        packetData, packetData.length, clientAddress, RTP_PORT);
                                rtpServer.send(packet);
                                packetCount++;
                                totalBytesStreamed += bytesRead;
                                clientBytesTransferred += bytesRead;
                                
                                // Log progress every 1000 packets
                                if (packetCount % 1000 == 0) {
                                    logWithTimestamp("RTP Stream progress: " + packetCount + " packets (" + 
                                            formatSize(totalBytesStreamed) + ") sent to " + clientAddress);
                                }
                            } catch (IOException e) {
                                logWithTimestamp("WARNING: Failed to send RTP packet: " + e.getMessage());
                                // Continue to next packet
                            }
                            
                            // Increment sequence number
                            sequenceNumber = (sequenceNumber + 1) & 0xFFFF;
                            
                            // Simulate real-time speed by adding a small delay
                            Thread.sleep(40);
                        }
                        
                        long duration = System.currentTimeMillis() - startTime;
                        double speedMbps = (totalBytesStreamed * 8.0) / (duration / 1000.0) / 1_000_000;
                        
                        logWithTimestamp("RTP stream completed - sent " + packetCount + " packets (" + 
                                formatSize(totalBytesStreamed) + ") in " + (duration / 1000) + 
                                " seconds (" + String.format("%.2f", speedMbps) + " Mbps)");
                    }
                    
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "Error streaming video over RTP to " + clientAddress, e);
                } finally {
                    activeStreams.decrementAndGet();
                }
            });
        }
        
        /**
         * Clean up resources used by this client handler
         */
        public void cleanup() {
            isActive.set(false);
            
            // Close TCP socket if open
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                try {
                    tcpSocket.close();
                    logWithTimestamp("Closed TCP socket for client " + clientAddress);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing TCP socket for client " + clientAddress, e);
                }
                tcpSocket = null;
            }
            
            // Update statistics
            bytesTransferred += clientBytesTransferred;
            clientsConnected.decrementAndGet();
            
            // Remove from active clients map
            activeClients.remove(clientAddress);
            
            logWithTimestamp("ClientHandler for " + clientAddress + " [Client #" + clientId + 
                    "] terminated - transferred " + formatSize(clientBytesTransferred));
        }
        
        /**
         * Get the client's address
         */
        public InetAddress getClientAddress() {
            return clientAddress;
        }
        
        /**
         * Set the TCP socket for this client
         */
        public void setTcpSocket(Socket socket) {
            this.tcpSocket = socket;
        }
        
        /**
         * Check if the client is active
         */
        public boolean isActive() {
            return isActive.get();
        }
        
        /**
         * Disconnect the client
         */
        public void disconnect() {
            isActive.set(false);
        }
    }
    
    /**
     * Register a new client with the streaming server
     * @param clientAddress The client's IP address
     * @param hostname The client's hostname
     * @return A unique client ID
     */
    public String registerClient(InetAddress clientAddress, String hostname) {
        // Generate a unique client ID
        String clientId = "client-" + System.currentTimeMillis() + "-" + 
                Math.abs(clientAddress.hashCode() % 1000);
        
        // Register the client by storing their IP address
        registeredClientIds.put(clientId, clientAddress);
        
        // Increment client count
        totalClientCount.incrementAndGet();
        clientsConnected.incrementAndGet();
        
        logWithTimestamp("NEW CLIENT REGISTERED - " + clientId + " from " + clientAddress + 
                " (" + hostname + ") - Active clients: " + clientsConnected.get());
        
        return clientId;
    }
    
    /**
     * Unregister a client from the streaming server
     * @param clientId The client ID to unregister
     * @param clientAddress The client's IP address for verification
     */
    public void unregisterClient(String clientId, InetAddress clientAddress) {
        // Check if this client ID exists
        InetAddress registeredAddress = registeredClientIds.get(clientId);
        if (registeredAddress == null) {
            logWithTimestamp("WARNING: Attempted to unregister unknown client ID: " + clientId);
            return;
        }
        
        // Verify the client address matches for security
        if (!registeredAddress.equals(clientAddress)) {
            logWithTimestamp("WARNING: Client address mismatch during unregistration. Expected " + 
                    registeredAddress + " but received " + clientAddress);
            return;
        }
        
        // Remove the client ID
        registeredClientIds.remove(clientId);
        
        // Decrement client count
        clientsConnected.decrementAndGet();
        
        // Check if this client has any active streams and terminate them
        ClientHandler handler = activeClients.get(clientAddress);
        if (handler != null) {
            logWithTimestamp("Terminating active streams for disconnecting client: " + clientId);
            handler.disconnect();
        }
        
        logWithTimestamp("CLIENT UNREGISTERED - " + clientId + " from " + clientAddress + 
                " - Active clients: " + clientsConnected.get());
    }
} 