package pt.alcidess.credentialsvault;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.security.SecureRandom;

public class DefinicaoCredencial extends AppCompatActivity {

    private EditText editTextNome;
    private EditText editTextUsername;
    private EditText editTextPassword;

    private TextView dicaLongClick;
    private Button buttonCancelar;
    private Button buttonGuardar;
    private SeekBar seekBarTamanho;
    private TextView textViewTamanho;
    private CheckBox checkBoxMaiusculas;
    private CheckBox checkBoxMinusculas;
    private CheckBox checkBoxDigitos;
    private CheckBox checkBoxEspeciais;
    private EditText editTextEspeciais;
    private Button buttonGerarPassword;
    private Button buttonApagar;

    private Credencial credencialAtual;
    private boolean ehEdicao = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_definicao_credencial);

        initViews();
        atualizarTextoTamanho();
        carregarCredencial();
        atualizarTitulo();
        initListeners();
    }

    private void atualizarTitulo() {
        if (ehEdicao && credencialAtual != null && !credencialAtual.getNome().isEmpty()) {
            setTitle(getString(R.string.def_cred_titulo_editar, credencialAtual.getNome()));
        } else {
            setTitle(R.string.def_cred_titulo_nova);
        }
    }

    private void atualizarTextoTamanho() {
        int progress = seekBarTamanho.getProgress();
        String texto = getString(R.string.def_cred_tamanho_label, progress);
        textViewTamanho.setText(texto);
    }

    private void initViews() {
        editTextNome = findViewById(R.id.editTextNome);
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonCancelar = findViewById(R.id.buttonCancelar);
        buttonGuardar = findViewById(R.id.buttonGuardar);
        seekBarTamanho = findViewById(R.id.seekBarTamanho);
        textViewTamanho = findViewById(R.id.textViewTamanho);
        checkBoxMaiusculas = findViewById(R.id.checkBoxMaiusculas);
        checkBoxMinusculas = findViewById(R.id.checkBoxMinusculas);
        checkBoxDigitos = findViewById(R.id.checkBoxDigitos);
        checkBoxEspeciais = findViewById(R.id.checkBoxEspeciais);
        editTextEspeciais = findViewById(R.id.editTextEspeciais);
        buttonGerarPassword = findViewById(R.id.buttonGerarPassword);
        buttonApagar = findViewById(R.id.buttonApagar);
    }

    private void carregarCredencial() {
        credencialAtual = getIntent().getParcelableExtra("credencial");

        if (credencialAtual == null) {
            credencialAtual = new Credencial();
            credencialAtual.setPasswordLength(AppConfig.getDefaultPasswordLength(this));
            credencialAtual.setUseUppercase(AppConfig.getDefaultUseUppercase(this));
            credencialAtual.setUseLowercase(AppConfig.getDefaultUseLowercase(this));
            credencialAtual.setUseDigits(AppConfig.getDefaultUseDigits(this));
            credencialAtual.setSpecialChars(AppConfig.getDefaultSpecialChars(this));
            ehEdicao = false;
        } else {
            ehEdicao = true;
        }

        editTextNome.setText(credencialAtual.getNome());
        editTextUsername.setText(credencialAtual.getUsername());
        editTextPassword.setText(credencialAtual.getPassword());
        atualizarVisibilidadeDicaLongClick(credencialAtual.getPassword());
        seekBarTamanho.setProgress(credencialAtual.getPasswordLength());
        atualizarTextoTamanho();
        checkBoxMaiusculas.setChecked(credencialAtual.isUseUppercase());
        checkBoxMinusculas.setChecked(credencialAtual.isUseLowercase());
        checkBoxDigitos.setChecked(credencialAtual.isUseDigits());
        checkBoxEspeciais.setChecked(!TextUtils.isEmpty(credencialAtual.getSpecialChars()));
        editTextEspeciais.setText(credencialAtual.getSpecialChars());
    }

    private void atualizarVisibilidadeDicaLongClick(String password) {
        TextView dicaLongClick = findViewById(R.id.textViewDicaLongClick);
        if (dicaLongClick != null) {
            if (password != null && !password.isEmpty()) {
                dicaLongClick.setVisibility(View.VISIBLE);
            } else {
                dicaLongClick.setVisibility(View.GONE);
            }
        }
    }

    private void initListeners() {
        buttonCancelar.setOnClickListener(v -> finish());

        buttonGuardar.setOnClickListener(v -> {
            String nome = editTextNome.getText().toString().trim();
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString();

            if (nome.isEmpty()) {
                editTextNome.setError(getString(R.string.def_cred_erro_nome_obrigatorio));
                editTextNome.requestFocus();
                return;
            }

            Runnable acaoGuardar = () -> {
                CredencialDAO dao = new CredencialDAO(this);
                dao.open();
                long resultado;

                if (ehEdicao) {
                    credencialAtual.setNome(nome);
                    credencialAtual.setUsername(username);
                    credencialAtual.setPassword(password);
                    credencialAtual.setPasswordLength(seekBarTamanho.getProgress());
                    credencialAtual.setUseUppercase(checkBoxMaiusculas.isChecked());
                    credencialAtual.setUseLowercase(checkBoxMinusculas.isChecked());
                    credencialAtual.setUseDigits(checkBoxDigitos.isChecked());
                    credencialAtual.setSpecialChars(
                            checkBoxEspeciais.isChecked() ? editTextEspeciais.getText().toString() : ""
                    );
                    credencialAtual.touch();
                    resultado = dao.update(credencialAtual);
                } else {
                    Credencial nova = new Credencial();
                    nova.setNome(nome);
                    nova.setUsername(username);
                    nova.setPassword(password);
                    nova.touch();
                    resultado = dao.insert(nova);
                }

                dao.close();

                if (resultado != -1) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, R.string.def_cred_erro_guardar, Toast.LENGTH_SHORT).show();
                }
            };

            // Só autentica em edição
            if (ehEdicao) {
                SecurityManager.exigirAutenticacaoPara(this, acaoGuardar);
            } else {
                acaoGuardar.run();
            }
        });

        seekBarTamanho.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                atualizarTextoTamanho();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        buttonGerarPassword.setOnClickListener(v -> gerarPasswordAleatoria());

        editTextPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (credencialAtual != null) {
                    String novaPassword = s.toString();
                    credencialAtual.setPassword(novaPassword);
                    atualizarVisibilidadeDicaLongClick(novaPassword);
                }
            }
        });

        // Long click → partilhar/copia com autenticação
        editTextPassword.setOnLongClickListener(v -> {
            if (ehEdicao) {
                SecurityManager.exigirAutenticacao(this,
                        getString(R.string.def_cred_partilhar_titulo),
                        getString(R.string.def_cred_partilhar_msg_autenticacao),
                        new SecurityManager.AuthCallback() {
                            @Override
                            public void onAuthenticated() {
                                String password = editTextPassword.getText().toString();
                                if (password.isEmpty()) {
                                    Toast.makeText(DefinicaoCredencial.this,
                                            getString(R.string.def_cred_partilhar_password_vazia),
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                new AlertDialog.Builder(DefinicaoCredencial.this)
                                        .setTitle(getString(R.string.def_cred_partilhar_titulo))
                                        .setMessage(getString(R.string.def_cred_partilhar_confirmacao))
                                        .setPositiveButton(getString(R.string.def_cred_partilhar_acao_partilhar), (d, w) -> {
                                            Intent intent = new Intent(Intent.ACTION_SEND);
                                            intent.setType("text/plain");
                                            intent.putExtra(Intent.EXTRA_SUBJECT,
                                                    getString(R.string.def_cred_partilhar_assunto, credencialAtual.getNome()));
                                            intent.putExtra(Intent.EXTRA_TEXT, password);
                                            startActivity(Intent.createChooser(intent,
                                                    getString(R.string.def_cred_partilhar_escolher_app)));
                                        })
                                        .setNeutralButton(getString(R.string.def_cred_partilhar_acao_copiar), (d, w) -> {
                                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                            ClipData clip = ClipData.newPlainText("Password", password);
                                            clipboard.setPrimaryClip(clip);
                                            Toast.makeText(DefinicaoCredencial.this,
                                                    getString(R.string.def_cred_partilhar_copiada),
                                                    Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show();
                            }
                        });
            }
            return true;
        });

        // Botão Apagar com autenticação
        if (ehEdicao && buttonApagar != null) {
            buttonApagar.setOnClickListener(v -> {
                SecurityManager.exigirAutenticacaoPara(this, () -> {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.def_cred_apagar_titulo))
                            .setMessage(getString(R.string.def_cred_apagar_confirmacao))
                            .setPositiveButton(getString(R.string.def_cred_apagar_acao_apagar), (d, w) -> {
                                CredencialDAO dao = new CredencialDAO(this);
                                dao.open();
                                dao.deleteById(credencialAtual.getId());
                                dao.close();
                                setResult(RESULT_OK);
                                finish();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            });
        }
    }

    private void gerarPasswordAleatoria() {
        int length = seekBarTamanho.getProgress();
        boolean useUpper = checkBoxMaiusculas.isChecked();
        boolean useLower = checkBoxMinusculas.isChecked();
        boolean useDigits = checkBoxDigitos.isChecked();
        boolean useSpecial = checkBoxEspeciais.isChecked();
        String specialChars = useSpecial ? editTextEspeciais.getText().toString() : "";

        if (!useUpper && !useLower && !useDigits && specialChars.isEmpty()) {
            Toast.makeText(this, R.string.def_cred_erro_tipo_caractere, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder password = new StringBuilder();
        SecureRandom random = new SecureRandom();

        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";

        StringBuilder allChars = new StringBuilder();
        if (useUpper) allChars.append(upper);
        if (useLower) allChars.append(lower);
        if (useDigits) allChars.append(digits);
        if (!specialChars.isEmpty()) allChars.append(specialChars);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(allChars.length());
            password.append(allChars.charAt(index));
        }

        editTextPassword.setText(password.toString());
    }
}