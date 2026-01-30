import os
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
import socketio
import shutil
from pathlib import Path

# --- Configuration ---
# --- Configuration ---
# UPLOAD_DIR = "uploads" # Deprecated for Cloud
# Path(UPLOAD_DIR).mkdir(parents=True, exist_ok=True)

from dotenv import load_dotenv
load_dotenv()

import cloudinary
import cloudinary.uploader

# Cloudinary Config
cloudinary.config(
  cloud_name = os.getenv("CLOUDINARY_CLOUD_NAME"),
  api_key = os.getenv("CLOUDINARY_API_KEY"),
  api_secret = os.getenv("CLOUDINARY_API_SECRET"),
  secure = True
)

# --- MongoDB Setup ---
from pymongo.mongo_client import MongoClient
from pymongo.server_api import ServerApi
from pydantic import BaseModel

uri = os.getenv("MONGODB_URI")
if not uri:
    raise ValueError("MONGODB_URI environment variable not set")

# Create a new client and connect to the server
mongo_client = MongoClient(uri, server_api=ServerApi('1'))
try:
    mongo_client.admin.command('ping')
    print("Pinged your deployment. You successfully connected to MongoDB!")
except Exception as e:
    print(f"MongoDB Connection Error: {e}")

db = mongo_client["steg_app_db"]
users_collection = db["users"]
messages_collection = db["messages"]

# --- Pydantic Models ---
class UserRegister(BaseModel):
    username: str

# --- Socket.IO Setup ---
# Async Socket.IO server
sio = socketio.AsyncServer(async_mode='asgi', cors_allowed_origins='*')
# Wrap with ASGI application
socket_app = socketio.ASGIApp(sio)

# --- FastAPI Setup ---
app = FastAPI()

# Mount Socket.IO app to /socket.io
app.mount("/socket.io", socket_app)

# CORS (Allow all for development)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
async def root():
    return {"status": "running", "message": "StegApp Cloud Server is Active"}

@app.post("/register")
async def register(user: UserRegister):
    """
    Registers a new user.
    """
    existing_user = users_collection.find_one({"username": user.username})
    if existing_user:
        raise HTTPException(status_code=400, detail="Username already exists")
    
    users_collection.insert_one({"username": user.username, "socket_id": None})
    return {"status": "success", "username": user.username}

@app.get("/check_user/{username}")
async def check_user(username: str):
    """
    Checks if a user exists.
    """
    user = users_collection.find_one({"username": username})
    if user:
        return {"exists": True}
    return {"exists": False}

@app.post("/upload/")
async def upload_image(file: UploadFile = File(...)):
    """
    Receives an image file and uploads it to Cloudinary.
    Returns the secure URL.
    """
    try:
        # Upload to Cloudinary (Enforce PNG and Lossless via quality)
        result = cloudinary.uploader.upload(
            file.file, 
            resource_type="image",
            format="png",
            quality="100"
        )
        # Get the URL
        url = result.get("secure_url")
        return {"status": "success", "url": url, "filename": file.filename}
    except Exception as e:
        print(f"Upload Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# --- Socket.IO Events ---
@sio.event
async def connect(sid, environ):
    # Extract username from query params
    query_string = environ.get('QUERY_STRING', '')
    params = dict(qs.split('=') for qs in query_string.split('&') if '=' in qs)
    username = params.get('username')
    
    if username:
        print(f"Client connected: {username} ({sid})")
        users_collection.update_one({"username": username}, {"$set": {"socket_id": sid}})
        
        # SYNC: Deliver offline messages
        offline_msgs = messages_collection.find({"recipient": username, "delivered": False})
        for msg in offline_msgs:
            data = {
                "id": msg.get("id"),
                "text": msg.get("text"),
                "imageUrl": msg.get("imageUrl"),
                "sender": msg.get("sender"),
                "recipient": msg.get("recipient"),
                "timestamp": msg.get("timestamp")
            }
            await sio.emit('new_message', data, room=sid)
            # Mark as delivered
            messages_collection.update_one({"_id": msg["_id"]}, {"$set": {"delivered": True}})
            print(f"Synced offline message from {data['sender']} to {username}")

    else:
        print(f"Client connected (Anonymous): {sid}")

@sio.event
async def disconnect(sid):
    print(f"Client disconnected: {sid}")
    # Optional: Clear socket_id in DB
    users_collection.update_one({"socket_id": sid}, {"$set": {"socket_id": None}})

@sio.event
async def delete_message(sid, data):
    """
    Handle message deletion request.
    Data: { 'messageId': ..., 'recipient': ... }
    """
    print(f"Delete request received: {data}")
    recipient = data.get('recipient')
    message_id = data.get('messageId')

    if recipient and message_id:
        # 1. ALWAYS Delete from DB (if you want server-side deletion too)
        # messages_collection.delete_one({"_id": ...}) # ID format mismatch likely, skipping for now or use filter
        
        # 2. Forward to Recipient
        user = users_collection.find_one({"username": recipient})
        if user and user.get("socket_id"):
            target_sid = user["socket_id"]
            await sio.emit('delete_message', data, room=target_sid)
            print(f"Forwarded delete_message to {recipient}")
        else:
            print(f"Recipient {recipient} offline. Deletion not propagated immediately.")
    else:
        print(f"Invalid delete request: {data}")

print("✅ Socket.IO event handlers registered: connect, disconnect, send_message, delete_message")


@sio.event
async def send_message(sid, data):
    """
    Relay message to specific recipient if possible, else broadcast.
    Data: { 'text': ..., 'imageUrl': ..., 'sender': ..., 'recipient': ... }
    """
    print(f"Message received: {data}")
    recipient = data.get('recipient')
    
    # ALWAYS Save to DB first (Persistence)
    msg_doc = {
        "id": data.get("id"),
        "text": data.get("text"),
        "imageUrl": data.get("imageUrl"),
        "sender": data.get("sender"),
        "recipient": recipient,
        "timestamp": data.get("timestamp"), # Client should send TS or Server adds it
        "delivered": False
    }
    result = messages_collection.insert_one(msg_doc)
    
    if recipient:
        # Find recipient's socket_id
        user = users_collection.find_one({"username": recipient})
        if user and user.get("socket_id"):
            target_sid = user["socket_id"]
            # Try to emit
            await sio.emit('new_message', data, room=target_sid)
            print(f"Sent to {recipient} at {target_sid}")
            # Mark as delivered
            messages_collection.update_one({"_id": result.inserted_id}, {"$set": {"delivered": True}})
        else:
            print(f"Recipient {recipient} offline. Message queued.")
    else:
        # Fallback to broadcast (Old behavior - rarely used now)
        await sio.emit('new_message', data)


if __name__ == "__main__":
    import uvicorn
    # Host 0.0.0.0 allows access from other devices on the network
    uvicorn.run(app, host="0.0.0.0", port=8000)
