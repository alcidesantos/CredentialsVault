package pt.alcidess.credentialsvault;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ListaCredenciais extends AppCompatActivity {

    private LinearLayout containerCredenciais;
    private Button buttonAdicionar;

    private EditText editTextPesquisa;

    // Modo de seleção múltipla
    private CheckBox checkBoxSelecaoModo;
    private boolean modoSelecao = false;
    private Set<Long> credenciaisSelecionadas = new HashSet<>();
    private List<Credencial> todasCredenciais = new ArrayList<>();

    private boolean primeiraVezPesquisa = true;

    private static final int REQUEST_CODE_CREDENCIAL = 100;
    private static final int REQUEST_CODE_IMP_EXP = 200;

    private static final String PREFS_NAME = "CredentialsVaultPrefs";
    private static final String KEY_PRIMEIRA_VEZ_LISTA = "primeira_vez_lista";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_credenciais);

        checkBoxSelecaoModo = findViewById(R.id.checkBoxSelecaoModo);
        checkBoxSelecaoModo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !modoSelecao) {
                entrarModoSelecao();
            } else if (!isChecked && modoSelecao) {
                sairModoSelecao();
            }
        });
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SecurityManager.exigirAutenticacao(this,
                getString(R.string.lista_credenciais_autenticacao_titulo),
                getString(R.string.lista_credenciais_autenticacao_mensagem),
                new SecurityManager.AuthCallback() {
                    @Override
                    public void onAuthenticated() {
                        carregarDaBaseDeDados();
                    }

                    @Override
                    public void onCanceled() {
                        finish();
                    }
                });
    }

    private void entrarModoSelecao() {
        modoSelecao = true;
        credenciaisSelecionadas.clear();
        checkBoxSelecaoModo.setChecked(true);
        carregarDaBaseDeDados();
        atualizarTitulo();
        invalidateOptionsMenu();
    }

    private void sairModoSelecao() {
        modoSelecao = false;
        credenciaisSelecionadas.clear();
        checkBoxSelecaoModo.setChecked(false);
        carregarDaBaseDeDados();
        atualizarTitulo();
    }

    private void atualizarTitulo() {
        TextView textViewTitulo = findViewById(R.id.textViewTitulo);
        if (modoSelecao) {
            int count = credenciaisSelecionadas.size();
            String titulo = count == 0 ? "Selecionar" : count + " selecionada" + (count != 1 ? "s" : "");
            textViewTitulo.setText(titulo);
        } else {
            textViewTitulo.setText(R.string.credenciais_lista_descricao);
        }
    }

    private void init() {
        containerCredenciais = findViewById(R.id.containerCredenciais);
        buttonAdicionar = findViewById(R.id.buttonAdicionar);
        editTextPesquisa = findViewById(R.id.editTextPesquisa);
        editTextPesquisa.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (primeiraVezPesquisa && s.toString().isEmpty()) {
                    primeiraVezPesquisa = false;
                    return; // ignora o primeiro evento vazio ao iniciar
                }
                filtrarCredenciais(s.toString().trim());
            }
        });

        carregarDaBaseDeDados();

        buttonAdicionar.setOnClickListener(v -> {
            Intent intent = new Intent(this, DefinicaoCredencial.class);
            startActivityForResult(intent, REQUEST_CODE_CREDENCIAL);
        });
    }

    private void filtrarCredenciais(String query) {
        containerCredenciais.removeAllViews();

        List<Credencial> listaFiltrada = new ArrayList<>();
        String queryLower = query.toLowerCase();

        for (Credencial cred : todasCredenciais) {
            boolean corresponde =
                    cred.getNome().toLowerCase().contains(queryLower) ||
                            cred.getUsername().toLowerCase().contains(queryLower);
            if (query.isEmpty() || corresponde) {
                listaFiltrada.add(cred);
            }
        }

        // Reutiliza carregarDaBaseDeDados — mas com lista filtrada
        mostrarCredenciais(listaFiltrada);
    }

    private void mostrarCredenciais(List<Credencial> credenciais) {
        containerCredenciais.removeAllViews();

        for (Credencial cred : credenciais) {
            View itemView;

            if (modoSelecao) {
                // ... nada
            } else {
                // ... modo normal (igual ao atual, mas com cred da lista filtrada)
                itemView = LayoutInflater.from(this)
                        .inflate(R.layout.item_credencial_nome, containerCredenciais, false);

                TextView textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
                TextView textViewNome = itemView.findViewById(R.id.textViewNome);

                textViewTimestamp.setText(cred.getLastUpdatedFormatted());
                textViewNome.setText(cred.getNome());

                itemView.setTag(cred);
                itemView.setOnClickListener(v -> {
                    Credencial c = (Credencial) v.getTag();
                    Intent intent = new Intent(ListaCredenciais.this, DefinicaoCredencial.class);
                    intent.putExtra("credencial", c);
                    startActivityForResult(intent, REQUEST_CODE_CREDENCIAL);
                });

                itemView.setOnLongClickListener(v -> {
                    Credencial c = (Credencial) v.getTag();
                    mostrarDialogoPartilha(c);
                    return true;
                });

                containerCredenciais.addView(itemView);
            }
        }
    }

    private void carregarDaBaseDeDados() {
        containerCredenciais.removeAllViews();

        CredencialDAO dao = new CredencialDAO(this);
        dao.open();
        todasCredenciais = dao.getAll();
        List<Credencial> credenciais = todasCredenciais;
        dao.close();

        // mostrarCredenciais(todasCredenciais);

        for (Credencial cred : credenciais) {
            View itemView;

            if (modoSelecao) {
                itemView = LayoutInflater.from(this)
                        .inflate(R.layout.item_credencial_selecao, containerCredenciais, false);
                CheckBox checkBox = itemView.findViewById(R.id.checkBoxSelecao);
                TextView textView = itemView.findViewById(R.id.textViewNome);
                textView.setText(cred.getNome());
                checkBox.setChecked(credenciaisSelecionadas.contains(cred.getId()));

                itemView.setTag(cred);
                itemView.setOnClickListener(v -> {
                    Credencial c = (Credencial) v.getTag();
                    boolean novoEstado = !credenciaisSelecionadas.contains(c.getId());
                    if (novoEstado) {
                        credenciaisSelecionadas.add(c.getId());
                    } else {
                        credenciaisSelecionadas.remove(c.getId());
                    }
                    checkBox.setChecked(novoEstado);
                    atualizarTitulo();
                });
            } else {
                itemView = LayoutInflater.from(this)
                        .inflate(R.layout.item_credencial_nome, containerCredenciais, false);

                TextView textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
                TextView textViewNome = itemView.findViewById(R.id.textViewNome);

                textViewTimestamp.setText(cred.getLastUpdatedFormatted());
                textViewNome.setText(cred.getNome());

                itemView.setTag(cred);
                itemView.setOnClickListener(v -> {
                    Credencial c = (Credencial) v.getTag();
                    Intent intent = new Intent(ListaCredenciais.this, DefinicaoCredencial.class);
                    intent.putExtra("credencial", c);
                    startActivityForResult(intent, REQUEST_CODE_CREDENCIAL);
                });

                itemView.setOnLongClickListener(v -> {
                    Credencial c = (Credencial) v.getTag();
                    mostrarDialogoPartilha(c);
                    return true;
                });
            }

            containerCredenciais.addView(itemView);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_CODE_CREDENCIAL || requestCode == REQUEST_CODE_IMP_EXP)
                && resultCode == RESULT_OK) {
            carregarDaBaseDeDados();
        }
    }

    private void mostrarDialogoPartilha(Credencial cred) {
        String mensagemMascarada = getString(R.string.lista_cred_partilhar_mascarada,
                cred.getNome(), cred.getUsername());
        String mensagemCompleta = getString(R.string.lista_cred_partilhar_completa,
                cred.getNome(), cred.getUsername(), cred.getPassword());

        new AlertDialog.Builder(this)
                .setTitle(R.string.lista_cred_partilhar_titulo)
                .setMessage(R.string.lista_cred_partilhar_mensagem)
                .setPositiveButton(R.string.lista_cred_partilhar_btn_com_password, (d, w) ->
                        partilharTexto(cred, mensagemCompleta))
                .setNegativeButton(R.string.lista_cred_partilhar_btn_sem_password, (d, w) ->
                        partilharTexto(cred, mensagemMascarada))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void partilharTexto(Credencial cred, String texto) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, texto);
        intent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.lista_cred_partilhar_assunto, cred.getNome()));
        startActivity(Intent.createChooser(intent,
                getString(R.string.lista_cred_partilhar_chooser_titulo)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(
                modoSelecao ? R.menu.menu_selecao : R.menu.menu_lista,
                menu
        );
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        // Ajuda está sempre disponível, independentemente do modo
        if (itemId == R.id.action_help) {
            mostrarAjuda();
            return true;
        }

        if (modoSelecao) {
            if (item.getItemId() == R.id.action_apagar_selecionadas) {
                confirmarApagarSelecionadas();
                return true;
            } else if (item.getItemId() == R.id.action_exportar_selecionadas) {
                exportarSelecionadas();
                return true;
            }
        } else if (item.getItemId() == R.id.action_impexp) {
            startActivityForResult(new Intent(this, ImpExpActivity.class), REQUEST_CODE_IMP_EXP);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportarSelecionadas() {
        List<Credencial> selecionadas = new ArrayList<>();
        for (Credencial c : todasCredenciais) {
            if (credenciaisSelecionadas.contains(c.getId())) {
                selecionadas.add(c);
            }
        }

        if (selecionadas.isEmpty()) {
            Toast.makeText(this, R.string.lista_cred_exportar_nenhuma_selecionada, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ImpExpActivity.class);
        intent.putParcelableArrayListExtra("credenciais_selecionadas", new ArrayList<>(selecionadas));
        startActivityForResult(intent, REQUEST_CODE_IMP_EXP);
    }

    private void confirmarApagarSelecionadas() {
        SecurityManager.exigirAutenticacaoPara(this, () -> {
            int count = credenciaisSelecionadas.size();
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.lista_cred_apagar_selecionadas_titulo))
                    .setMessage(getString(R.string.lista_cred_apagar_selecionadas_mensagem, count))
                    .setPositiveButton(R.string.lista_cred_apagar_selecionadas_acao_apagar, (d, w) -> apagarSelecionadas())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void apagarSelecionadas() {
        CredencialDAO dao = new CredencialDAO(this);
        dao.open();
        for (long id : credenciaisSelecionadas) {
            dao.deleteById(id);
        }
        dao.close();

        sairModoSelecao();
        carregarDaBaseDeDados();

        Toast.makeText(this, R.string.lista_cred_apagadas_sucesso, Toast.LENGTH_SHORT).show();
    }

    // Em ListaCredenciais.java
    private void mostrarAjuda() {
        String titulo = getString(R.string.ajuda_lista_titulo);
        String conteudo = getString(R.string.ajuda_lista_conteudo);

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(conteudo)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}