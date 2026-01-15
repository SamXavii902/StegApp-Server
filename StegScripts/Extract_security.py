from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.backends import default_backend
from PIL import Image
import numpy as np
import lz4.frame

# ----------- HARDCODED VALUES ----------- #
# Ensure this matches the OUTPUT_PATH from the embed script (must be .png)
STEGO_IMAGE_PATH = "C:/Users/vamsi/Desktop/Final Year Project/TRAE_projekt_stego/PVD/stego.png"
PASSWORD = "strongPassword123"
# ---------------------------------------- #

def decrypt_chacha20(salt, nonce, ciphertext, password):
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(), length=32, salt=salt, iterations=100000, backend=default_backend()
    )
    key = kdf.derive(password.encode())
    cipher = Cipher(algorithms.ChaCha20(key, nonce), mode=None, backend=default_backend())
    decryptor = cipher.decryptor()
    return decryptor.update(ciphertext)

def bits_to_message(bits, password=PASSWORD):
    marker = bits[:8]
    if marker != '01000101':  # 'E'
        return "[Error: Missing encryption marker or wrong file format]"

    try:
        data_length = int(bits[8:40], 2)
        total_bytes = 16 + 16 + data_length # salt + nonce + ciphertext
        expected_total_bits = 40 + total_bytes * 8
        
        if len(bits) < expected_total_bits:
             return "[Error: Incomplete data extracted]"

        bits = bits[:expected_total_bits]

        encrypted_bytes = bytearray()
        for i in range(40, len(bits), 8):
            encrypted_bytes.append(int(bits[i:i+8], 2))

        salt = bytes(encrypted_bytes[:16])
        nonce = bytes(encrypted_bytes[16:32])
        ciphertext = bytes(encrypted_bytes[32:])

        decrypted = decrypt_chacha20(salt, nonce, ciphertext, password)
        decompressed = lz4.frame.decompress(decrypted)
        return decompressed.decode('utf-8').split("||END||")[0]
        
    except Exception as e:
        return f"[Extraction Failed] {e}"

def generate_magic_pair_indices(width, height, seed=42):
    pair_indices = [(y, x) for y in range(height) for x in range(0, width - 1, 2)]
    np.random.seed(seed)
    np.random.shuffle(pair_indices)
    return pair_indices

def extract_pvd(image_path, password=PASSWORD):
    try:
        img = Image.open(image_path).convert("RGB")
    except FileNotFoundError:
        return f"[Error] File not found: {image_path}"
        
    width, height = img.size
    pixels = np.array(img, dtype=int)
    pair_list = generate_magic_pair_indices(width, height)

    extracted_bits = []
    bit_count = 0
    header_read = False
    total_bits_to_read = float('inf')

    for y, x in pair_list:
        if bit_count >= total_bits_to_read:
            break

        p1 = pixels[y, x, 0]
        p2 = pixels[y, x + 1, 0]
        diff = abs(p1 - p2)

        if diff < 8:       n_bits = 1
        elif diff < 16:    n_bits = 2
        elif diff < 32:    n_bits = 3
        elif diff < 64:    n_bits = 4
        else:              n_bits = 5

        d_prime = diff % (2 ** n_bits)
        extracted_bits.append(f'{d_prime:0{n_bits}b}')
        bit_count += n_bits

        if not header_read and bit_count >= 40:
            header_read = True
            temp_bits = ''.join(extracted_bits)
            # Read the 32-bit length header to know when to stop
            data_length = int(temp_bits[8:40], 2)
            total_bits_to_read = 40 + (16 + 16 + data_length) * 8

    all_bits = ''.join(extracted_bits)
    return bits_to_message(all_bits, password)

if __name__ == "__main__":
    secret_message = extract_pvd(STEGO_IMAGE_PATH, PASSWORD)
    print(f"Extracted Message: {secret_message}")