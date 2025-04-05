package com.videostreaming.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for handling video formats and resolutions
 */
public class VideoFormatUtil {

    // Supported video formats
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("mp4", "avi", "mkv");
    
    // Supported resolutions (in order from lowest to highest)
    private static final List<String> SUPPORTED_RESOLUTIONS = Arrays.asList(
            "240p", "360p", "480p", "720p", "1080p"
    );
    
    /**
     * Get a list of all supported video formats
     * @return List of supported formats (without dot prefix)
     */
    public static List<String> getSupportedFormats() {
        return new ArrayList<>(SUPPORTED_FORMATS);
    }
    
    /**
     * Get a list of all supported video resolutions
     * @return List of supported resolutions
     */
    public static List<String> getSupportedResolutions() {
        return new ArrayList<>(SUPPORTED_RESOLUTIONS);
    }
    
    /**
     * Check if a given format is supported
     * @param format Video format (without dot prefix)
     * @return true if the format is supported, false otherwise
     */
    public static boolean isFormatSupported(String format) {
        return SUPPORTED_FORMATS.contains(format.toLowerCase());
    }
    
    /**
     * Check if a given resolution is supported
     * @param resolution Video resolution
     * @return true if the resolution is supported, false otherwise
     */
    public static boolean isResolutionSupported(String resolution) {
        return SUPPORTED_RESOLUTIONS.contains(resolution.toLowerCase());
    }
    
    /**
     * Get a list of all resolutions up to (and including) the given maximum resolution
     * @param maxResolution Maximum resolution to include
     * @return List of resolutions up to the maximum
     */
    public static List<String> getResolutionsUpTo(String maxResolution) {
        int maxIndex = SUPPORTED_RESOLUTIONS.indexOf(maxResolution);
        if (maxIndex == -1) {
            return new ArrayList<>();
        }
        return new ArrayList<>(SUPPORTED_RESOLUTIONS.subList(0, maxIndex + 1));
    }
    
    /**
     * Get all possible format-resolution combinations up to a given resolution
     * @param maxResolution Maximum resolution to include
     * @return List of format-resolution pairs in the format "format:resolution"
     */
    public static List<String> getAllCombinations(String maxResolution) {
        List<String> combinations = new ArrayList<>();
        List<String> resolutions = getResolutionsUpTo(maxResolution);
        
        for (String format : SUPPORTED_FORMATS) {
            for (String resolution : resolutions) {
                combinations.add(format + ":" + resolution);
            }
        }
        
        return combinations;
    }
    
    /**
     * Extract the base name from a filename
     * @param filename Filename in format "basename-resolution.format"
     * @return Base name of the video
     */
    public static String extractBaseName(String filename) {
        int dashIndex = filename.lastIndexOf('-');
        if (dashIndex == -1) {
            return filename;
        }
        return filename.substring(0, dashIndex);
    }
    
    /**
     * Extract the resolution from a filename
     * @param filename Filename in format "basename-resolution.format"
     * @return Resolution of the video
     */
    public static String extractResolution(String filename) {
        int dashIndex = filename.lastIndexOf('-');
        int dotIndex = filename.lastIndexOf('.');
        
        if (dashIndex == -1 || dotIndex == -1 || dashIndex >= dotIndex) {
            return "";
        }
        
        return filename.substring(dashIndex + 1, dotIndex);
    }
    
    /**
     * Extract the format from a filename
     * @param filename Filename in format "basename-resolution.format"
     * @return Format of the video
     */
    public static String extractFormat(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }
    
    /**
     * Compare two resolutions
     * @param res1 First resolution (e.g., "480p")
     * @param res2 Second resolution (e.g., "720p")
     * @return -1 if res1 < res2, 0 if res1 == res2, 1 if res1 > res2
     */
    public static int compareResolutions(String res1, String res2) {
        int val1 = Integer.parseInt(res1.replace("p", ""));
        int val2 = Integer.parseInt(res2.replace("p", ""));
        return Integer.compare(val1, val2);
    }
    
    /**
     * Generate a list of missing format-resolution combinations for a given video
     * @param filename Filename in format "basename-resolution.format"
     * @return List of missing combinations in the format "format:resolution"
     */
    public static List<String> getMissingCombinations(String filename) {
        String baseName = extractBaseName(filename);
        String existingResolution = extractResolution(filename);
        String existingFormat = extractFormat(filename);
        
        // Return empty list if the file doesn't match expected pattern
        if (baseName.isEmpty() || existingResolution.isEmpty() || existingFormat.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Return empty list if resolution or format is not supported
        if (!isResolutionSupported(existingResolution) || !isFormatSupported(existingFormat)) {
            return new ArrayList<>();
        }
        
        // Get all possible combinations up to this resolution
        List<String> allCombinations = getAllCombinations(existingResolution);
        
        // Create a list of existing combinations (assuming only the current one exists)
        String existingCombination = existingFormat + ":" + existingResolution;
        List<String> missingCombinations = allCombinations.stream()
                .filter(combination -> !combination.equals(existingCombination))
                .collect(Collectors.toList());
        
        return missingCombinations;
    }
    
    /**
     * Generate a full filename with a specific format and resolution
     * @param baseName Base name of the video
     * @param format Format of the video
     * @param resolution Resolution of the video
     * @return Full filename in format "basename-resolution.format"
     */
    public static String generateFilename(String baseName, String format, String resolution) {
        return baseName + "-" + resolution + "." + format;
    }
    
    /**
     * Generate a complete filepath with a specific format and resolution
     * @param baseName Base name of the video
     * @param format Format of the video
     * @param resolution Resolution of the video
     * @param directory Directory where the video is stored
     * @return Full filepath for the video
     */
    public static String generateFilepath(String baseName, String format, String resolution, String directory) {
        String filename = generateFilename(baseName, format, resolution);
        return directory + "/" + filename;
    }
} 