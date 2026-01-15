# StegApp Testing Guide

## 1. Start the Backend Server
The Android app needs the Python server to handle image uploads and socket connections.

1.  Open a terminal in the project root: `c:\Users\vamsi\Desktop\Resume projects\StegAppMaterialUI`
2.  Run the helper script:
    ```powershell
    .\run_server.ps1
    ```
    *This starts the server on port 8000.*

## 2. Configure the Android App
Ensure the app points to your computer's IP address if you are running on a real device.

1.  Open `app/src/main/java/com/vamsi/stegapp/network/NetworkModule.kt`.
2.  Update `BASE_URL` with your PC's local IP:
    ```kotlin
    // Example (Check your specific IP via `ipconfig`)
    const val BASE_URL = "http://192.168.1.X:8000/" 
    ```
    *(Note: You already updated this to `http://172.31.221.145/`. Ensure that is correct and the port `:8000` is included if needed, e.g., `http://172.31.221.145:8000/`)*.

    > **Important:** If your server is running on port 8000, your URL MUST include `:8000`.

## 3. Testing Steps

### A. Hide Message (Send)
1.  Open the app on your phone.
2.  Tap the **"Hide"** tab (or ensure you are in Hide mode).
3.  Tap the **Image Icon** to select a cover image.
4.  Type a secret message.
5.  Enter a secret key/password.
6.  Tap **Send**.
    *   **Success:** The app should embed the text into the image, save it locally, and upload it to the server. You should see a "Sent & Uploaded" toast.

### B. Reveal Message (Receive)
1.  Tap the **"Reveal"** tab.
2.  Tap the **Image Icon** to select a Stego-image (one that has a message hidden).
3.  Enter the **same** secret key used to hide the message.
4.  The hidden text should appear in the chat bubble.

## 4. Troubleshooting
*   **Connection Refused:** Ensure your phone and PC are on the same Wi-Fi network.
*   **Firewall:** Windows Firewall might block the connection. Allow Python/Uvicorn through the firewall or temporarily disable it for testing.
*   **Port Missing:** Make sure `BASE_URL` includes the port (usually `:8000`) unless you are using a reverse proxy.
