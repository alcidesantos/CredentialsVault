package pt.alcidess.credentialsvault;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

public class CredencialDAO {
    private SQLiteDatabase database;
    private DatabaseHelper dbHelper;

    public CredencialDAO(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    // Inserir nova credencial — inclui last_updated
    public long insert(Credencial credencial) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_NOME, credencial.getNome());
        values.put(DatabaseHelper.COLUMN_USERNAME, credencial.getUsername());
        values.put(DatabaseHelper.COLUMN_PASSWORD, credencial.getPassword());
        values.put(DatabaseHelper.COLUMN_PASSWORD_LENGTH, credencial.getPasswordLength());
        values.put(DatabaseHelper.COLUMN_USE_UPPERCASE, credencial.isUseUppercase() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_USE_LOWERCASE, credencial.isUseLowercase() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_USE_DIGITS, credencial.isUseDigits() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_SPECIAL_CHARS, credencial.getSpecialChars());
        // Adiciona last_updated em segundos (epoch)
        values.put("last_updated", credencial.getLastUpdated() / 1000);
        return database.insert(DatabaseHelper.TABLE_CREDENCIAIS, null, values);
    }

    // Obter todas as credenciais — inclui last_updated
    public List<Credencial> getAll() {
        List<Credencial> credenciais = new ArrayList<>();
        Cursor cursor = database.query(
                DatabaseHelper.TABLE_CREDENCIAIS,
                null, null, null, null, null,
                "last_updated DESC"  // ← ordena por data decrescente (mais recente primeiro)
        );

        if (cursor.moveToFirst()) {
            do {
                Credencial cred = new Credencial();
                cred.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)));
                cred.setNome(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NOME)));
                cred.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USERNAME)));
                cred.setPassword(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PASSWORD)));
                cred.setPasswordLength(cursor.getInt(cursor.getColumnIndexOrThrow("password_length")));
                cred.setUseUppercase(cursor.getInt(cursor.getColumnIndexOrThrow("use_uppercase")) == 1);
                cred.setUseLowercase(cursor.getInt(cursor.getColumnIndexOrThrow("use_lowercase")) == 1);
                cred.setUseDigits(cursor.getInt(cursor.getColumnIndexOrThrow("use_digits")) == 1);
                cred.setSpecialChars(cursor.getString(cursor.getColumnIndexOrThrow("special_chars")));
                // Carrega last_updated e converte para milissegundos
                cred.setLastUpdated(cursor.getLong(cursor.getColumnIndexOrThrow("last_updated")) * 1000L);
                credenciais.add(cred);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return credenciais;
    }

    // Obter por ID — inclui last_updated
    public Credencial getById(long id) {
        Cursor cursor = database.query(
                DatabaseHelper.TABLE_CREDENCIAIS,
                null,
                DatabaseHelper.COLUMN_ID + "=?",
                new String[]{String.valueOf(id)},
                null, null, null
        );

        Credencial cred = null;
        if (cursor.moveToFirst()) {
            cred = new Credencial();
            cred.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)));
            cred.setNome(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NOME)));
            cred.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USERNAME)));
            cred.setPassword(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PASSWORD)));
            // Carrega last_updated
            cred.setLastUpdated(cursor.getLong(cursor.getColumnIndexOrThrow("last_updated")) * 1000L);
        }
        cursor.close();
        return cred;
    }

    // Atualiza credencial — atualiza last_updated
    public int update(Credencial credencial) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_NOME, credencial.getNome());
        values.put(DatabaseHelper.COLUMN_USERNAME, credencial.getUsername());
        values.put(DatabaseHelper.COLUMN_PASSWORD, credencial.getPassword());
        values.put(DatabaseHelper.COLUMN_PASSWORD_LENGTH, credencial.getPasswordLength());
        values.put(DatabaseHelper.COLUMN_USE_UPPERCASE, credencial.isUseUppercase() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_USE_LOWERCASE, credencial.isUseLowercase() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_USE_DIGITS, credencial.isUseDigits() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_SPECIAL_CHARS, credencial.getSpecialChars());
        // Atualiza last_updated ao guardar
        values.put("last_updated", credencial.getLastUpdated() / 1000);

        return database.update(
                DatabaseHelper.TABLE_CREDENCIAIS,
                values,
                DatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(credencial.getId())}
        );
    }

    public int deleteAll() {
        return database.delete(DatabaseHelper.TABLE_CREDENCIAIS, null, null);
    }

    public int deleteById(long id) {
        return database.delete(
                DatabaseHelper.TABLE_CREDENCIAIS,
                DatabaseHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public int count() {
        Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CREDENCIAIS, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    public boolean existeCredencialExata(String nome, String username, String password) {
        String query = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CREDENCIAIS +
                " WHERE " + DatabaseHelper.COLUMN_NOME + " = ?" +
                " AND " + DatabaseHelper.COLUMN_USERNAME + " = ?" +
                " AND " + DatabaseHelper.COLUMN_PASSWORD + " = ?";

        Cursor cursor = database.rawQuery(query, new String[]{nome, username, password});
        boolean existe = false;
        if (cursor.moveToFirst()) {
            existe = cursor.getInt(0) > 0;
        }
        cursor.close();
        return existe;
    }
}