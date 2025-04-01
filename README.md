# Video Streaming Application

A Java-based client-server video streaming application that adapts video quality based on connection speed.

## Features

- Automatic bandwidth detection
- Video quality adaptation based on connection speed
- Format selection (mp4, mkv, avi)
- Video playback with basic controls
- Server-side video management

## Requirements

### Software Requirements
- JDK 17 or higher
- Maven 3.6 or higher
- VLC Media Player 3.0+ (for client)
- FFmpeg (for server)

### System Requirements
- At least 4GB RAM
- 100MB free disk space (plus space for videos)
- Network connection between client and server

## Dependencies
All dependencies are managed through Maven in the respective pom.xml files:

### Server Dependencies
- net.bramp.ffmpeg:ffmpeg:0.7.0
- org.slf4j:slf4j-api:2.0.9
- ch.qos.logback:logback-classic:1.4.11

### Client Dependencies
- uk.co.caprica:vlcj:4.8.2
- org.slf4j:slf4j-api:2.0.9
- ch.qos.logback:logback-classic:1.4.11
- org.openjfx:javafx-controls:17
- org.openjfx:javafx-media:17
- org.openjfx:javafx-swing:17
- fr.bmartel:jspeedtest:1.32.1

## Setup and Installation
See the [INSTALL.md](INSTALL.md) file for detailed setup instructions.

## Video Bitrate Guidelines

The application uses the following YouTube-recommended bitrates for different resolutions:

| Resolution | Video Dimensions | Maximum Bitrate | Recommended Bitrate | Minimum Bitrate |
|------------|------------------|-----------------|---------------------|-----------------|
| 240p       | 425 x 240        | 700 Kbps        | 400 Kbps            | 300 Kbps        |
| 360p       | 640 x 360        | 1000 Kbps       | 750 Kbps            | 400 Kbps        |
| 480p       | 854 x 480        | 2000 Kbps       | 1000 Kbps           | 500 Kbps        |
| 720p       | 1280 x 720       | 4000 Kbps       | 2500 Kbps           | 1500 Kbps       |
| 1080p      | 1920 x 1080      | 6000 Kbps       | 4500 Kbps           | 3000 Kbps       |

## License
This project is licensed under the MIT License - see the LICENSE file for details.