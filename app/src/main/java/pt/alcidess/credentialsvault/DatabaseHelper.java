package pt.alcidess.credentialsvault;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "credentials_vault.db";

    // DATABASE_VERSION history:
    // 1 - esquma inicial
    // 2 - adicionados campos para password generation settings (length, use_uppercase, etc.)
    // 3 - actualizado password generation settings to use default special chars (no schema change)
    // 4 - adicionado campo last_updated para credenciais
    private static final int DATABASE_VERSION = 4;

    // Nome da tabela
    public static final String TABLE_CREDENCIAIS = "credenciais";

    // Colunas
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NOME = "nome";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";

    public static final String COLUMN_PASSWORD_LENGTH = "password_length";
    public static final String COLUMN_USE_UPPERCASE = "use_uppercase";
    public static final String COLUMN_USE_LOWERCASE = "use_lowercase";
    public static final String COLUMN_USE_DIGITS = "use_digits";
    public static final String COLUMN_SPECIAL_CHARS = "special_chars";
    private static final String DEFAULT_SPECIAL_CHARS = "!@#$%^&*";
    private static final int DEFAULT_PASSWORD_LENGTH = 12;
    private static final int DEFAULT_BOOLEAN_TRUE = 1;
    private static final int DEFAULT_BOOLEAN_FALSE = 0;
    // SQL para criar tabela
    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_CREDENCIAIS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_NOME + " TEXT NOT NULL, " +
                    COLUMN_USERNAME + " TEXT, " +
                    COLUMN_PASSWORD + " TEXT, " +
                    COLUMN_PASSWORD_LENGTH + " INTEGER DEFAULT " + DEFAULT_PASSWORD_LENGTH + ", " +
                    COLUMN_USE_UPPERCASE + " INTEGER DEFAULT " + DEFAULT_BOOLEAN_TRUE + ", " +
                    COLUMN_USE_LOWERCASE + " INTEGER DEFAULT " + DEFAULT_BOOLEAN_TRUE + ", " +
                    COLUMN_USE_DIGITS + " INTEGER DEFAULT " + DEFAULT_BOOLEAN_TRUE + ", " +
                    COLUMN_SPECIAL_CHARS + " TEXT DEFAULT '" + DEFAULT_SPECIAL_CHARS + "', " +
                    "last_updated INTEGER NOT NULL DEFAULT (" + (System.currentTimeMillis() / 1000) + ")" +  // em segundos
                    ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_CREDENCIAIS +
                    " ADD COLUMN " + COLUMN_PASSWORD_LENGTH + " INTEGER DEFAULT " + DEFAULT_PASSWORD_LENGTH);
            db.execSQL("ALTER TABLE " + TABLE_CREDENCIAIS +
                    " ADD COLUMN " + COLUMN_USE_UPPERCASE + " INTEGER DEFAULT " + DEFAULT_BOOLEAN_TRUE);
            db.execSQL("ALTER TABLE " + TABLE_CREDENCIAIS +
                    " ADD COLUMN " + COLUMN_USE_LOWERCASE + " INTEGER DEFAULT " + DEFAULT_BOOLEAN_TRUE);
            db.execSQL("ALTER TABLE " + TABLE_CREDENCIAIS +
                    " ADD COLUMN " + COLUMN_USE_DIGITS + " INTEGER DEFAULT " + DEFAULT_BOOLEAN_TRUE);
            db.execSQL("ALTER TABLE " + TABLE_CREDENCIAIS +
                    " ADD COLUMN " + COLUMN_SPECIAL_CHARS + " TEXT DEFAULT '" + DEFAULT_SPECIAL_CHARS + "'");
        }
        if (oldVersion < 4) {
            // Adiciona last_updated em segundos (epoch), com valor atual para existentes
            db.execSQL("ALTER TABLE " + TABLE_CREDENCIAIS +
                    " ADD COLUMN last_updated INTEGER NOT NULL DEFAULT " + (System.currentTimeMillis() / 1000));
        }
    }
}