package com.videostreaming.model;

import com.videostreaming.util.VideoFormatUtil;

import java.io.File;

/**
 * Model class representing a video file
 */
public class Video {
    private String name;
    private String resolution;
    private String format;
    private String filePath;

    public Video(String name, String resolution, String format, String filePath) {
        this.name = name;
        this.resolution = resolution;
        this.format = format;
        this.filePath = filePath;
    }

    /**
     * Create a Video object from a file path
     */
    public static Video fromFilePath(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf(File.separator) + 1);
        String baseName = VideoFormatUtil.extractBaseName(fileName);
        String resolution = VideoFormatUtil.extractResolution(fileName);
        String format = VideoFormatUtil.extractFormat(fileName);
        
        Video video = new Video(baseName, resolution, format, filePath);
        return video;
    }

    public String getName() {
        return name;
    }

    public String getResolution() {
        return resolution;
    }

    public String getFormat() {
        return format;
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public String toString() {
        return name + " (" + resolution + ", " + format + ")";
    }
    
    /**
     * Check if this video is the same as another (same base name, format, and resolution)
     */
    public boolean isSameVideo(Video other) {
        return this.name.equals(other.name) && 
               this.format.equals(other.format) && 
               this.resolution.equals(other.resolution);
    }
    
    /**
     * Compare resolutions with another video
     * @param other Other video to compare with
     * @return -1 if this has lower resolution, 0 if equal, 1 if this has higher resolution
     */
    public int compareResolution(Video other) {
        return VideoFormatUtil.compareResolutions(this.resolution, other.resolution);
    }
} 