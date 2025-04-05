# Video Streaming Client

A JavaFX application for streaming videos from a video streaming server.

## Features

- Download speed test indicator
- Video format selection (.mp4, .avi, .mkv)
- Available videos display
- Client logging
- Video streaming

## Building the application

This is a Maven-based project. To build it, run:

```
mvn clean package
```

## Running the application

After building, you can run the application with:

```
java -jar target/streamingclient-1.0-SNAPSHOT.jar
```

Alternatively, you can use Maven to run it:

```
mvn javafx:run
```

## Requirements

- Java 11 or higher
- JavaFX 17.0.2
- Maven

## Project Structure

- `src/main/java/com/videostreaming/client`: Application code
- `src/main/java/com/videostreaming/client/model`: Data models
- `src/main/resources`: Resources like CSS styles

## Usage

1. Start the application
2. The client will connect to the server and retrieve the list of available videos
3. Select a video format from the dropdown
4. Click on a video in the list to select it
5. Click "Start Streaming" to begin streaming the selected video 