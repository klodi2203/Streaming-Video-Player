package com.videostreaming.client;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectionSpeedTest {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionSpeedTest.class);
    
    // Multiple test file URLs in case one fails
    private static final String[] TEST_FILE_URLS = {
        "https://speed.cloudflare.com/__down?bytes=25000000", // Cloudflare speed test (25MB)
        "https://speedtest.tele2.net/10MB.zip",               // Tele2 speed test (10MB)
        "https://proof.ovh.net/files/10Mb.dat",               // OVH speed test (10MB)
        "http://ipv4.ikoula.testdebit.info/10M.iso"           // Original test (10MB)
    };
    
    private static final int TEST_DURATION_SECONDS = 5;
    
    private double downloadSpeed; // in Mbps
    
    public double testConnectionSpeed() {
        logger.info("Starting connection speed test...");
        
        // Try each test URL until one works
        for (String testUrl : TEST_FILE_URLS) {
            if (runSpeedTest(testUrl)) {
                return downloadSpeed;
            }
            // If we get here, the test failed, try the next URL
            logger.info("Trying alternative speed test server...");
        }
        
        // If all tests failed
        logger.error("All speed tests failed");
        downloadSpeed = -1;
        return downloadSpeed;
    }
    
    private boolean runSpeedTest(String testUrl) {
        logger.info("Testing with URL: {}", testUrl);
        updateProgress(0, "Connecting to test server: " + testUrl);
        
        // Create a countdown latch to wait for test completion
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        
        // Initialize speed test socket
        SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        
        // Add a listener to get download speed
        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(SpeedTestReport report) {
                // Convert from bit/s to Mbit/s
                downloadSpeed = report.getTransferRateBit().doubleValue() / 1_000_000;
                String message = String.format("Test completed: %.2f Mbps", downloadSpeed);
                logger.info("Speed test completed. Download speed: {} Mbps", String.format("%.2f", downloadSpeed));
                updateProgress(100, message);
                success[0] = true;
                latch.countDown();
            }
            
            @Override
            public void onProgress(float percent, SpeedTestReport report) {
                // Update progress more frequently
                int progressPercent = Math.round(percent);
                double currentSpeed = report.getTransferRateBit().doubleValue() / 1_000_000;
                String message = String.format("Testing: %.2f Mbps", currentSpeed);
                
                updateProgress(progressPercent, message);
                
                // Log progress every 20%
                if (progressPercent % 20 == 0) {
                    logger.info("Speed test progress: {}%, current speed: {} Mbps", 
                        progressPercent, String.format("%.2f", currentSpeed));
                }
            }
            
            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage) {
                logger.error("Speed test error: {} - {}", speedTestError, errorMessage);
                updateProgress(0, "Error: " + errorMessage);
                success[0] = false;
                latch.countDown();
            }
        });
        
        try {
            // Start download test
            speedTestSocket.startDownload(testUrl);
            
            // Stop the test after TEST_DURATION_SECONDS
            new Thread(() -> {
                try {
                    Thread.sleep(TEST_DURATION_SECONDS * 1000);
                    if (!success[0]) {
                        updateProgress(0, "Test timed out, stopping...");
                        speedTestSocket.forceStopTask();
                    }
                } catch (InterruptedException e) {
                    logger.error("Speed test interrupted", e);
                }
            }).start();
            
            // Wait for test to complete
            latch.await(TEST_DURATION_SECONDS + 2, TimeUnit.SECONDS);
            
            return success[0];
        } catch (Exception e) {
            logger.error("Exception during speed test: {}", e.getMessage());
            updateProgress(0, "Error: " + e.getMessage());
            return false;
        }
    }
    
    public double getDownloadSpeed() {
        return downloadSpeed;
    }
    
    public String getRecommendedResolution() {
        if (downloadSpeed < 0) {
            return "480p"; // Default if test failed
        } else if (downloadSpeed < 2) {
            return "240p"; // For very slow connections
        } else if (downloadSpeed < 5) {
            return "360p"; // For slow connections
        } else if (downloadSpeed < 8) {
            return "480p"; // For medium connections
        } else if (downloadSpeed < 12) {
            return "720p"; // For fast connections
        } else {
            return "1080p"; // For very fast connections
        }
    }
    
    public void updateProgress(int percent, String message) {
        // This is a hook method that will be overridden
        // Default implementation does nothing
    }
} 