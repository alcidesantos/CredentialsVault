package pt.alcidess.credentialsvault;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

public class SecurityManager {

    // Interface para retorno
    public interface AuthCallback {
        void onAuthenticated();
        default void onCanceled() {}
    }

    public static void exigirAutenticacao(androidx.appcompat.app.AppCompatActivity activity, String title, String subtitle, AuthCallback callback) {
        BiometricManager biometricManager = BiometricManager.from(activity);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.security_manager_configurar_titulo))
                    .setMessage(activity.getString(R.string.security_manager_configurar_mensagem))
                    .setPositiveButton(activity.getString(R.string.security_manager_btn_definicoes), (d, w) ->
                            activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> callback.onCanceled())
                    .setCancelable(false)
                    .show();
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)          // ← já é passado como parâmetro (internacionalizado ao chamar)
                .setSubtitle(subtitle)    // ← idem
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        callback.onAuthenticated();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            Toast.makeText(activity,
                                    activity.getString(R.string.security_manager_erro_autenticacao, errString),
                                    Toast.LENGTH_SHORT).show();
                        }
                        callback.onCanceled();
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    // Atalho para operações sensíveis
    public static void exigirAutenticacaoPara(androidx.appcompat.app.AppCompatActivity activity, Runnable acao) {
        exigirAutenticacao(activity,
                activity.getString(R.string.security_manager_confirmar_identidade_titulo),
                activity.getString(R.string.security_manager_confirmar_identidade_mensagem),
                new AuthCallback() {
                    @Override
                    public void onAuthenticated() { acao.run(); }
                });
    }
}