package pt.alcidess.credentialsvault;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.provider.OpenableColumns;
import android.database.Cursor;

public class ImpExpActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT = 300;
    private static final int REQUEST_CODE_IMPORT_CVB = 400;
    private static final int REQUEST_CODE_IMPORT_JSON = 500;

    private Button buttonExportarBD;
    private Button buttonExportarComSenha;
    private Button buttonExportarJSON;

    private Button buttonImportar;
    private Button buttonImportarComSenha;
    private Button buttonImportarJSON;

    // Campo para guardar dados temporariamente
    private byte[] dadosParaExportar;

    // Para repetir seleção após erro
    private Intent ultimoIntentImportacao;
    private int ultimoRequestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imp_exp);

        buttonExportarBD = findViewById(R.id.buttonExportarBD);
        buttonExportarComSenha = findViewById(R.id.buttonExportarComSenha);
        buttonExportarJSON = findViewById(R.id.buttonExportarJSON);
        buttonImportar = findViewById(R.id.buttonImportar);
        buttonImportarComSenha = findViewById(R.id.buttonImportarComSenha);
        buttonImportarJSON = findViewById(R.id.buttonImportarJSON);

        List<Credencial> credenciaisSelecionadas = getIntent()
                .getParcelableArrayListExtra("credenciais_selecionadas");

        if (credenciaisSelecionadas != null) {
            setTitle(getString(R.string.imp_exp_titulo_parcial, credenciaisSelecionadas.size()));

            // Modo parcial: só exportações, todas com autenticação
            buttonExportarBD.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, () -> exportarBaseDados(credenciaisSelecionadas)));
            buttonExportarComSenha.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, () -> exportarComSenha(credenciaisSelecionadas)));
            buttonExportarJSON.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, () -> exportarComoJson(credenciaisSelecionadas)));

            // Esconde botões de importação
            buttonImportar.setVisibility(View.GONE);
            buttonImportarComSenha.setVisibility(View.GONE);
            buttonImportarJSON.setVisibility(View.GONE);

        } else {
            // Modo normal: tudo visível, todas as operações com autenticação
            buttonExportarBD.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, () -> exportarBaseDados(null)));
            buttonExportarComSenha.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, () -> exportarComSenha(null)));
            buttonExportarJSON.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, () -> exportarComoJson(null)));

            buttonImportar.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, this::iniciarImportacao));
            buttonImportarComSenha.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, this::importarComSenha));
            buttonImportarJSON.setOnClickListener(v ->
                    SecurityManager.exigirAutenticacaoPara(this, this::importarJson));

            // Garante visibilidade
            buttonImportar.setVisibility(View.VISIBLE);
            buttonImportarComSenha.setVisibility(View.VISIBLE);
            buttonImportarJSON.setVisibility(View.VISIBLE);

        }
    }

    private void exportarBaseDados() {
        File dbFile = getDatabasePath("credentials_vault.db");
        if (!dbFile.exists()) {
            // Internationalized
            Toast.makeText(this, R.string.impexp_erro_bd_nao_encontrada, Toast.LENGTH_SHORT).show();
            return;
        }

        String exportFileName = getString(R.string.impexp_nome_ficheiro,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));

        Uri exportUri = copyDbToPublicDocuments(dbFile, exportFileName);
        if (exportUri == null) {
            Toast.makeText(this, R.string.impexp_erro_exportar, Toast.LENGTH_SHORT).show();
            return;
        }

        partilharFicheiro(exportUri, exportFileName);
    }

    private void exportarBaseDados(List<Credencial> parcial) {
        new Thread(() -> {
            try {
                File dbFile = getDatabasePath("credentials_vault.db");
                if (parcial != null && !parcial.isEmpty()) {
                    dbFile = criarBdParcial(parcial);
                } else if (!dbFile.exists()) {
                    throw new FileNotFoundException("Base de dados não encontrada");
                }

                String exportFileName = getString(R.string.impexp_nome_ficheiro,
                        new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));

                Uri exportUri = copyDbToPublicDocuments(dbFile, exportFileName);
                if (exportUri == null) {
                    throw new IOException("Falha ao copiar para documentos");
                }

                runOnUiThread(() -> partilharFicheiro(exportUri, exportFileName));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.erro_exportacao_bd_generico, Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private File criarBdParcial(List<Credencial> credenciais) throws IOException {
        // Nome único evita conflito entre execuções rápidas
        String nomeTemp = "temp_bd_parcial_" + System.currentTimeMillis() + ".db";
        File tempDb = new File(getCacheDir(), nomeTemp);

        // Cria BD limpa (não reutiliza cache)
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(tempDb, null);
        db.beginTransaction();
        try {
            // Garante que a tabela não existe (evita "already exists")
            db.execSQL("DROP TABLE IF EXISTS credenciais");
            db.execSQL("CREATE TABLE credenciais (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nome TEXT NOT NULL," +
                    "username TEXT NOT NULL," +
                    "password TEXT NOT NULL," +
                    "password_length INTEGER NOT NULL," +
                    "use_uppercase INTEGER NOT NULL," +
                    "use_lowercase INTEGER NOT NULL," +
                    "use_digits INTEGER NOT NULL," +
                    "special_chars TEXT)");

            // Insere credenciais
            for (Credencial c : credenciais) {
                ContentValues values = new ContentValues();
                values.put("nome", c.getNome());
                values.put("username", c.getUsername());
                values.put("password", c.getPassword());
                values.put("password_length", c.getPasswordLength());
                values.put("use_uppercase", c.isUseUppercase() ? 1 : 0);
                values.put("use_lowercase", c.isUseLowercase() ? 1 : 0);
                values.put("use_digits", c.isUseDigits() ? 1 : 0);
                values.put("special_chars", c.getSpecialChars());
                db.insert("credenciais", null, values);
            }

            db.setTransactionSuccessful();
            return tempDb;

        } finally {
            db.endTransaction();
            db.close(); // ← essencial: libera recursos e garante escrita
        }
    }

    private void exportarComSenha(List<Credencial> parcial) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_senha, null);
        TextInputEditText editTextSenha = dialogView.findViewById(R.id.editTextSenha);
        TextInputEditText editTextConfirmar = dialogView.findViewById(R.id.editTextConfirmarSenha);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.impexp_senha_titulo)
                .setView(dialogView)
                .setPositiveButton(R.string.impexp_senha_btn_exportar, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String senha = editTextSenha.getText().toString().trim();
                String confirmacao = editTextConfirmar.getText().toString().trim();

                if (senha.isEmpty()) {
                    editTextSenha.setError(getString(R.string.impexp_senha_erro_vazia));
                    editTextSenha.requestFocus();
                    return;
                }
                if (senha.length() < 6) {
                    editTextSenha.setError(getString(R.string.impexp_senha_erro_curta));
                    editTextSenha.requestFocus();
                    return;
                }
                if (!senha.equals(confirmacao)) {
                    editTextConfirmar.setError(getString(R.string.impexp_senha_erro_confirmacao));
                    editTextConfirmar.requestFocus();
                    return;
                }

                dialog.dismiss();
                iniciarExportacaoComSenha(parcial, senha); // ← passa 'parcial'
            });
        });

        dialog.show();
    }

    private void iniciarExportacaoComSenha(List<Credencial> parcial, String senha) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.imp_exp_progress_encriptar));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                byte[] dbBytes;
                if (parcial != null && !parcial.isEmpty()) {
                    File tempDb = criarBdParcial(parcial);
                    dbBytes = Files.readAllBytes(tempDb.toPath());
                    tempDb.delete();
                } else {
                    File dbFile = getDatabasePath("credentials_vault.db");
                    if (!dbFile.exists()) {
                        throw new FileNotFoundException("Base de dados não encontrada");
                    }
                    dbBytes = Files.readAllBytes(dbFile.toPath());
                }

                byte[] encryptedData = CryptoUtils.encrypt(dbBytes, senha);
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = getString(R.string.imp_exp_nome_ficheiro_cvb, timestamp);

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/CredentialsVault/");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(encryptedData);
                }

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    partilharFicheiro(uri, fileName);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    // Mensagem amigável, sem detalhes técnicos
                    Toast.makeText(this, R.string.erro_exportacao_senha_generico, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void iniciarImportacao() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/x-sqlite3",
                "application/octet-stream",
                "application/vnd.sqlite3"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Só um createChooser — e com título internacionalizado
        Intent chooser = Intent.createChooser(intent,
                getString(R.string.impexp_importar_selecionar));

        // Regista para repetição (usado em mostrarErroExtensao)
        ultimoIntentImportacao = chooser;
        ultimoRequestCode = REQUEST_CODE_IMPORT;

        startActivityForResult(chooser, REQUEST_CODE_IMPORT);
    }

    private void importarComSenha() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream",
                ".cvb"  // extensão explícita — compatível desde Android 7.0+
        });

        Intent chooser = Intent.createChooser(intent,
                getString(R.string.impexp_importar_cvb_selecionar));

        ultimoIntentImportacao = chooser;
        ultimoRequestCode = REQUEST_CODE_IMPORT_CVB;

        startActivityForResult(chooser, REQUEST_CODE_IMPORT_CVB);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            String displayName = getDisplayName(uri);
            if (displayName == null || !displayName.toLowerCase().endsWith(".db")) {
                mostrarErroExtensao(".db");
                return;
            }
            confirmarImportacao(uri);

        } else if (requestCode == REQUEST_CODE_IMPORT_CVB && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            String displayName = getDisplayName(uri);
            if (displayName == null || !displayName.toLowerCase().endsWith(".cvb")) {
                mostrarErroExtensao(".cvb");
                return;
            }
            pedirSenhaParaImportar(uri);

        } else if (requestCode == REQUEST_CODE_IMPORT_JSON && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            String displayName = getDisplayName(uri);
            if (displayName == null || !displayName.toLowerCase().endsWith(".json")) {
                mostrarErroExtensao(".json");
                return;
            }
            confirmarImportacaoJson(uri);
        }

        // ... restante onActivityResult ...
    }

    private void confirmarImportacaoJson(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.impexp_importar_json_titulo))
                .setMessage(getString(R.string.impexp_importar_json_mensagem))
                .setPositiveButton(getString(R.string.impexp_importar_json_acao_substituir), (d, w) ->
                        iniciarImportacaoJson(uri, true))
                .setNeutralButton(getString(R.string.impexp_importar_json_acao_adicionar), (d, w) ->
                        iniciarImportacaoJson(uri, false))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void iniciarImportacaoJson(Uri uri, boolean replace) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.impexp_progress_importar_json));
        progressDialog.show();

        new Thread(() -> {
            try {
                int ignorados = 0;
                // 1. Ler ficheiro
                String json;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }

                // 2. Analisar JSON
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(json, JsonObject.class);
                JsonArray credenciaisArray = root.getAsJsonArray("credenciais");

                // 3. Validar estrutura
                if (credenciaisArray == null || credenciaisArray.size() == 0) {
                    throw new IllegalArgumentException("Ficheiro JSON inválido: não contém credenciais");
                }

                // 4. Backup automático
                Uri backupUri = fazerBackupAutomatico();
                if (backupUri == null) throw new IOException("Não foi possível criar backup");

                // 5. Substituir BD
                CredencialDAO dao = new CredencialDAO(this);
                dao.open();

                // Só limpa se 'replace' for true
                if (replace) {
                    int deleted = dao.deleteAll();
                    Log.d("JSON_IMPORT", "Eliminadas " + deleted + " credenciais existentes");
                }

                for (JsonElement element : credenciaisArray) {
                    if (!element.isJsonObject()) continue;
                    JsonObject obj = element.getAsJsonObject();

                    // Validação estrita — falha se campos obrigatórios em falta
                    if (!obj.has("nome") || obj.get("nome").isJsonNull()) {
                        throw new IllegalArgumentException("Campo 'nome' em falta ou nulo na credencial");
                    }
                    if (!obj.has("username") || obj.get("username").isJsonNull()) {
                        throw new IllegalArgumentException("Campo 'username' em falta ou nulo na credencial");
                    }
                    if (!obj.has("password") || obj.get("password").isJsonNull()) {
                        throw new IllegalArgumentException("Campo 'password' em falta ou nulo na credencial — JSON deve conter passwords em claro");
                    }

                    Credencial cred = new Credencial();
                    cred.setNome(obj.get("nome").getAsString());
                    cred.setUsername(obj.get("username").getAsString());
                    cred.setPassword(obj.get("password").getAsString());

                    // Configuração opcional (com fallbacks seguros)
                    cred.setPasswordLength(
                            obj.has("password_length") && !obj.get("password_length").isJsonNull()
                                    ? obj.get("password_length").getAsInt() : 12
                    );
                    cred.setUseUppercase(
                            obj.has("use_uppercase") && !obj.get("use_uppercase").isJsonNull()
                                    ? obj.get("use_uppercase").getAsBoolean() : true
                    );
                    cred.setUseLowercase(
                            obj.has("use_lowercase") && !obj.get("use_lowercase").isJsonNull()
                                    ? obj.get("use_lowercase").getAsBoolean() : true
                    );
                    cred.setUseDigits(
                            obj.has("use_digits") && !obj.get("use_digits").isJsonNull()
                                    ? obj.get("use_digits").getAsBoolean() : true
                    );
                    cred.setSpecialChars(
                            obj.has("special_chars") && !obj.get("special_chars").isJsonNull()
                                    ? obj.get("special_chars").getAsString() : "!@#$%^&*"
                    );

                    if (dao.existeCredencialExata(cred.getNome(), cred.getUsername(), cred.getPassword())) {
                        Log.d("JSON_IMPORT", "Ignorada credencial duplicada: " + cred.getNome());
                    } else {
                        dao.insert(cred);
                    }
                }
                dao.close();

                // 6. Sucesso
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    String backupNome = backupUri != null ? backupUri.getLastPathSegment() : "?";
                    String msgSucesso = getString(R.string.impexp_json_importado_sucesso,
                            getString(R.string.impexp_backup_automatico, backupNome));

                    if (ignorados > 0) {
                        msgSucesso += "\n\n" + getString(R.string.impexp_json_ignorados, ignorados);
                    }

                    Toast.makeText(this, msgSucesso, Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(ImpExpActivity.this)
                            .setTitle(getString(R.string.erro_importacao_json_titulo))
                            .setMessage(getString(R.string.erro_importacao_json_mensagem, e.getLocalizedMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        }).start();
    }

    private String gerarPassword(int length, boolean useUpper, boolean useLower,
                                 boolean useDigits, String specialChars) {
        StringBuilder allChars = new StringBuilder();
        if (useUpper) allChars.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        if (useLower) allChars.append("abcdefghijklmnopqrstuvwxyz");
        if (useDigits) allChars.append("0123456789");
        if (!specialChars.isEmpty()) allChars.append(specialChars);

        if (allChars.length() == 0) return "password";

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }
        return password.toString();
    }

    private void pedirSenhaParaImportar(Uri uri) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_senha_importar, null);
        TextInputEditText editTextSenha = dialogView.findViewById(R.id.editTextSenha);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.impexp_senha_importar_titulo)
                .setMessage(getString(R.string.impexp_senha_importar_mensagem_introducao))
                .setView(dialogView)
                .setPositiveButton(R.string.impexp_senha_importar_acao_importar, null) // botão internacionalizado
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String senha = editTextSenha.getText().toString().trim();
                if (senha.isEmpty()) {
                    editTextSenha.setError(getString(R.string.erro_senha_obrigatoria));
                    editTextSenha.requestFocus();
                    return;
                }

                dialog.dismiss();
                iniciarImportacaoComSenha(uri, senha);
            });
        });

        dialog.show();
    }

    private void iniciarImportacaoComSenha(Uri uri, String senha) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.impexp_progress_desencriptar));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                // 1. Ler ficheiro .cvb
                byte[] encryptedData;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    encryptedData = in.readAllBytes();
                }

                // 2. Desencriptar
                byte[] decryptedData = CryptoUtils.decrypt(encryptedData, senha);

                // 3. Fazer backup da BD atual
                Uri backupUri = fazerBackupAutomatico();
                if (backupUri == null) throw new IOException("Não foi possível criar backup");

                // 4. Substituir BD
                CredencialDAO dao = new CredencialDAO(this);
                dao.open();
                dao.deleteAll();

                File dbFile = getDatabasePath("credentials_vault.db");
                try (FileOutputStream out = new FileOutputStream(dbFile)) {
                    out.write(decryptedData); // Escreve o ficheiro .db desencriptado
                }

                dao.close(); // Importante: fecha a conexão para forçar reload

                // 5. Sucesso
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    String backupNome = backupUri != null ? backupUri.getLastPathSegment() : "?";
                    String msgSucesso = getString(R.string.impexp_cvb_importado_sucesso,
                            getString(R.string.impexp_backup_automatico, backupNome));
                    Toast.makeText(this, msgSucesso, Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();

                    // Senha incorreta → diálogo modal
                    if (e instanceof IllegalArgumentException ||
                            (e.getMessage() != null &&
                                    (e.getMessage().contains("GCM") || e.getMessage().contains("BadPadding") ||
                                            e.getMessage().contains("Invalid key")))) {

                        new AlertDialog.Builder(ImpExpActivity.this)
                                .setTitle(getString(R.string.impexp_senha_erro_titulo_incorreta))
                                .setMessage(getString(R.string.impexp_senha_erro_mensagem_incorreta))
                                .setPositiveButton(getString(R.string.impexp_senha_btn_tentar_novamente),
                                        (d, which) -> pedirSenhaParaImportar(uri))
                                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                                .setCancelable(false)
                                .show();

                    }
                    // Outros erros
                    else {
                        new AlertDialog.Builder(ImpExpActivity.this)
                                .setTitle(R.string.impexp_erro_importar_titulo)
                                .setMessage(getString(R.string.impexp_erro_importar_mensagem_generico))
                                .setPositiveButton(R.string.impexp_senha_btn_tentar_novamente, (d, which) -> pedirSenhaParaImportar(uri))
                                .show();
                    }
                });
            }
        }).start();
    }

    private void confirmarImportacao(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.impexp_importar_titulo)
                .setMessage(R.string.impexp_importar_bd_mensagem)
                .setNeutralButton(R.string.impexp_importar_bd_acao_adicionar, (d, w) ->
                        importarBaseDados(uri, false))
                .setPositiveButton(R.string.impexp_importar_bd_acao_substituir, (d, w) -> {
                    // Limpa BD *antes* de importar
                    CredencialDAO dao = new CredencialDAO(ImpExpActivity.this);
                    dao.open();
                    dao.deleteAll();
                    dao.close();
                    importarBaseDados(uri, true); // o 'true' pode ser usado para logs
                })
                .setNegativeButton(android.R.string.cancel, null) // "Cancelar", não "Não"
                .show();
    }

    private void importarBaseDados(Uri uri, boolean replace) {
        try {
            // 1. Backup automático
            Uri backupUri = fazerBackupAutomatico();
            if (backupUri == null) {
                Toast.makeText(this, R.string.impexp_erro_exportar, Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Validar BD
            if (!validarBaseDados(uri)) {
                Toast.makeText(this, R.string.impexp_importar_erro_invalido, Toast.LENGTH_LONG).show();
                return;
            }

            // 3–5. Ler credenciais da BD importada (sem alterações)
            File tempDb = new File(getCacheDir(), "import_temp.db");
            // ... (cópia do URI para tempDb) ...
            SQLiteDatabase importDb = SQLiteDatabase.openDatabase(tempDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            List<Credencial> importadas = new ArrayList<>();
            Cursor cursor = importDb.query("credenciais", null, null, null, null, null, null);
            while (cursor.moveToNext()) {
                Credencial c = new Credencial();
                // ... (preenche campos) ...
                importadas.add(c);
            }
            cursor.close();
            importDb.close();
            tempDb.delete();

            // 6. Gravar na BD atual
            CredencialDAO dao = new CredencialDAO(this);
            dao.open();

            // CORREÇÃO: só um ciclo — com lógica coerente
            int inseridos = 0, ignorados = 0;
            for (Credencial c : importadas) {
                if (dao.existeCredencialExata(c.getNome(), c.getUsername(), c.getPassword())) {
                    ignorados++;
                } else {
                    dao.insert(c);
                    inseridos++;
                }
            }

            //   Se 'replace' for true, deve LIMPAR ANTES — mas só se for pedido!
            //   (no seu código atual, 'replace' não está a ser usado — vamos corrigir)
            //   → Esta lógica deve estar em 'confirmarImportacao', não aqui.

            dao.close();

            // 7. Sucesso
            String backupNome = backupUri.getLastPathSegment();
            String msgSucesso = getString(R.string.impexp_bd_importada_sucesso,
                    getString(R.string.impexp_backup_automatico, backupNome));

            if (ignorados > 0) {
                msgSucesso += "\n\n" + getString(R.string.impexp_bd_ignorados, ignorados);
            }

            Toast.makeText(this, msgSucesso, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.impexp_importar_erro_leitura, Toast.LENGTH_SHORT).show();
        }
    }

    // Faz backup automático antes de importar
    private Uri fazerBackupAutomatico() {
        File dbFile = getDatabasePath("credentials_vault.db");
        if (!dbFile.exists()) return null;

        String backupName = "Backup_PreImport_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".db";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, backupName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/x-sqlite3");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/CredentialsVault/");

        try {
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) return null;

            try (FileInputStream in = new FileInputStream(dbFile);
                 FileOutputStream out = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
            return uri;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Valida se é uma BD CredentialsVault válida
    private boolean validarBaseDados(Uri uri) {
        try {
            File tempFile = new File(getCacheDir(), "temp_import.db");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }

            // Abrir e verificar estrutura
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    tempFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='credenciais'", null);
            boolean hasTable = cursor.getCount() > 0;
            cursor.close();
            db.close();
            tempFile.delete();
            return hasTable;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    // Adicione este helper (opcional, mas limpo)
    private String getExportFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return getString(R.string.impexp_nome_ficheiro, timestamp);
    }

    private Uri copyDbToPublicDocuments(File sourceFile, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/x-sqlite3");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/CredentialsVault/");

        Uri uri = null;
        try {
            uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) return null;

            try (FileInputStream in = new FileInputStream(sourceFile);
                 FileOutputStream out = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (uri != null) {
                getContentResolver().delete(uri, null, null);
            }
            return null;
        }
        return uri;
    }

    private void partilharFicheiro(Uri uri, String fileName) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/x-sqlite3");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.impexp_backup_assunto));
        intent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.impexp_backup_corpo, new Date().toString()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(intent, getString(R.string.impexp_partilhar_titulo));
        startActivity(chooser);
    }

    private void exportarComoJson(List<Credencial> parcial) {
        // 1. Inflar layout do diálogo
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirmar_json, null);
        CheckBox checkBoxConfirmacao = dialogView.findViewById(R.id.checkBoxConfirmacao);

        // 2. Criar e mostrar diálogo
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.impexp_json_titulo)
                .setView(dialogView)
                .setPositiveButton(R.string.impexp_json_btn_exportar, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);

            checkBoxConfirmacao.setOnCheckedChangeListener((buttonView, isChecked) ->
                    positiveButton.setEnabled(isChecked));

            positiveButton.setOnClickListener(v -> {
                if (checkBoxConfirmacao.isChecked()) {
                    dialog.dismiss();
                    iniciarExportacaoJson(parcial, true); // ← passa 'parcial'
                }
            });
        });

        dialog.show();
    }

    private void iniciarExportacaoJson(List<Credencial> parcial, boolean revealPassword) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.impexp_progress_exportar_json));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                List<Credencial> credenciais;

                // Se for parcial, usa a lista recebida
                if (parcial != null) {
                    credenciais = parcial;
                } else {
                    // Senão, carrega tudo como antes
                    CredencialDAO dao = new CredencialDAO(this);
                    dao.open();
                    credenciais = dao.getAll();
                    dao.close();
                }

                // Restante código igual
                JsonArray jsonArray = Credencial.toJsonArray(credenciais, revealPassword);
                JsonObject root = new JsonObject();
                root.addProperty("versao", "1.0");
                root.addProperty("data_exportacao",
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                root.add("credenciais", jsonArray);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(root);

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = getString(R.string.impexp_nome_ficheiro_json, timestamp);

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/CredentialsVault/");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    partilharFicheiro(uri, fileName);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, R.string.erro_exportacao_json_generico, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void importarJson() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json",
                ".json"
        });

        Intent chooser = Intent.createChooser(intent,
                getString(R.string.impexp_importar_json_selecionar));

        ultimoIntentImportacao = chooser;
        ultimoRequestCode = REQUEST_CODE_IMPORT_JSON;

        startActivityForResult(chooser, REQUEST_CODE_IMPORT_JSON);
    }

    private String getDisplayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            }
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    private void mostrarErroExtensao(String extensaoEsperada) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.erro_extensao_titulo))
                .setMessage(getString(R.string.erro_extensao_mensagem, extensaoEsperada))
                .setPositiveButton(getString(R.string.erro_extensao_btn_tentar_novamente), (d, w) -> {
                    d.dismiss();
                    if (ultimoIntentImportacao != null) {
                        startActivityForResult(ultimoIntentImportacao, ultimoRequestCode);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}