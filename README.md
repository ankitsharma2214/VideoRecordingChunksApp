📹 VideoRecordChunks

VideoRecordChunks is an Android application that records video using CameraX, processes it in the background, adds a watermark, and splits the recorded video into multiple chunks before saving them to the device gallery.

The app demonstrates how to implement background video recording with a foreground service, along with post-processing using Media3 Transformer.

✨ Features

🎥 Video recording using CameraX

📺 Live camera preview

🔊 Audio recording support

🧵 Background recording with Foreground Service

🖊 Automatic watermark added to recorded video

✂️ Video split into multiple chunks

💾 Save full video and chunks to device gallery

🔁 Service auto-restart if the app is removed

🛠 Tech Stack

Kotlin

CameraX API

Android Media3 Transformer

MediaExtractor & MediaMuxer

RecyclerView

Android Foreground Service

⚙️ How It Works

User starts recording from the app.

The app records video using CameraX.

After recording stops:

A watermark is added to the video.

The video is split into 4 chunks.

The full video and chunks are saved to the device gallery.

🔐 Permissions Required

The app requires the following permissions:

CAMERA

RECORD_AUDIO

FOREGROUND_SERVICE

FOREGROUND_SERVICE_CAMERA

FOREGROUND_SERVICE_MICROPHONE

▶️ Running the Project

Clone the repository

git clone https://github.com/YOUR_USERNAME/VideoRecordChunks.git

Open the project in Android Studio

Connect a real Android device

Run the application

📂 Output Example

After recording, the following files will be generated:

Movies/
 ├── FULL_VIDEO_xxx.mp4
 ├── CHUNK_1_xxx.mp4
 ├── CHUNK_2_xxx.mp4
 ├── CHUNK_3_xxx.mp4
 └── CHUNK_4_xxx.mp4
👨‍💻 Author

Developed by Ankit

⭐ If you find this project useful, consider starring the repository.
