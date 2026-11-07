package com.consultainventario.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la API key cifrada en el almacenamiento privado de la app.
 * Android Keystore protege la clave AES; SharedPreferences almacena solo
 * el vector de inicialización (IV) y el texto cifrado.
 */
internal class KeyStoreStore(context: Context) {
    private val preferences = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)
    private val alias = "consulta_inventario_api_key"

    /** Recupera la API key o devuelve null si no existe o ya no puede descifrarse. */
    fun get(): String? {
        val encoded = preferences.getString("key", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            // Si el dato cifrado quedó inválido, se elimina para permitir configurarlo de nuevo.
            preferences.edit().remove("key").apply()
            null
        }
    }

    /** Cifra y guarda la clave ingresada en la pantalla de configuración. */
    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        // El IV se antepone al cifrado para poder usarlo al descifrar.
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString("key", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    /** Quita la API key guardada en el teléfono. */
    fun clear() {
        preferences.edit().remove("key").apply()
    }

    /** Reutiliza la clave AES del sistema o la crea en el primer guardado. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
