from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.backends import default_backend
from PIL import Image
import numpy as np
import lz4.frame
import os
import sys

# Common Utilities

def generate_magic_pair_indices(width, height, seed=42):
    pair_indices = [(y, x) for y in range(height) for x in range(0, width - 1, 2)]
    np.random.seed(seed)
    np.random.shuffle(pair_indices)
    return pair_indices

def _clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v

# Encryption

def encrypt_message_chacha20(message_bytes: bytes, password: str):
    salt = os.urandom(16)
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(), length=32, salt=salt, iterations=100000, backend=default_backend()
    )
    key = kdf.derive(password.encode())
    nonce = os.urandom(16)
    algorithm = algorithms.ChaCha20(key, nonce)
    cipher = Cipher(algorithm, mode=None, backend=default_backend())
    encryptor = cipher.encryptor()
    ciphertext = encryptor.update(message_bytes)
    return salt, nonce, ciphertext

def decrypt_chacha20(salt, nonce, ciphertext, password):
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(), length=32, salt=salt, iterations=100000, backend=default_backend()
    )
    key = kdf.derive(password.encode())
    cipher = Cipher(algorithms.ChaCha20(key, nonce), mode=None, backend=default_backend())
    decryptor = cipher.decryptor()
    return decryptor.update(ciphertext)


# Embed Logic

def message_to_bits(message, password):
    message_with_marker = (message + "||END||").encode("utf-8")
    compressed = lz4.frame.compress(message_with_marker)
    salt, nonce, encrypted = encrypt_message_chacha20(compressed, password)

    bits = "01000101"  # 'E' marker
    bits += format(len(encrypted), '032b')

    final_payload = salt + nonce + encrypted
    for b in final_payload:
        bits += format(b, '08b')
    return bits

def embed_pvd(image_path, message, output_path, password):
    try:
        img = Image.open(image_path).convert("RGB")
        width, height = img.size
        pixels = np.array(img, dtype=int)

        bits_to_embed = message_to_bits(message, password)
        total_bits = len(bits_to_embed)
        bit_idx = 0

        pair_list = generate_magic_pair_indices(width, height)

        for (y, x) in pair_list:
            if bit_idx >= total_bits:
                break

            p1 = int(pixels[y, x, 0])
            p2 = int(pixels[y, x + 1, 0])
            diff = abs(p1 - p2)

            if diff < 8:       n_bits = 1
            elif diff < 16:    n_bits = 2
            elif diff < 32:    n_bits = 3
            elif diff < 64:    n_bits = 4
            else:              n_bits = 5

            remaining = total_bits - bit_idx
            if n_bits > remaining:
                n_bits = remaining

            secret_bits = bits_to_embed[bit_idx: bit_idx + n_bits]
            bit_val = int(secret_bits, 2)

            mod = 2 ** n_bits
            new_diff = (diff - (diff % mod)) + bit_val

            sgn = 1 if p1 < p2 else -1

            if sgn == 1:
                p1p = _clamp(p1, 0, 255 - new_diff)
                p2p = p1p + new_diff
            else:
                p1p = _clamp(p1, new_diff, 255)
                p2p = p1p - new_diff

            pixels[y, x, 0] = int(_clamp(p1p, 0, 255))
            pixels[y, x + 1, 0] = int(_clamp(p2p, 0, 255))

            bit_idx += n_bits

        # Ensure saving as PNG to preserve pixel values
        out = Image.fromarray(pixels.astype(np.uint8))
        out.save(output_path, format="PNG")
        return f"SUCCESS:{output_path}"
    except Exception as e:
        return f"FAILURE:{str(e)}"

# Extract Logic

def bits_to_message(bits, password):
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

def extract_pvd(image_path, password):
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
