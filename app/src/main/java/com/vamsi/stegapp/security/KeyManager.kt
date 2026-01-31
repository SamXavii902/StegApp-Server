package com.vamsi.stegapp.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

object KeyManager {
    private const val KEY_ALIAS = "StegAppIdentityKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun getPublicKey(): String? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            entry?.certificate?.publicKey?.encoded?.let {
                Base64.encodeToString(it, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateKeyPair(): String? {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, 
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_AGREE_KEY
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                build()
            }
            keyPairGenerator.initialize(parameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()
            Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deriveSharedSecret(remotePublicKeyBase64: String): String? {
        return try {
            // 1. Load our Private Key
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val myPrivateKey: PrivateKey = entry.privateKey

            // 2. Decode Remote Public Key
            val remoteKeyBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val remotePublicKeySpec = X509EncodedKeySpec(remoteKeyBytes)
            val remotePublicKey: PublicKey = keyFactory.generatePublic(remotePublicKeySpec)

            // 3. Perform ECDH
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(myPrivateKey)
            keyAgreement.doPhase(remotePublicKey, true)
            val sharedSecretBytes = keyAgreement.generateSecret()

            // 4. Hash the secret to get a clean AES key (32 bytes / 256 bits)
            val digest = MessageDigest.getInstance("SHA-256")
            val hashedSecret = digest.digest(sharedSecretBytes)
            
            // Return as Base64 String to use as the "password" for existing stego logic
            Base64.encodeToString(hashedSecret, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
