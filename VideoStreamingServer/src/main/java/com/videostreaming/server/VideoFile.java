package com.videostreaming.server;

import java.io.Serializable;

public class VideoFile implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String format;
    private String resolution;
    private String path;

    public VideoFile(String name, String format, String resolution, String path) {
        this.name = name;
        this.format = format;
        this.resolution = resolution;
        this.path = path;
    }

    // Getters
    public String getName() { return name; }
    public String getFormat() { return format; }
    public String getResolution() { return resolution; }
    public String getPath() { return path; }

    @Override
    public String toString() {
        return String.format("%s-%s%s", name, resolution, format);
    }
} 