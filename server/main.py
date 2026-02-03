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

from typing import Optional

# --- Pydantic Models ---
class UserRegister(BaseModel):
    username: str
    public_key: Optional[str] = None  # Base64 encoded public key

class KeyUpload(BaseModel):
    username: str
    public_key: str

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
    
    users_collection.insert_one({
        "username": user.username, 
        "socket_id": None,
        "public_key": user.public_key
    })
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

@app.post("/keys/upload")
async def upload_key(data: KeyUpload):
    """
    Updates the public key for a user.
    """
    result = users_collection.update_one(
        {"username": data.username},
        {"$set": {"public_key": data.public_key}}
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="User not found")
    return {"status": "success"}

@app.get("/keys/fetch/{username}")
async def fetch_key(username: str):
    """
    Retrieves the public key for a specific user.
    """
    user = users_collection.find_one({"username": username})
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    public_key = user.get("public_key")
    if not public_key:
        raise HTTPException(status_code=404, detail="User has no public key")
        
    return {"username": username, "public_key": public_key}

@app.post("/upload/")
async def upload_image(file: UploadFile = File(...)):
    """
    Receives an image file and uploads it to Cloudinary.
    Returns the secure URL.
    """
    try:
        # Upload to Cloudinary (Enforce PNG and Lossless via quality)
        # Increased timeout to 300s for large files
        print(f"Starting upload for {file.filename}...")
        result = cloudinary.uploader.upload(
            file.file, 
            resource_type="image",
            format="png",
            quality="100",
            timeout=300 
        )
        # Get the URL
        url = result.get("secure_url")
        print(f"Upload success: {url}")
        return {"status": "success", "url": url, "filename": file.filename}
    except Exception as e:
        print(f"Upload Error: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Upload Failed: {str(e)}")

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
            
            # ✅ Send delivery confirmation to sender
            sender_user = users_collection.find_one({"username": data['sender']})
            if sender_user and sender_user.get("socket_id"):
                await sio.emit('message_status', {
                    'messageId': data['id'],
                    'status': 'delivered'
                }, room=sender_user["socket_id"])
                print(f"✅ Sent 'delivered' status to {data['sender']} for offline message")


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


print("✅ Socket.IO event handlers registered: connect, disconnect, send_message, delete_message, user_status, message_read")

@sio.event
async def user_status(sid, data):
    """
    Handle user online/offline status updates.
    Data: { 'username': ..., 'status': 'online'/'offline' }
    """
    username = data.get('username')
    status = data.get('status')
    
    if username and status:
        print(f"User {username} is now {status}")
        # Broadcast to all connected clients
        await sio.emit('user_status', data, skip_sid=sid)
    else:
        print(f"Invalid status update: {data}")



@sio.event
async def send_message(sid, data):
    """
    Relay message to specific recipient if possible, else broadcast.
    Data: { 'text': ..., 'imageUrl': ..., 'sender': ..., 'recipient': ... }
    """
    print(f"Message received: {data}")
    recipient = data.get('recipient')
    sender = data.get('sender')
    message_id = data.get('id')
    
    # ALWAYS Save to DB first (Persistence)
    msg_doc = {
        "id": message_id,
        "text": data.get("text"),
        "imageUrl": data.get("imageUrl"),
        "sender": sender,
        "recipient": recipient,
        "timestamp": data.get("timestamp"), # Client should send TS or Server adds it
        "delivered": False,
        "read": False
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
            # Mark as delivered in DB
            messages_collection.update_one({"_id": result.inserted_id}, {"$set": {"delivered": True}})
            
            # ✅ SEND DELIVERY CONFIRMATION TO SENDER
            sender_user = users_collection.find_one({"username": sender})
            if sender_user and sender_user.get("socket_id"):
                await sio.emit('message_status', {
                    'messageId': message_id,
                    'status': 'delivered'
                }, room=sender_user["socket_id"])
                print(f"✅ Sent 'delivered' status to {sender}")
        else:
            print(f"Recipient {recipient} offline. Message queued.")
    else:
        # Fallback to broadcast (Old behavior - rarely used now)
        await sio.emit('new_message', data)


@sio.event
async def message_read(sid, data):
    """
    Handle read receipt from recipient.
    Data: { 'messageId': ..., 'reader': ... }
    """
    message_id = data.get('messageId')
    reader = data.get('reader')
    
    print(f"👁️ Read receipt: {reader} read message {message_id}")
    
    # Update DB to mark as read
    messages_collection.update_one(
        {"id": message_id},
        {"$set": {"read": True}}
    )
    
    # Find the original message to get sender
    msg = messages_collection.find_one({"id": message_id})
    if msg:
        sender = msg.get("sender")
        
        # Send read status to original sender
        sender_user = users_collection.find_one({"username": sender})
        if sender_user and sender_user.get("socket_id"):
            await sio.emit('message_status', {
                'messageId': message_id,
                'status': 'read'
            }, room=sender_user["socket_id"])
            print(f"✅ Sent 'read' status to {sender}")
        else:
            print(f"⚠️ Sender {sender} offline, read receipt not sent")
    else:
        print(f"⚠️ Message {message_id} not found in database")



if __name__ == "__main__":
    import uvicorn
    # Host 0.0.0.0 allows access from other devices on the network
    uvicorn.run(app, host="0.0.0.0", port=8000)
