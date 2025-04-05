package com.videostreaming.client.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Utility class for enhanced logging across the client application
 */
public class LoggingUtil {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Prevent instantiation
    private LoggingUtil() {}
    
    /**
     * Configure a logger with enhanced formatting
     * 
     * @param logger The logger to configure
     */
    public static void configureLogger(Logger logger) {
        // Set up enhanced logging format
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new EnhancedLogFormatter());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
    }
    
    /**
     * Log a message with timestamp and channel information
     * 
     * @param logger The logger to use
     * @param level The log level
     * @param channel The log channel/category
     * @param message The message to log
     */
    public static void log(Logger logger, Level level, String channel, String message) {
        String formattedMessage = formatLogMessage(channel, message);
        logger.log(level, formattedMessage);
    }
    
    /**
     * Log an info message with timestamp and channel information
     * 
     * @param logger The logger to use
     * @param channel The log channel/category
     * @param message The message to log
     */
    public static void info(Logger logger, String channel, String message) {
        log(logger, Level.INFO, channel, message);
    }
    
    /**
     * Log a warning message with timestamp and channel information
     * 
     * @param logger The logger to use
     * @param channel The log channel/category
     * @param message The message to log
     */
    public static void warning(Logger logger, String channel, String message) {
        log(logger, Level.WARNING, channel, message);
    }
    
    /**
     * Log an error message with timestamp and channel information
     * 
     * @param logger The logger to use
     * @param channel The log channel/category
     * @param message The message to log
     */
    public static void error(Logger logger, String channel, String message) {
        log(logger, Level.SEVERE, channel, message);
    }
    
    /**
     * Log a message with timestamp and channel information, and also send to a UI callback
     * 
     * @param logger The logger to use
     * @param level The log level
     * @param channel The log channel/category
     * @param message The message to log
     * @param uiCallback Callback to update UI with the log message
     */
    public static void logWithUi(Logger logger, Level level, String channel, 
                                String message, Consumer<String> uiCallback) {
        String formattedMessage = formatLogMessage(channel, message);
        logger.log(level, formattedMessage);
        
        if (uiCallback != null) {
            uiCallback.accept(getTimestamp() + " [" + channel + "] " + message);
        }
    }
    
    /**
     * Format a log message with channel information
     * 
     * @param channel The log channel/category
     * @param message The message
     * @return The formatted message
     */
    private static String formatLogMessage(String channel, String message) {
        return "[" + channel + "] " + message;
    }
    
    /**
     * Get the current timestamp as a formatted string
     * 
     * @return The formatted timestamp
     */
    private static String getTimestamp() {
        return DATE_FORMAT.format(new Date());
    }
    
    /**
     * Enhanced log formatter that includes the current thread name and timestamp
     */
    private static class EnhancedLogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            
            sb.append("[").append(DATE_FORMAT.format(new Date(record.getMillis()))).append("]");
            sb.append(" [").append(Thread.currentThread().getName()).append("]");
            sb.append(" [").append(record.getLevel()).append("]");
            sb.append(" ").append(record.getMessage());
            sb.append("\n");
            
            return sb.toString();
        }
    }
    
    /**
     * Log a network message with a specific format for clarity
     * 
     * @param logger The logger to use
     * @param direction "SENT" or "RECEIVED"
     * @param endpoint The endpoint (URL, address, etc.)
     * @param messageType The type of message
     * @param details Any additional details
     * @param uiCallback Callback to update UI with the log message
     */
    public static void logNetworkActivity(Logger logger, String direction, String endpoint, 
                                        String messageType, String details, Consumer<String> uiCallback) {
        String message = direction + " " + messageType + " " + (endpoint != null ? "to " + endpoint : "") + 
                (details != null ? " - " + details : "");
        
        logWithUi(logger, Level.INFO, "NETWORK", message, uiCallback);
    }
    
    /**
     * Log a streaming message with a specific format
     * 
     * @param logger The logger to use
     * @param protocol The streaming protocol
     * @param status The streaming status
     * @param details Any additional details
     * @param uiCallback Callback to update UI with the log message
     */
    public static void logStreaming(Logger logger, String protocol, String status, 
                                   String details, Consumer<String> uiCallback) {
        String message = protocol + " stream " + status + (details != null ? " - " + details : "");
        
        logWithUi(logger, Level.INFO, "STREAMING", message, uiCallback);
    }
} 