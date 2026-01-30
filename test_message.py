import socketio
import time
import requests

# --- Configuration ---
# Default to Render URL, but allow override if running locally
DEFAULT_URL = "https://stegapp-server.onrender.com" 
RECIPIENT_USERNAME = input("Enter YOUR Username (Recipient): ")
SIMULATED_SENDER = "TestBot"

# A reliable, public image URL (Cloudinary Sample) that is guaranteed to work
TEST_IMAGE_URL = "https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg"

# Standard synchronous client (more stable on Windows/simpler deps)
sio = socketio.Client()

@sio.event
def connect():
    print(f"✅ Connected as '{SIMULATED_SENDER}'")
    print(f"🚀 Sending image message to '{RECIPIENT_USERNAME}'...")
    
    # Payload matching what the Android app sends
    payload = {
        "sender": SIMULATED_SENDER,
        "recipient": RECIPIENT_USERNAME,
        "text": "This is a simulated test message.",
        "imageUrl": TEST_IMAGE_URL,
        "secretKey": "1234", # Dummy key
        "timestamp": 1700000000000 # Dummy timestamp
    }
    
    sio.emit('send_message', payload)
    print("📤 Message emitted!")
    print("⏳ Waiting a moment to ensure delivery...")
    time.sleep(5)
    sio.disconnect()

@sio.event
def disconnect():
    print("❌ Disconnected")

def main():
    target_url = input(f"Enter Server URL (Press Enter for {DEFAULT_URL}): ").strip() or DEFAULT_URL
    
    # Append username for connection query
    connect_url = f"{target_url}?username={SIMULATED_SENDER}"
    
    print(f"🔌 Connecting to: {target_url}...")
    try:
        # socketio.Client uses websocket-client by default for ws:// or wss://
        # It handles the query string in the URL correctly
        # Increase timeout for Render cold starts
        http_session = requests.Session()
        sio.connect(connect_url, wait_timeout=60, auth=None)
        sio.wait()
    except Exception as e:
        print(f"🔥 Connection Error: {e}")
        print("Make sure the server is valid and accessible.")
        print("Note: You might need to run 'pip install websocket-client'")

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        pass
