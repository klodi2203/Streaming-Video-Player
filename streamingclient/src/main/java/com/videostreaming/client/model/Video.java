package com.videostreaming.client.model;

/**
 * Model class representing a video in the streaming client.
 */
public class Video {
    private String name;
    private String resolution;
    private String format;
    private String url;

    public Video(String name, String resolution, String format, String url) {
        this.name = name;
        this.resolution = resolution;
        this.format = format;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return name + " (" + resolution + ", " + format + ")";
    }
} 