package pt.alcidess.credentialsvault;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

public class CryptoUtils {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Deriva chave a partir da senha do utilizador
    public static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    // Encripta dados
    public static byte[] encrypt(byte[] data, String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] encrypted = cipher.doFinal(data);

        // Formato: [salt:16][iv:12][ciphertext]
        byte[] result = new byte[16 + 12 + encrypted.length];
        System.arraycopy(salt, 0, result, 0, 16);
        System.arraycopy(iv, 0, result, 16, 12);
        System.arraycopy(encrypted, 0, result, 28, encrypted.length);
        return result;
    }

    // Desencripta dados
    public static byte[] decrypt(byte[] encryptedData, String password) throws Exception {
        if (encryptedData.length < 28) throw new IllegalArgumentException("Dados inválidos");

        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[encryptedData.length - 28];
        System.arraycopy(encryptedData, 0, salt, 0, 16);
        System.arraycopy(encryptedData, 16, iv, 0, 12);
        System.arraycopy(encryptedData, 28, ciphertext, 0, ciphertext.length);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }
}