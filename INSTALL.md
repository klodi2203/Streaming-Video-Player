# Installation Guide

## Prerequisites

### 1. Install JDK 17 or higher
- **Linux (Ubuntu/Debian)**: `sudo apt install openjdk-17-jdk`
- **Windows**: Download and install from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [AdoptOpenJDK](https://adoptopenjdk.net/)

### 2. Install Maven
- **Linux (Ubuntu/Debian)**: `sudo apt install maven`
- **Windows**: Download from [Maven website](https://maven.apache.org/download.cgi) and add to PATH

### 3. Install VLC Media Player
- **Linux (Ubuntu/Debian)**: `sudo apt install vlc`
- **Windows**: Download and install from [VLC website](https://www.videolan.org/vlc/)

### 4. Install FFmpeg
- **Linux (Ubuntu/Debian)**: `sudo apt install ffmpeg`
- **Windows**: Download from [FFmpeg website](https://ffmpeg.org/download.html) and add to PATH

## Building the Project

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/video-streaming-app.git
   cd video-streaming-app
   ```

2. Build the common module first:
   ```
   cd VideoStreamingCommon
   mvn clean install
   ```

3. Build the server:
   ```
   cd ../VideoStreamingServer
   mvn clean package
   ```

4. Build the client:
   ```
   cd ../VideoStreamingClient
   mvn clean package
   ```

## Configuration

1. Create a `videos` directory in the VideoStreamingServer folder:
   ```
   mkdir -p VideoStreamingServer/videos
   ```

2. Add video files to the videos directory (supported formats: mp4, mkv, avi)
   - Video files should follow the naming convention: `videoname-resolution.format`
   - Example: `sample-720p.mp4`, `movie-1080p.mkv`
   - Supported resolutions: 240p, 360p, 480p, 720p, 1080p

## Running the Application

1. Start the server:
   ```
   java -jar VideoStreamingServer/target/VideoStreamingServer-1.0-SNAPSHOT.jar
   ```

2. Start the client:
   ```
   java -jar VideoStreamingClient/target/VideoStreamingClient-1.0-SNAPSHOT.jar
   ```

3. By default, the client will connect to `localhost:8080`. To connect to a different server, modify the `ClientMain.java` file.

## Troubleshooting

### VLC-related issues

- If you encounter VLC-related errors, make sure VLC is installed and its path is correctly set
- For Linux, you may need to set these environment variables:
  ```
  export VLCJ_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu
  export JNA_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu
  ```
- For Windows, ensure VLC is in your PATH or set these variables:
  ```
  set VLCJ_LIBRARY_PATH=C:\Program Files\VideoLAN\VLC
  set JNA_LIBRARY_PATH=C:\Program Files\VideoLAN\VLC
  ```

### Connection issues

- Make sure the server is running before starting the client
- Check that port 8080 is not blocked by a firewall
- If running on different machines, ensure the client can reach the server's IP address

### Video playback issues

- Ensure the video files are properly named and in supported formats
- Check that FFmpeg is correctly installed and in the system PATH
- Verify that the videos directory contains video files