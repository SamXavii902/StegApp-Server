import requests
import base64
import os

# BASE_URL = "http://localhost:8000" # Local
BASE_URL = "https://stegapp-server.onrender.com" # Cloud (Use this if testing against deployed, but I should test against local running server)
# But I am not sure if local server is running. The user has `run_server.ps1` open.
# I will assume local server for testing if I can run it.
# Actually, I'll try localhost:8000 first.

# BASE_URL = "http://127.0.0.1:8000"
BASE_URL = "https://stegapp-server.onrender.com"

def test_key_exchange():
    print("--- STARTING VERIFICATION ---")
    print(f"Target URL: {BASE_URL}")
    try:
        # Check Root
        print("1. Checking Root Endpoint...")
        resp = requests.get(f"{BASE_URL}/", timeout=10)
        print(f"   Root Status: {resp.status_code}")
        print(f"   Root Msg: {resp.json()}")
    except Exception as e:
        print(f"   Root Check Failed: {e}")
        return

    # 1. Register User A

    # 1. Register User A
    user_a = "TestUserA_" + os.urandom(4).hex()
    key_a = base64.b64encode(os.urandom(32)).decode('utf-8')
    print(f"\n1. Registering {user_a} with Key: {key_a[:10]}...")
    
    resp = requests.post(f"{BASE_URL}/register", json={"username": user_a, "public_key": key_a})
    if resp.status_code == 200:
        print("   Success")
    else:
        print(f"   Failed: {resp.text}")

    # 2. Register User B
    user_b = "TestUserB_" + os.urandom(4).hex()
    key_b = base64.b64encode(os.urandom(32)).decode('utf-8')
    print(f"\n2. Registering {user_b} with Key: {key_b[:10]}...")
    
    resp = requests.post(f"{BASE_URL}/register", json={"username": user_b, "public_key": key_b})
    if resp.status_code == 200:
        print("   Success")
    else:
        print(f"   Failed: {resp.text}")

    # 3. User A fetches User B's Key
    print(f"\n3. Fetching Key for {user_b}...")
    resp = requests.get(f"{BASE_URL}/keys/fetch/{user_b}")
    if resp.status_code == 200:
        fetched_key = resp.json().get("public_key")
        if fetched_key == key_b:
            print(f"   Success! Fetched Key matches: {fetched_key[:10]}...")
        else:
            print(f"   Mismatch! Expected {key_b}, got {fetched_key}")
    else:
        print(f"   Failed: {resp.text}")

    # 4. Update User A's Key
    new_key_a = base64.b64encode(os.urandom(32)).decode('utf-8')
    print(f"\n4. Updating Key for {user_a} to {new_key_a[:10]}...")
    resp = requests.post(f"{BASE_URL}/keys/upload", json={"username": user_a, "public_key": new_key_a})
    if resp.status_code == 200:
        print("   Success")
    else:
        print(f"   Failed: {resp.text}")

    # 5. User B fetches User A's NEW Key
    print(f"\n5. Fetching NEW Key for {user_a}...")
    resp = requests.get(f"{BASE_URL}/keys/fetch/{user_a}")
    if resp.status_code == 200:
        fetched_key = resp.json().get("public_key")
        if fetched_key == new_key_a:
            print(f"   Success! Fetched Key matches: {fetched_key[:10]}...")
        else:
            print(f"   Mismatch! Expected {new_key_a}, got {fetched_key}")
    else:
        print(f"   Failed: {resp.text}")

if __name__ == "__main__":
    try:
        test_key_exchange()
    except Exception as e:
        print(f"Error: {e}")
        print("Is the server running? Run 'uvicorn main:app --reload'")
