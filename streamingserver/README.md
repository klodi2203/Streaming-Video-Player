# Video Streaming Server

A simple Java-based video streaming server application with a JavaFX GUI.

## Project Structure
- Maven-based Java application
- JavaFX GUI for the user interface

## Features
- Main window titled "Streaming Server"
- Video list displaying available videos with format and resolution information
- Text area for server logs
- "Rescan" button to refresh the video list
- "Convert Missing" button to generate missing video formats and resolutions using FFMPEG

## Video File Format
The application expects video files to be named using the following format:
```
videoName-resolution.format
```
Example: `sample-480p.mp4`

## Supported Formats and Resolutions
### Formats
- MP4 (.mp4)
- AVI (.avi)
- Matroska (.mkv)

### Resolutions
- 240p
- 360p
- 480p
- 720p
- 1080p

## Video Conversion
The application can automatically generate missing format and resolution combinations:
- Only generates versions with resolutions equal to or lower than the source video
- Only generates versions that don't already exist
- Uses FFMPEG for video conversion
- Logs conversion progress in the server logs panel

## Requirements
- Java 11 or higher
- Maven 3.6 or higher
- FFMPEG (must be installed and available in the system PATH)

## How to Run

### Using Maven
```bash
cd streamingserver
mvn clean javafx:run
```

### Building a JAR
```bash
cd streamingserver
mvn clean package
java -jar target/streamingserver-1.0-SNAPSHOT.jar
```

## Development Notes
- Videos are stored in the `videos` directory
- The application automatically creates the videos directory if it doesn't exist
- Video conversion runs in a background thread to keep the UI responsive 