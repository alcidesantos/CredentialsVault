package pt.alcidess.credentialsvault;

import android.content.Context;

public class AppConfig {
    public static String getDefaultSpecialChars(Context context) {
        return context.getString(R.string.default_password_special_chars);
    }

    public static int getDefaultPasswordLength(Context context) {
        return context.getResources().getInteger(R.integer.default_password_length);
    }

    public static boolean getDefaultUseUppercase(Context context) {
        return context.getResources().getBoolean(R.bool.default_use_uppercase);
    }

    public static boolean getDefaultUseLowercase(Context context) {
        return context.getResources().getBoolean(R.bool.default_use_lowercase);
    }

    public static boolean getDefaultUseDigits(Context context) {
        return context.getResources().getBoolean(R.bool.default_use_digits);
    }
}