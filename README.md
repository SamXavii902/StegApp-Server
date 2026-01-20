# 🕵️ StegApp: Secure Steganography Chat

**StegApp** is a privacy-focused Android messaging application that hides encrypted messages *inside* images using advanced Steganography techniques. Built with a modern tech stack, it ensures that your conversations look like innocent image exchanges to any third-party observer.

![StegApp Banner](https://via.placeholder.com/1200x500.png?text=StegApp+Secure+Chat)
*(Replace with actual screenshot or banner)*

## 🚀 Key Features

*   **Pixels are the Password**: Messages are embedded directly into the pixel data of images using Python-powered algorithms (running natively on Android via ChaquoPython).
*   **Invisible Encryption**: Uses **LSB (Least Significant Bit)** and **PVD (Pixel Value Differencing)** techniques to ensure images look virtually identical to the original.
*   **Real-Time Global Chat**: Powered by a **Python FastAPI** backend and **Socket.IO** for instant, low-latency messaging anywhere in the world.
*   **Cloud Synchronization**:
    *   **MongoDB Atlas**: Persists user accounts and chat metadata securely.
    *   **Cloudinary**: Handles high-speed, **lossless** image storage to preserve steganographic data.
*   **Local-First Architecture**: 
    *   **Room Database**: caches chats offline.
    *   **Jetpack Compose**: provides a fluid, modern Material 3 UI.

## 🛠️ Tech Stack

### Android (Client)
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
*   **Storage**: Room Database (SQLite), DataStore
*   **Networking**: Retrofit, OkHttp, Socket.IO Client
*   **Python Integration**: ChaquoPython (for Steganography algorithms)

### Backend (Server)
*   **Framework**: FastAPI (Python)
*   **Real-time**: Python-SocketIO
*   **Database**: MongoDB Atlas
*   **Storage**: Cloudinary (Lossless optimization)
*   **Hosting**: Render.com

## 🔧 Installation & Setup

### 1. Android App
1.  Clone the repository.
2.  Open in **Android Studio Ladybug (or newer)**.
3.  Sync Gradle dependencies.
4.  Build & Run on an Emulator or Physical Device.

### 2. Backend Server (Optional - Self Hosting)
If you want to run your own backend instead of using the public one:
```bash
cd server
pip install -r requirements.txt
# Create .env file with CLOUDINARY and MONGODB keys
uvicorn main:app --reload
```

## 🔒 How it Works
1.  **Sender** selects an image and types a secret message + password.
2.  **StegApp** embeds the text into the image pixels locally.
3.  The image is uploaded to **Cloudinary** (lossless) and delivered to the recipient.
4.  **Recipient** taps the image and enters the password.
5.  The hidden text is extracted and displayed!

## 🤝 Contributing
Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

## 📄 License
[MIT](https://choosealicense.com/licenses/mit/)
